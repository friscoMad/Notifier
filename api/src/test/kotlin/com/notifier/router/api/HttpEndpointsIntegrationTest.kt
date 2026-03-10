package com.notifier.router.api

import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.repository.ChannelSubscriptionRepository
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import com.notifier.router.api.service.NovuService
import com.notifier.router.common.domain.Filter
import com.notifier.router.common.dto.ChannelSubscriptionDto
import com.notifier.router.common.dto.EventDto
import com.notifier.router.common.dto.SubscriptionDto
import com.notifier.router.common.dto.UserDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import java.util.UUID

/**
 * Integration tests that make real HTTP calls to the API endpoints, verifying the full Spring stack
 * (serialization, routing, JPA, etc.) using an embedded H2 database and a random server port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class HttpEndpointsIntegrationTest : BaseIntegrationTest() {
    @LocalServerPort private var port: Int = 0

    @Autowired private lateinit var restTemplate: RestTestClient

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var notificationTypeRepository: NotificationTypeRepository

    @Autowired private lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired private lateinit var channelSubscriptionRepository: ChannelSubscriptionRepository

    @MockitoBean private lateinit var novuService: NovuService

    private lateinit var seededTypeId: UUID

    @BeforeEach
    fun setup() {
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

            val result =
                restTemplate
                    .post()
                    .uri("/api/v1/users")
                    .body(dto)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<UserDto>()
                    .returnResult()

            val body = result.responseBody
            assertNotNull(body?.id)
            assertEquals("U_HTTP_1", body?.slackId)
            assertEquals("http@test.com", body?.email)
        }

        @Test
        fun `GET users by slackId returns user`() {
            val createdResult =
                restTemplate
                    .post()
                    .uri("/api/v1/users")
                    .body(UserDto(slackId = "U_GET_1", name = "Lookup User"))
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody(UserDto::class.java)
                    .returnResult()
            val created = createdResult.responseBody!!

            val result =
                restTemplate
                    .get()
                    .uri("/api/v1/users/${created.slackId}")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<UserDto>()
                    .returnResult()

            assertEquals(created.id, result.responseBody?.id)
        }

        @Test
        fun `GET users returns 404 for unknown slackId`() {
            restTemplate
                .get()
                .uri("/api/v1/users/UNKNOWN_SLACK_ID")
                .exchange()
                .expectStatus()
                .isNotFound
        }
    }

    // ── Subscriptions ────────────────────────────────────────────────────────

    @Nested
    inner class SubscriptionEndpoints {
        private lateinit var userId: String

        @BeforeEach
        fun seedUser() {
            val result =
                restTemplate
                    .post()
                    .uri("/api/v1/users")
                    .body(UserDto(slackId = "U_SUB_USER", name = "Sub User"))
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<UserDto>()
                    .returnResult()
            userId = result.responseBody!!.id!!
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

            val result =
                restTemplate
                    .post()
                    .uri("/api/v1/subscriptions")
                    .body(dto)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<SubscriptionDto>()
                    .returnResult()

            val body = result.responseBody
            assertNotNull(body?.id)
            assertEquals(userId, body?.userId)
            assertEquals(1, body?.filters?.size)
        }

        @Test
        fun `GET subscriptions by userId returns all user subscriptions`() {
            val dto =
                SubscriptionDto(
                    userId = userId,
                    notificationTypeId = seededTypeId.toString(),
                    channels = listOf("slack_dm"),
                )
            restTemplate
                .post()
                .uri("/api/v1/subscriptions")
                .body(dto)
                .exchange()
                .expectStatus()
                .isOk

            val result =
                restTemplate
                    .get()
                    .uri("/api/v1/subscriptions/users/$userId")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<Array<SubscriptionDto>>()
                    .returnResult()

            assertTrue(result.responseBody!!.isNotEmpty())
        }

        @Test
        fun `DELETE subscription returns 204`() {
            val created =
                restTemplate
                    .post()
                    .uri("/api/v1/subscriptions")
                    .body(
                        SubscriptionDto(
                            userId = userId,
                            notificationTypeId =
                            seededTypeId.toString(),
                            channels = listOf("slack_dm"),
                        ),
                    ).exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<SubscriptionDto>()
                    .returnResult()
                    .responseBody!!

            restTemplate
                .delete()
                .uri("/api/v1/subscriptions/${created.id}")
                .exchange()
                .expectStatus()
                .isNoContent
        }

        @Test
        fun `PATCH subscription updates channels`() {
            val created =
                restTemplate
                    .post()
                    .uri("/api/v1/subscriptions")
                    .body(
                        SubscriptionDto(
                            userId = userId,
                            notificationTypeId =
                            seededTypeId.toString(),
                            channels = listOf("slack_dm"),
                        ),
                    ).exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<SubscriptionDto>()
                    .returnResult()
                    .responseBody!!

            val updateDto =
                SubscriptionDto(
                    userId = userId,
                    notificationTypeId = seededTypeId.toString(),
                    channels = listOf("slack_dm", "email"),
                )

            val result =
                restTemplate
                    .patch()
                    .uri("/api/v1/subscriptions/${created.id}")
                    .body(updateDto)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<SubscriptionDto>()
                    .returnResult()

            assertEquals(listOf("slack_dm", "email"), result.responseBody?.channels)
        }
    }

    // ── Notification Types ───────────────────────────────────────────────────

    @Nested
    inner class NotificationTypeEndpoints {
        @Test
        fun `GET notification-types returns seeded types`() {
            val result =
                restTemplate
                    .get()
                    .uri("/api/v1/notification-types")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<String>()
                    .returnResult()

            val body = result.responseBody!!
            assertTrue(body.contains("pr_created"))
        }

        @Test
        fun `GET notification-types filters by key returns 200`() {
            restTemplate
                .get()
                .uri("/api/v1/notification-types/pr_created/filters")
                .exchange()
                .expectStatus()
                .isOk
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

            val result =
                restTemplate
                    .post()
                    .uri("/api/v1/channel-subscriptions")
                    .body(dto)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<ChannelSubscriptionDto>()
                    .returnResult()

            val body = result.responseBody
            assertNotNull(body?.id)
            assertEquals("C12345", body?.slackChannelId)
        }

        @Test
        fun `GET channel-subscriptions by channelId returns results`() {
            restTemplate
                .post()
                .uri("/api/v1/channel-subscriptions")
                .body(
                    ChannelSubscriptionDto(
                        slackChannelId = "C_LOOKUP",
                        slackChannelName = "#lookup",
                        notificationTypeId = seededTypeId.toString(),
                    ),
                ).exchange()
                .expectStatus()
                .isOk

            val result =
                restTemplate
                    .get()
                    .uri("/api/v1/channel-subscriptions/channels/C_LOOKUP")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<Array<ChannelSubscriptionDto>>()
                    .returnResult()

            assertTrue(result.responseBody!!.isNotEmpty())
        }

        @Test
        fun `DELETE channel-subscription returns 204`() {
            val created =
                restTemplate
                    .post()
                    .uri("/api/v1/channel-subscriptions")
                    .body(
                        ChannelSubscriptionDto(
                            slackChannelId = "C_DEL",
                            slackChannelName = "#del-channel",
                            notificationTypeId =
                            seededTypeId.toString(),
                        ),
                    ).exchange()
                    .expectStatus()
                    .isOk
                    .expectBody<ChannelSubscriptionDto>()
                    .returnResult()
                    .responseBody!!

            restTemplate
                .delete()
                .uri("/api/v1/channel-subscriptions/${created.id}")
                .exchange()
                .expectStatus()
                .isNoContent
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

            restTemplate
                .post()
                .uri("/api/v1/events")
                .body(dto)
                .exchange()
                .expectStatus()
                .isAccepted
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

            restTemplate
                .post()
                .uri("/api/v1/webhooks/github")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .exchange()
                .expectStatus()
                .isAccepted
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

            restTemplate
                .post()
                .uri("/api/v1/webhooks/github-actions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .exchange()
                .expectStatus()
                .isAccepted
        }

        @Test
        fun `POST buildkite webhook with no token configured returns 202`() {
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

            restTemplate
                .post()
                .uri("/api/v1/webhooks/buildkite")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .exchange()
                .expectStatus()
                .isAccepted
        }

        @Test
        fun `POST github webhook with malformed json returns 400`() {
            restTemplate
                .post()
                .uri("/api/v1/webhooks/github")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{invalid-json")
                .exchange()
                .expectStatus()
                .isBadRequest
        }
    }
}
