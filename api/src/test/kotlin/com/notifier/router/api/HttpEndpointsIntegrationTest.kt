package com.notifier.router.api

import com.notifier.router.api.domain.Filter
import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.dto.ChannelSubscriptionDto
import com.notifier.router.api.dto.EventDto
import com.notifier.router.api.dto.SubscriptionDto
import com.notifier.router.api.dto.UserDto
import com.notifier.router.api.repository.ChannelSubscriptionRepository
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import com.notifier.router.api.service.NovuService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * Integration tests that make real HTTP calls to the API endpoints, verifying the full Spring stack
 * (serialization, routing, JPA, etc.) using an embedded H2 database and a random server port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HttpEndpointsIntegrationTest {
    @LocalServerPort private var port: Int = 0

    @Autowired private lateinit var restTemplate: TestRestTemplate

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var notificationTypeRepository: NotificationTypeRepository

    @Autowired private lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired private lateinit var channelSubscriptionRepository: ChannelSubscriptionRepository

    @MockBean private lateinit var novuService: NovuService

    private lateinit var seededTypeId: UUID

    @BeforeEach
    fun setup() {
        channelSubscriptionRepository.deleteAll()
        subscriptionRepository.deleteAll()
        notificationTypeRepository.deleteAll()
        userRepository.deleteAll()

        val prCreatedType =
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = "pr_created",
                name = "PR Created",
                description = "Triggered when a new Pull Request is opened.",
            )
        notificationTypeRepository.save(prCreatedType)
        seededTypeId = prCreatedType.id
    }

    // ── Users ────────────────────────────────────────────────────────────────

    @Nested
    inner class UserEndpoints {
        @Test
        fun `POST users creates a user and returns it`() {
            val dto =
                UserDto(
                    slackId = "U_HTTP_1",
                    email = "http@test.com",
                    name = "Http User",
                )

            val response =
                restTemplate.postForEntity(
                    "/api/v1/users",
                    dto,
                    UserDto::class.java,
                )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body?.id)
            assertEquals("U_HTTP_1", response.body?.slackId)
            assertEquals("http@test.com", response.body?.email)
        }

        @Test
        fun `GET users by slackId returns user`() {
            val created =
                restTemplate
                    .postForEntity(
                        "/api/v1/users",
                        UserDto(slackId = "U_GET_1", name = "Lookup User"),
                        UserDto::class.java,
                    ).body!!

            val response =
                restTemplate.getForEntity(
                    "/api/v1/users/${created.slackId}",
                    UserDto::class.java,
                )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(created.id, response.body?.id)
        }

        @Test
        fun `GET users returns 404 for unknown slackId`() {
            val response =
                restTemplate.getForEntity(
                    "/api/v1/users/UNKNOWN_SLACK_ID",
                    String::class.java,
                )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }
    }

    // ── Subscriptions ────────────────────────────────────────────────────────

    @Nested
    inner class SubscriptionEndpoints {
        private lateinit var userId: String

        @BeforeEach
        fun seedUser() {
            val user =
                restTemplate
                    .postForEntity(
                        "/api/v1/users",
                        UserDto(slackId = "U_SUB_USER", name = "Sub User"),
                        UserDto::class.java,
                    ).body!!
            userId = user.id!!
        }

        @Test
        fun `POST subscription creates and returns subscription`() {
            val dto =
                SubscriptionDto(
                    userId = userId,
                    notificationTypeId = seededTypeId.toString(),
                    channels = listOf("slack_dm"),
                    filters =
                        listOf(
                            Filter(
                                field = "repo",
                                operator = "EQ",
                                value = "my-repo",
                            ),
                        ),
                )

            val response =
                restTemplate.postForEntity(
                    "/api/v1/subscriptions",
                    dto,
                    SubscriptionDto::class.java,
                )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body?.id)
            assertEquals(userId, response.body?.userId)
            assertEquals(1, response.body?.filters?.size)
        }

        @Test
        fun `GET subscriptions by userId returns all user subscriptions`() {
            val dto =
                SubscriptionDto(
                    userId = userId,
                    notificationTypeId = seededTypeId.toString(),
                    channels = listOf("slack_dm"),
                )
            restTemplate.postForEntity(
                "/api/v1/subscriptions",
                dto,
                SubscriptionDto::class.java,
            )

            val response =
                restTemplate.exchange(
                    "/api/v1/subscriptions/users/$userId",
                    HttpMethod.GET,
                    null,
                    Array<SubscriptionDto>::class.java,
                )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(response.body!!.isNotEmpty())
        }

        @Test
        fun `DELETE subscription returns 204`() {
            val created =
                restTemplate
                    .postForEntity(
                        "/api/v1/subscriptions",
                        SubscriptionDto(
                            userId = userId,
                            notificationTypeId = seededTypeId.toString(),
                            channels = listOf("slack_dm"),
                        ),
                        SubscriptionDto::class.java,
                    ).body!!

            val response =
                restTemplate.exchange(
                    "/api/v1/subscriptions/${created.id}",
                    HttpMethod.DELETE,
                    null,
                    Void::class.java,
                )

            assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        }

        @Test
        fun `PATCH subscription updates channels`() {
            val created =
                restTemplate
                    .postForEntity(
                        "/api/v1/subscriptions",
                        SubscriptionDto(
                            userId = userId,
                            notificationTypeId = seededTypeId.toString(),
                            channels = listOf("slack_dm"),
                        ),
                        SubscriptionDto::class.java,
                    ).body!!

            val updateDto =
                SubscriptionDto(
                    userId = userId,
                    notificationTypeId = seededTypeId.toString(),
                    channels = listOf("slack_dm", "email"),
                )

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            val response =
                restTemplate.exchange(
                    "/api/v1/subscriptions/${created.id}",
                    HttpMethod.PATCH,
                    HttpEntity(updateDto, headers),
                    SubscriptionDto::class.java,
                )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(listOf("slack_dm", "email"), response.body?.channels)
        }
    }

    // ── Notification Types ───────────────────────────────────────────────────

    @Nested
    inner class NotificationTypeEndpoints {
        @Test
        fun `GET notification-types returns seeded types`() {
            val response =
                restTemplate.exchange(
                    "/api/v1/notification-types",
                    HttpMethod.GET,
                    null,
                    String::class.java,
                )

            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertTrue(body.contains("pr_created"))
        }

        @Test
        fun `GET notification-types filters by key returns 200`() {
            val response =
                restTemplate.getForEntity(
                    "/api/v1/notification-types/pr_created/filters",
                    String::class.java,
                )

            assertEquals(HttpStatus.OK, response.statusCode)
        }
    }

    // ── Channel Subscriptions ────────────────────────────────────────────────

    @Nested
    inner class ChannelSubscriptionEndpoints {
        @Test
        fun `POST channel-subscription creates and returns it`() {
            val dto =
                ChannelSubscriptionDto(
                    slackChannelId = "C12345",
                    slackChannelName = "#test-channel",
                    notificationTypeId = seededTypeId.toString(),
                )

            val response =
                restTemplate.postForEntity(
                    "/api/v1/channel-subscriptions",
                    dto,
                    ChannelSubscriptionDto::class.java,
                )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body?.id)
            assertEquals("C12345", response.body?.slackChannelId)
        }

        @Test
        fun `GET channel-subscriptions by channelId returns results`() {
            restTemplate.postForEntity(
                "/api/v1/channel-subscriptions",
                ChannelSubscriptionDto(
                    slackChannelId = "C_LOOKUP",
                    slackChannelName = "#lookup",
                    notificationTypeId = seededTypeId.toString(),
                ),
                ChannelSubscriptionDto::class.java,
            )

            val response =
                restTemplate.exchange(
                    "/api/v1/channel-subscriptions/channels/C_LOOKUP",
                    HttpMethod.GET,
                    null,
                    Array<ChannelSubscriptionDto>::class.java,
                )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(response.body!!.isNotEmpty())
        }

        @Test
        fun `DELETE channel-subscription returns 204`() {
            val created =
                restTemplate
                    .postForEntity(
                        "/api/v1/channel-subscriptions",
                        ChannelSubscriptionDto(
                            slackChannelId = "C_DEL",
                            slackChannelName = "#del-channel",
                            notificationTypeId = seededTypeId.toString(),
                        ),
                        ChannelSubscriptionDto::class.java,
                    ).body!!

            val response =
                restTemplate.exchange(
                    "/api/v1/channel-subscriptions/${created.id}",
                    HttpMethod.DELETE,
                    null,
                    Void::class.java,
                )

            assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        }
    }

    // ── Events ───────────────────────────────────────────────────────────────

    @Nested
    inner class EventEndpoints {
        @Test
        fun `POST events returns 202 Accepted`() {
            val dto =
                EventDto(
                    typeKey = "pr_created",
                    metadata = mapOf("repo" to "my-repo", "author" to "ramiro"),
                    payload = mapOf("title" to "Test PR"),
                )

            val response =
                restTemplate.postForEntity(
                    "/api/v1/events",
                    dto,
                    Void::class.java,
                )

            assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        }
    }

    // ── Webhooks ─────────────────────────────────────────────────────────────

    @Nested
    inner class WebhookEndpoints {
        @Test
        fun `POST github webhook with valid payload returns 202`() {
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

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response =
                restTemplate.exchange(
                    "/api/v1/webhooks/github",
                    HttpMethod.POST,
                    HttpEntity(payload, headers),
                    Void::class.java,
                )

            assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        }

        @Test
        fun `POST github-actions webhook returns 202`() {
            val payload =
                """
                {
                  "action": "completed",
                  "deployment": {
                    "environment": "production",
                    "creator": { "login": "deployer" },
                    "ref": "main",
                    "url": "https://api.github.com/repos/org/repo/deployments/1",
                    "created_at": "2023-01-01T00:00:00Z"
                  },
                  "deployment_status": {
                    "state": "success",
                    "url": "https://api.github.com/repos/org/repo/deployments/1/statuses/1",
                    "created_at": "2023-01-01T00:05:00Z"
                  },
                  "repository": {
                    "full_name": "org/repo"
                  },
                  "sender": { "login": "github-actions" }
                }
                """.trimIndent()

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response =
                restTemplate.exchange(
                    "/api/v1/webhooks/github-actions",
                    HttpMethod.POST,
                    HttpEntity(payload, headers),
                    Void::class.java,
                )

            assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        }

        @Test
        fun `POST buildkite webhook returns 202`() {
            val payload =
                """
                {
                  "event": "build.finished",
                  "build": {
                    "id": "build-1",
                    "state": "passed",
                    "web_url": "https://buildkite.com/builds/1",
                    "message": "Deploy API",
                    "branch": "main",
                    "commit": "abc123",
                    "finished_at": "2023-01-01T00:10:00Z"
                  },
                  "pipeline": {
                    "name": "deploy-api",
                    "slug": "deploy-api",
                    "repository": "git@github.com:org/repo.git"
                  },
                  "sender": { "name": "CI Bot" }
                }
                """.trimIndent()

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response =
                restTemplate.exchange(
                    "/api/v1/webhooks/buildkite",
                    HttpMethod.POST,
                    HttpEntity(payload, headers),
                    Void::class.java,
                )

            assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        }

        @Test
        fun `POST github webhook with malformed json returns 400`() {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response =
                restTemplate.exchange(
                    "/api/v1/webhooks/github",
                    HttpMethod.POST,
                    HttpEntity("{invalid-json", headers),
                    String::class.java,
                )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }
    }
}
