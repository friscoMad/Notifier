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

    private val testSecret = "my-test-secret"
    private val samplePayload =
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

    @BeforeEach
    fun setup() {
        webhookController = WebhookController(eventService, testSecret)
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

    @Test
    fun `handleGitHubWebhook accepts valid signature and yields Accepted`() {
        val signature = generateHmac(samplePayload, testSecret)

        val response = webhookController.handleGitHubWebhook(signature, samplePayload)

        assert(response.statusCode == HttpStatus.ACCEPTED)
        verify(eventService).processEventAsync(any())
    }

    @Test
    fun `handleGitHubWebhook rejects invalid signature and yields Unauthorized`() {
        val invalidSignature = "sha256=invalidhashvalue"

        val response = webhookController.handleGitHubWebhook(invalidSignature, samplePayload)

        assert(response.statusCode == HttpStatus.UNAUTHORIZED)
        verify(eventService, never()).processEventAsync(any())
    }

    @Test
    fun `handleGitHubWebhook accepts payload if secret is totally blank`() {
        webhookController = WebhookController(eventService, "")

        val response = webhookController.handleGitHubWebhook(null, samplePayload)

        assert(response.statusCode == HttpStatus.ACCEPTED)
        verify(eventService).processEventAsync(any())
    }
}
