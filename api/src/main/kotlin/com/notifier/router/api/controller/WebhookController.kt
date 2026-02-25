package com.notifier.router.api.controller

import com.notifier.router.api.adapter.BuildkiteWebhookAdapter
import com.notifier.router.api.adapter.GitHubActionsWebhookAdapter
import com.notifier.router.api.adapter.GitHubWebhookAdapter
import com.notifier.router.api.domain.Event
import com.notifier.router.api.service.EventService
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
) {
    @PostMapping("/github")
    fun handleGitHubWebhook(
        @RequestHeader("X-Hub-Signature-256") signature: String?,
        @RequestBody payload: String,
    ): ResponseEntity<Void> {
        if (!verifyGitHubSignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        try {
            val event = GitHubWebhookAdapter.parse(payload)
            return processEvent(event)
        } catch (e: Exception) {
            return ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/github-actions")
    fun handleGitHubActionsWebhook(
        @RequestBody payload: String,
    ): ResponseEntity<Void> {
        try {
            val event = GitHubActionsWebhookAdapter.parse(payload)
            return processEvent(event)
        } catch (e: Exception) {
            return ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/buildkite")
    fun handleBuildkiteWebhook(
        @RequestBody payload: String,
    ): ResponseEntity<Void> {
        try {
            val event = BuildkiteWebhookAdapter.parse(payload)
            return processEvent(event)
        } catch (e: Exception) {
            return ResponseEntity.badRequest().build()
        }
    }

    private fun verifyGitHubSignature(
        payload: String,
        signature: String?,
    ): Boolean {
        if (githubSecret.isBlank()) {
            return true // Skip validation if exactly not configured (e.g. tests)
        }
        if (signature.isNullOrBlank()) return false

        try {
            val algorithm = "HmacSHA256"
            val prefix = "sha256="
            if (!signature.startsWith(prefix)) return false

            val expectedSignature = signature.substring(prefix.length)

            val hmac = Mac.getInstance(algorithm)
            val secretKeySpec = SecretKeySpec(githubSecret.toByteArray(Charsets.UTF_8), algorithm)
            hmac.init(secretKeySpec)

            val hashBytes = hmac.doFinal(payload.toByteArray(Charsets.UTF_8))
            val computedSignature = hashBytes.joinToString("") { "%02x".format(it) }

            return MessageDigest.isEqual(
                expectedSignature.toByteArray(Charsets.UTF_8),
                computedSignature.toByteArray(Charsets.UTF_8),
            )
        } catch (e: Exception) {
            return false
        }
    }

    private fun processEvent(event: Event): ResponseEntity<Void> {
        eventService.processEventAsync(event)

        return ResponseEntity.accepted().build()
    }
}
