package com.notifier.router.api.controller

import com.notifier.router.api.adapter.BuildkiteWebhookAdapter
import com.notifier.router.api.adapter.GitHubActionsWebhookAdapter
import com.notifier.router.api.adapter.GitHubWebhookAdapter
import com.notifier.router.api.domain.NotificationEvent
import com.notifier.router.api.service.EventService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
@RequestMapping("/api/v1/webhooks")
class WebhookController(
    private val eventService: EventService,
    @Value("\${github.webhook.secret:}") private val githubSecret: String,
    @Value("\${buildkite.webhook.token:}") private val buildkiteToken: String,
    @Value("\${buildkite.webhook.enforce-hmac:false}") private val buildkiteEnforceHmac: Boolean,
) {
    private val logger = LoggerFactory.getLogger(WebhookController::class.java)

    @PostMapping("/github")
    fun handleGitHubWebhook(
        @RequestHeader("X-Hub-Signature-256") signature: String?,
        @RequestBody payload: String,
    ): ResponseEntity<Void> {
        if (!verifyGitHubSignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return parseAndProcess { GitHubWebhookAdapter.parse(payload) }
    }

    @PostMapping("/github-actions")
    fun handleGitHubActionsWebhook(
        @RequestBody payload: String,
    ): ResponseEntity<Void> = parseAndProcess { GitHubActionsWebhookAdapter.parse(payload) }

    @PostMapping("/buildkite")
    fun handleBuildkiteWebhook(
        @RequestHeader("X-Buildkite-Token", required = false) token: String?,
        @RequestHeader("X-Buildkite-Signature", required = false) hmacSignature: String?,
        @RequestBody payload: String,
    ): ResponseEntity<Void> {
        if (!verifyBuildkiteRequest(token, hmacSignature, payload)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        return try {
            val event = BuildkiteWebhookAdapter.parse(payload)
            if (event.typeKey == "buildkite_ping") {
                logger.info("Received Buildkite ping — webhook connection confirmed")
                eventService.processEventAsync(event)
                return ResponseEntity.ok().build()
            }
            eventService.processEventAsync(event)
            ResponseEntity.accepted().build()
        } catch (_: Exception) {
            ResponseEntity.badRequest().build()
        }
    }

    private fun parseAndProcess(parse: () -> NotificationEvent): ResponseEntity<Void> =
        try {
            eventService.processEventAsync(parse())
            ResponseEntity.accepted().build()
        } catch (_: Exception) {
            ResponseEntity.badRequest().build()
        }

    private fun verifyGitHubSignature(
        payload: String,
        signature: String?,
    ): Boolean {
        if (githubSecret.isBlank()) return true
        if (signature.isNullOrBlank()) return false

        return try {
            val prefix = "sha256="
            if (!signature.startsWith(prefix)) return false

            val hmac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(githubSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val computed =
                hmac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") {
                    "%02x".format(it)
                }

            MessageDigest.isEqual(
                signature.substring(prefix.length).toByteArray(Charsets.UTF_8),
                computed.toByteArray(Charsets.UTF_8),
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyBuildkiteRequest(
        token: String?,
        hmacSignature: String?,
        payload: String,
    ): Boolean {
        if (buildkiteToken.isBlank()) return true

        if (buildkiteEnforceHmac) {
            if (hmacSignature.isNullOrBlank()) return false
            return verifyBuildkiteHmac(payload, hmacSignature)
        }

        return when {
            !hmacSignature.isNullOrBlank() -> verifyBuildkiteHmac(payload, hmacSignature)
            !token.isNullOrBlank() -> MessageDigest.isEqual(
                token.toByteArray(Charsets.UTF_8),
                buildkiteToken.toByteArray(Charsets.UTF_8),
            )
            else -> false
        }
    }

    /**
     * Verifies a Buildkite HMAC-SHA256 signature.
     *
     * Header format: `timestamp=<unix-ts>,signature=<hex-hmac-sha256>`
     * HMAC is computed over `<timestamp>.<payload-body>` using [buildkiteToken] as the secret.
     */
    private fun verifyBuildkiteHmac(
        payload: String,
        signature: String,
    ): Boolean =
        try {
            val params = signature.split(",").associate {
                val (k, v) = it.split("=", limit = 2)
                k to v
            }
            val timestamp = params["timestamp"] ?: return false
            val receivedSig = params["signature"] ?: return false

            val hmac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(buildkiteToken.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val computed = hmac.doFinal("$timestamp.$payload".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            MessageDigest.isEqual(
                receivedSig.toByteArray(Charsets.UTF_8),
                computed.toByteArray(Charsets.UTF_8),
            )
        } catch (_: Exception) {
            false
        }
}
