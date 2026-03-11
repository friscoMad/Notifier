package com.notifier.router.api.controller

import com.notifier.router.api.BaseIntegrationTest
import com.notifier.router.api.service.NovuService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.client.RestTestClient
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Integration tests for Buildkite webhook authentication.
 * Covers plain token, HMAC signature, and enforce-hmac mode.
 */
@AutoConfigureRestTestClient
class BuildkiteWebhookTokenIntegrationTest : BaseIntegrationTest() {
    @Autowired private lateinit var restTemplate: RestTestClient

    @MockitoBean private lateinit var novuService: NovuService

    private val secret = "integration-test-token"

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

    private fun hmacSignature(
        payload: String,
        secret: String,
        timestamp: String = "1700000000",
    ): String {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sig = hmac.doFinal("$timestamp.$payload".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "timestamp=$timestamp,signature=$sig"
    }

    @Nested
    @TestPropertySource(properties = ["buildkite.webhook.token=integration-test-token"])
    inner class PlainTokenMode {
        @Test
        fun `valid token returns 202`() {
            restTemplate.post().uri("/api/v1/webhooks/buildkite")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Buildkite-Token", secret)
                .body(validPayload).exchange().expectStatus().isAccepted
        }

        @Test
        fun `wrong token returns 401`() {
            restTemplate.post().uri("/api/v1/webhooks/buildkite")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Buildkite-Token", "wrong-token")
                .body(validPayload).exchange().expectStatus().isUnauthorized
        }

        @Test
        fun `missing token returns 401`() {
            restTemplate.post().uri("/api/v1/webhooks/buildkite")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validPayload).exchange().expectStatus().isUnauthorized
        }

        @Test
        fun `ping with valid token returns 200`() {
            val pingPayload = """{"event":"ping","service":{"id":"a","provider":"webhook"},"organization":{"slug":"org"},"sender":{"name":"u"}}"""
            restTemplate.post().uri("/api/v1/webhooks/buildkite")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Buildkite-Token", secret)
                .body(pingPayload).exchange().expectStatus().isOk
        }
    }

    @Nested
    @TestPropertySource(properties = ["buildkite.webhook.token=integration-test-token"])
    inner class HmacMode {
        @Test
        fun `valid HMAC signature returns 202`() {
            val sig = hmacSignature(validPayload, secret)
            restTemplate.post().uri("/api/v1/webhooks/buildkite")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Buildkite-Signature", sig)
                .body(validPayload).exchange().expectStatus().isAccepted
        }

        @Test
        fun `invalid HMAC signature returns 401`() {
            restTemplate.post().uri("/api/v1/webhooks/buildkite")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Buildkite-Signature", "timestamp=1700000000,signature=deadbeef")
                .body(validPayload).exchange().expectStatus().isUnauthorized
        }
    }

    @Nested
    @TestPropertySource(
        properties = [
            "buildkite.webhook.token=integration-test-token",
            "buildkite.webhook.enforce-hmac=true",
        ],
    )
    inner class EnforceHmacMode {
        @Test
        fun `valid HMAC signature accepted`() {
            val sig = hmacSignature(validPayload, secret)
            restTemplate.post().uri("/api/v1/webhooks/buildkite")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Buildkite-Signature", sig)
                .body(validPayload).exchange().expectStatus().isAccepted
        }

        @Test
        fun `plain token rejected even if valid`() {
            restTemplate.post().uri("/api/v1/webhooks/buildkite")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Buildkite-Token", secret)
                .body(validPayload).exchange().expectStatus().isUnauthorized
        }

        @Test
        fun `no header returns 401`() {
            restTemplate.post().uri("/api/v1/webhooks/buildkite")
                .contentType(MediaType.APPLICATION_JSON)
                .body(validPayload).exchange().expectStatus().isUnauthorized
        }
    }
}
