package com.notifier.router.api.controller

import com.notifier.router.api.service.EventService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.http.HttpStatus
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@ExtendWith(MockitoExtension::class)
class WebhookControllerTest {
    @Mock private lateinit var eventService: EventService

    private lateinit var webhookController: WebhookController

    private val testGithubSecret = "my-test-secret"
    private val testBuildkiteToken = "my-buildkite-token"

    private val sampleGithubPayload =
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

    private val sampleBuildkitePayload =
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

    @BeforeEach
    fun setup() {
        webhookController = WebhookController(eventService, testGithubSecret, testBuildkiteToken)
    }

    private fun generateHmac(
        payload: String,
        secret: String,
    ): String {
        val hmac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        hmac.init(secretKeySpec)
        val hashBytes = hmac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return "sha256=" + hashBytes.joinToString("") { "%02x".format(it) }
    }

    // --- GitHub tests ---

    @Test
    fun `handleGitHubWebhook accepts valid signature and yields Accepted`() {
        val signature = generateHmac(sampleGithubPayload, testGithubSecret)

        val response = webhookController.handleGitHubWebhook(signature, sampleGithubPayload)

        assert(response.statusCode == HttpStatus.ACCEPTED)
        verify(eventService).processEventAsync(any())
    }

    @Test
    fun `handleGitHubWebhook rejects invalid signature and yields Unauthorized`() {
        val invalidSignature = "sha256=invalidhashvalue"

        val response = webhookController.handleGitHubWebhook(invalidSignature, sampleGithubPayload)

        assert(response.statusCode == HttpStatus.UNAUTHORIZED)
        verify(eventService, never()).processEventAsync(any())
    }

    @Test
    fun `handleGitHubWebhook accepts payload if secret is totally blank`() {
        webhookController = WebhookController(eventService, "", testBuildkiteToken)

        val response = webhookController.handleGitHubWebhook(null, sampleGithubPayload)

        assert(response.statusCode == HttpStatus.ACCEPTED)
        verify(eventService).processEventAsync(any())
    }

    // --- Buildkite tests ---

    @Test
    fun `handleBuildkiteWebhook accepts valid token and yields Accepted`() {
        val response = webhookController.handleBuildkiteWebhook(testBuildkiteToken, sampleBuildkitePayload)

        assert(response.statusCode == HttpStatus.ACCEPTED)
        verify(eventService).processEventAsync(any())
    }

    @Test
    fun `handleBuildkiteWebhook rejects wrong token and yields Unauthorized`() {
        val response = webhookController.handleBuildkiteWebhook("wrong-token", sampleBuildkitePayload)

        assert(response.statusCode == HttpStatus.UNAUTHORIZED)
        verify(eventService, never()).processEventAsync(any())
    }

    @Test
    fun `handleBuildkiteWebhook rejects missing token and yields Unauthorized`() {
        val response = webhookController.handleBuildkiteWebhook(null, sampleBuildkitePayload)

        assert(response.statusCode == HttpStatus.UNAUTHORIZED)
        verify(eventService, never()).processEventAsync(any())
    }

    @Test
    fun `handleBuildkiteWebhook accepts payload if token is not configured`() {
        webhookController = WebhookController(eventService, testGithubSecret, "")

        val response = webhookController.handleBuildkiteWebhook(null, sampleBuildkitePayload)

        assert(response.statusCode == HttpStatus.ACCEPTED)
        verify(eventService).processEventAsync(any())
    }
}
