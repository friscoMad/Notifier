package com.notifier.router.api.service

import com.notifier.router.api.BaseIntegrationTest
import com.notifier.router.api.domain.Filter
import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.domain.User
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Integration test that validates the full notification flow from webhook reception to NovuService
 * dispatch, including deep payload verification.
 *
 * Uses mockito-kotlin's `check {}` for null-safe argument verification, since Java's
 * ArgumentCaptor.capture() returns null which is incompatible with Kotlin's non-nullable parameter
 * types.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NovuServiceIntegrationTest : BaseIntegrationTest() {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var notificationTypeRepository: NotificationTypeRepository

    @Autowired private lateinit var subscriptionRepository: SubscriptionRepository

    @MockitoBean private lateinit var novuService: NovuService

    private lateinit var testUser: User
    private lateinit var prCreatedType: NotificationType

    @BeforeEach
    fun setup() {
        subscriptionRepository.deleteAll()
        notificationTypeRepository.deleteAll()
        userRepository.deleteAll()

        prCreatedType =
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = "pr_created",
                name = "PR Created",
                description = "Triggered when a new Pull Request is opened.",
            )
        notificationTypeRepository.save(prCreatedType)

        testUser =
            User(
                id = UUID.randomUUID(),
                slackId = "U_DEEP_TEST",
                email = "deep@example.com",
                name = "Deep Test User",
            )
        userRepository.save(testUser)
    }

    @Test
    fun `novu receives correct workflow id and subscriber list`() {
        subscriptionRepository.save(
            Subscription(
                id = UUID.randomUUID(),
                userId = testUser.id,
                notificationTypeId = prCreatedType.id,
                channels = listOf("slack_dm"),
                filters =
                    listOf(
                        Filter(
                            field = "repo",
                            operator = "EQ",
                            value = "org/my-repo",
                        ),
                    ),
                enabled = true,
            ),
        )

        val payload =
            """
            {
              "action": "opened",
              "pull_request": {
                "user": { "login": "author123" },
                "base": { "ref": "main" },
                "title": "Add new feature",
                "body": "Description here",
                "html_url": "https://github.com/org/my-repo/pull/42",
                "created_at": "2025-06-01T10:00:00Z"
              },
              "repository": {
                "full_name": "org/my-repo"
              }
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/webhooks/github")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            ).andExpect(status().isAccepted)

        Thread.sleep(500)

        // Use mockito-kotlin's check {} for null-safe verification
        verify(novuService)
            .triggerWorkflow(
                check { workflowId -> assertEquals("pr_created", workflowId) },
                check { subscribers ->
                    assertEquals(1, subscribers.size)
                    assertEquals("U_DEEP_TEST", subscribers[0])
                },
                check { capturedPayload ->
                    assertEquals("Add new feature", capturedPayload["title"])
                    assertEquals(
                        "Description here",
                        capturedPayload["description"],
                    )
                    assertEquals(
                        "https://github.com/org/my-repo/pull/42",
                        capturedPayload["url"],
                    )
                    assertEquals(
                        "2025-06-01T10:00:00Z",
                        capturedPayload["created_at"],
                    )
                },
            )
    }

    @Test
    fun `multiple matching subscriptions result in single novu call with all subscribers`() {
        val secondUser =
            User(
                id = UUID.randomUUID(),
                slackId = "U_SECOND",
                email = "second@example.com",
                name = "Second User",
            )
        userRepository.save(secondUser)

        subscriptionRepository.save(
            Subscription(
                id = UUID.randomUUID(),
                userId = testUser.id,
                notificationTypeId = prCreatedType.id,
                channels = listOf("slack_dm"),
                filters =
                    listOf(
                        Filter(
                            field = "repo",
                            operator = "EQ",
                            value = "org/shared-repo",
                        ),
                    ),
                enabled = true,
            ),
        )
        subscriptionRepository.save(
            Subscription(
                id = UUID.randomUUID(),
                userId = secondUser.id,
                notificationTypeId = prCreatedType.id,
                channels = listOf("slack_dm"),
                filters =
                    listOf(
                        Filter(
                            field = "repo",
                            operator = "EQ",
                            value = "org/shared-repo",
                        ),
                    ),
                enabled = true,
            ),
        )

        val payload =
            """
            {
              "action": "opened",
              "pull_request": {
                "user": { "login": "contributor" },
                "base": { "ref": "develop" },
                "title": "Shared repo PR",
                "body": "",
                "html_url": "https://github.com/org/shared-repo/pull/99",
                "created_at": "2025-06-01T12:00:00Z"
              },
              "repository": {
                "full_name": "org/shared-repo"
              }
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/webhooks/github")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            ).andExpect(status().isAccepted)

        Thread.sleep(500)

        verify(novuService)
            .triggerWorkflow(
                check { assertEquals("pr_created", it) },
                check { subscribers ->
                    assertEquals(2, subscribers.size)
                    assertTrue(subscribers.contains("U_DEEP_TEST"))
                    assertTrue(subscribers.contains("U_SECOND"))
                },
                check { /* payload verified in other test */ },
            )
    }

    @Test
    fun `disabled subscription does not trigger novu`() {
        subscriptionRepository.save(
            Subscription(
                id = UUID.randomUUID(),
                userId = testUser.id,
                notificationTypeId = prCreatedType.id,
                channels = listOf("slack_dm"),
                filters =
                    listOf(
                        Filter(
                            field = "repo",
                            operator = "EQ",
                            value = "org/repo",
                        ),
                    ),
                enabled = false,
            ),
        )

        val payload =
            """
            {
              "action": "opened",
              "pull_request": {
                "user": { "login": "testuser" },
                "base": { "ref": "main" },
                "title": "Fix something",
                "body": "",
                "html_url": "https://github.com/org/repo/pull/1",
                "created_at": "2025-06-01T08:00:00Z"
              },
              "repository": {
                "full_name": "org/repo"
              }
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/webhooks/github")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            ).andExpect(status().isAccepted)

        Thread.sleep(500)

        verify(novuService, never()).triggerWorkflow(any(), any(), any())
    }

    @Test
    fun `unknown notification type does not crash and does not trigger novu`() {
        val payload =
            """
            {
              "action": "unknown_action_xyz",
              "pull_request": {
                "user": { "login": "testuser" },
                "base": { "ref": "main" },
                "title": "Unknown PR",
                "body": "",
                "html_url": "https://github.com/org/repo/pull/1",
                "created_at": "2025-06-01T08:00:00Z"
              },
              "repository": {
                "full_name": "org/repo"
              }
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/webhooks/github")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            ).andExpect(status().isAccepted)

        Thread.sleep(500)

        verify(novuService, never()).triggerWorkflow(any(), any(), any())
    }
}
