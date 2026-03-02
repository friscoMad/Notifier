package com.notifier.router.api.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.notifier.router.api.BaseIntegrationTest
import com.notifier.router.api.domain.Filter
import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.domain.User
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post as mockPost

/**
 * Full end-to-end integration test that validates the ACTUAL HTTP request the Novu SDK sends over
 * the wire. Uses WireMock to intercept the SDK's outgoing HTTP traffic by overriding internal
 * fields via Java reflection.
 *
 * This test proves the entire pipeline works: Webhook → WebhookController → EventService →
 * NovuService → Novu SDK → HTTP POST
 *
 * WireMock stubs the Novu API's trigger endpoint and we assert on the actual JSON body and headers
 * sent over the wire.
 */
@SpringBootTest(properties = ["novu.api.key=test-wiremock-key"])
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NovuWireMockIntegrationTest : BaseIntegrationTest() {
    companion object {
        private val wireMock = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @DynamicPropertySource
        fun configureNovuUrl(registry: DynamicPropertyRegistry) {
            // Start WireMock before Spring context initialization
            if (!wireMock.isRunning) wireMock.start()
            registry.add("novu.api.url") { wireMock.baseUrl() + "/v1/" }
        }
    }

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var notificationTypeRepository: NotificationTypeRepository

    @Autowired private lateinit var subscriptionRepository: SubscriptionRepository

    private lateinit var wireMockClient: WireMock

    @BeforeAll
    fun startWireMock() {
        if (!wireMock.isRunning) wireMock.start()
        // Create a WireMock client bound to the correct dynamic port
        wireMockClient = WireMock(wireMock.port())
    }

    @AfterAll
    fun stopWireMock() {
        wireMock.stop()
    }

    @BeforeEach
    fun setup() {
        wireMock.resetAll()

        subscriptionRepository.deleteAll()
        notificationTypeRepository.deleteAll()
        userRepository.deleteAll()

        // Stub the Novu trigger endpoint to return 201
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/events/trigger"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "data": {
                                "acknowledged": true,
                                "status": "processed",
                                "transactionId": "test-tx-id"
                              }
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    @Test
    fun `webhook triggers actual HTTP POST to novu trigger endpoint via wiremock`() {
        // Setup data
        val prCreatedType =
            notificationTypeRepository.save(
                NotificationType(
                    id = UUID.randomUUID(),
                    typeKey = "pr_created",
                    name = "PR Created",
                    description =
                        "Triggered when a new Pull Request is opened.",
                ),
            )

        val testUser =
            userRepository.save(
                User(
                    id = UUID.randomUUID(),
                    slackId = "U_WIREMOCK_TEST",
                    email = "wiremock@example.com",
                    name = "WireMock Test User",
                ),
            )

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
                            value = "org/wiremock-repo",
                        ),
                    ),
                enabled = true,
            ),
        )

        val webhookPayload =
            """
            {
              "action": "opened",
              "pull_request": {
                "user": { "login": "wire-author" },
                "base": { "ref": "main" },
                "title": "WireMock test PR",
                "body": "Testing real HTTP",
                "html_url": "https://github.com/org/wiremock-repo/pull/77",
                "created_at": "2025-07-01T10:00:00Z"
              },
              "repository": {
                "full_name": "org/wiremock-repo"
              }
            }
            """.trimIndent()

        // Send the webhook
        mockMvc
            .perform(
                mockPost("/api/v1/webhooks/github")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(webhookPayload),
            ).andExpect(status().isAccepted)

        // Wait for async processing
        Thread.sleep(1500)

        // Verify WireMock received the actual HTTP POST from the Novu SDK
        // using the instance client (not static WireMock.verify which defaults to port
        // 8080)
        wireMockClient.verifyThat(
            postRequestedFor(urlPathEqualTo("/v1/events/trigger"))
                .withHeader("Authorization", equalTo("ApiKey test-wiremock-key"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("pr_created")))
                .withRequestBody(
                    matchingJsonPath(
                        "$.payload.title",
                        equalTo("WireMock test PR"),
                    ),
                ),
        )
    }

    @Test
    fun `non-matching webhook does not produce HTTP call to novu`() {
        val prCreatedType =
            notificationTypeRepository.save(
                NotificationType(
                    id = UUID.randomUUID(),
                    typeKey = "pr_created",
                    name = "PR Created",
                ),
            )

        val testUser =
            userRepository.save(
                User(
                    id = UUID.randomUUID(),
                    slackId = "U_NO_MATCH",
                    email = "nomatch@example.com",
                    name = "No Match User",
                ),
            )

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
                            value = "org/specific-repo",
                        ),
                    ),
                enabled = true,
            ),
        )

        val webhookPayload =
            """
            {
              "action": "opened",
              "pull_request": {
                "user": { "login": "someone" },
                "base": { "ref": "main" },
                "title": "Different repo PR",
                "body": "",
                "html_url": "https://github.com/org/other-repo/pull/99",
                "created_at": "2025-07-01T12:00:00Z"
              },
              "repository": {
                "full_name": "org/other-repo"
              }
            }
            """.trimIndent()

        mockMvc
            .perform(
                mockPost("/api/v1/webhooks/github")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(webhookPayload),
            ).andExpect(status().isAccepted)

        Thread.sleep(1500)

        // Verify NO HTTP calls were made to the Novu trigger endpoint
        wireMockClient.verifyThat(0, postRequestedFor(urlPathEqualTo("/v1/events/trigger")))
    }
}
