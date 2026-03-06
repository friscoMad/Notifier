package com.notifier.router.api

import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.domain.User
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import com.notifier.router.api.service.NovuService
import com.notifier.router.common.domain.Filter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class NotificationFlowIntegrationTest : BaseIntegrationTest() {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var notificationTypeRepository: NotificationTypeRepository

    @Autowired private lateinit var subscriptionRepository: SubscriptionRepository

    @MockitoBean private lateinit var novuService: NovuService

    @BeforeEach
    fun setup() {
        // 1. Setup Notification Type
        val prCreatedType =
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = "pr_created",
                name = "PR Created",
                description = "Triggered when a new Pull Request is opened.",
            )
        notificationTypeRepository.save(prCreatedType)

        // 2. Setup User
        val testUser =
            User(
                id = UUID.randomUUID(),
                slackId = "U77777",
                email = "dev@example.com",
                name = "Integration Test User",
            )
        userRepository.save(testUser)

        // 3. Setup Subscription with a filter for "org/repo"
        val subscription =
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
                enabled = true,
            )
        subscriptionRepository.save(subscription)
    }

    @Test
    fun `github webhook triggers novu for matching subscription`() {
        val payload =
            """
            {
              "action": "opened",
              "pull_request": {
                "user": { "login": "testuser" },
                "base": { "ref": "main" },
                "title": "Fix bug",
                "body": "Fixes 123",
                "html_url": "https://github.com/pulls/1",
                "created_at": "2023-01-01T00:00:00Z"
              },
              "repository": {
                "full_name": "org/repo"
              }
            }
            """.trimIndent()

        // Send webhook (secret is blank in properties, so no signature needed)
        mockMvc
            .perform(
                post("/api/v1/webhooks/github")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            ).andExpect(status().isAccepted)

        // Give Async processing a moment
        Thread.sleep(500)

        // Verify NovuService was called for "U77777"
        verify(novuService).triggerWorkflow(eq("pr_created"), eq(listOf("U77777")), any())
    }

    @Test
    fun `github webhook ignores non-matching subscription`() {
        val payload =
            """
            {
              "action": "opened",
              "pull_request": {
                "user": { "login": "testuser" },
                "base": { "ref": "main" },
                "title": "Fix bug",
                "body": "Fixes 123",
                "html_url": "https://github.com/pulls/1",
                "created_at": "2023-01-01T00:00:00Z"
              },
              "repository": {
                "full_name": "org/ignored-repo"
              }
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/webhooks/github")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            ).andExpect(status().isAccepted)

        // Give Async processing a moment
        Thread.sleep(500)

        // Verify NovuService was NOT called (filter did not match)
        verify(novuService, org.mockito.Mockito.never())
            .triggerWorkflow(any(), any(), any())
    }
}
