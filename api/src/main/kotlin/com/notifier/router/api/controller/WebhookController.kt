package com.notifier.router.api.controller

import com.notifier.router.api.adapter.*
import com.notifier.router.api.domain.Event
import com.notifier.router.api.service.FilterEvaluator
import com.notifier.router.api.service.SubscriptionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/webhooks")
class WebhookController(
    private val subscriptionService: SubscriptionService,
    private val filterEvaluator: FilterEvaluator,
) {
    @PostMapping("/github")
    fun handleGitHubWebhook(
        @RequestBody payload: String,
    ): ResponseEntity<Void> {
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

    private fun processEvent(event: Event): ResponseEntity<Void> {
        // In a real implementation, you would:
        // 1. Get all subscriptions for this event type
        // 2. Evaluate filters using FilterEvaluator
        // 3. Route to appropriate channels via Novu

        return ResponseEntity.accepted().build()
    }
}
