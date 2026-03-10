package com.notifier.router.api.controller

import com.notifier.router.api.BaseIntegrationTest
import com.notifier.router.api.service.NovuService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * Integration tests for Buildkite webhook token verification.
 * Exercises the full HTTP stack with a configured token (unlike HttpEndpointsIntegrationTest,
 * which leaves the token blank to test the pass-through / not-configured behaviour).
 */
@TestPropertySource(properties = ["buildkite.webhook.token=integration-test-token"])
@AutoConfigureRestTestClient
class BuildkiteWebhookTokenIntegrationTest : BaseIntegrationTest() {
    @Autowired private lateinit var restTemplate: RestTestClient

    @MockitoBean private lateinit var novuService: NovuService

    private val validPayload =
        """
        {
          "event": "build.finished",
          "pipeline": { "slug": "my-pipeline", "web_url": "https://buildkite.com/org/my-pipeline" },
          "build": {
            "state": "passed",
            "message": "production",
            "web_url": "https://buildkite.com/org/my-pipeline/builds/1",
            "finished_at": "2023-01-01T00:00:00Z"
          }
        }
        """.trimIndent()

    @Test
    fun `POST buildkite webhook with valid token returns 202`() {
        restTemplate
            .post()
            .uri("/api/v1/webhooks/buildkite")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Buildkite-Token", "integration-test-token")
            .body(validPayload)
            .exchange()
            .expectStatus()
            .isAccepted
    }

    @Test
    fun `POST buildkite webhook with wrong token returns 401`() {
        restTemplate
            .post()
            .uri("/api/v1/webhooks/buildkite")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Buildkite-Token", "wrong-token")
            .body(validPayload)
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `POST buildkite webhook without token returns 401`() {
        restTemplate
            .post()
            .uri("/api/v1/webhooks/buildkite")
            .contentType(MediaType.APPLICATION_JSON)
            .body(validPayload)
            .exchange()
            .expectStatus()
            .isUnauthorized
    }
}
