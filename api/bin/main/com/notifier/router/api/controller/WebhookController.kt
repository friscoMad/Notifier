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
        return parseAndProcess { GitHubWebhookAdapter.parse(payload) }
    }

    @PostMapping("/github-actions")
    fun handleGitHubActionsWebhook(
        @RequestBody payload: String,
    ): ResponseEntity<Void> = parseAndProcess { GitHubActionsWebhookAdapter.parse(payload) }

    @PostMapping("/buildkite")
    fun handleBuildkiteWebhook(
        @RequestBody payload: String,
    ): ResponseEntity<Void> = parseAndProcess { BuildkiteWebhookAdapter.parse(payload) }

    private fun parseAndProcess(parse: () -> Event): ResponseEntity<Void> =
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
}
