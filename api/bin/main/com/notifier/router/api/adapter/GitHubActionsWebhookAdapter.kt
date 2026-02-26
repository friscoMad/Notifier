package com.notifier.router.api.adapter

import com.fasterxml.jackson.module.kotlin.readValue
import com.notifier.router.api.domain.Event

object GitHubActionsWebhookAdapter {
    fun parse(payload: String): Event {
        val webhook = webhookMapper.readValue<GitHubActionsPayload>(payload)
        return when (webhook.action) {
            "created", "in_progress" -> parseDeploymentStarted(webhook)
            "completed" -> parseDeploymentCompleted(webhook)
            else -> parseGenericEvent(webhook, payload)
        }
    }

    private fun parseDeploymentStarted(w: GitHubActionsPayload): Event {
        val d = w.deployment!!
        return Event(
            typeKey = "deploy_started",
            metadata = d.commonMetadata(),
            payload =
                mapOf(
                    "deployment_url" to d.url,
                    "status" to (w.action ?: "created"),
                    "created_at" to d.createdAt,
                ),
        )
    }

    private fun parseDeploymentCompleted(w: GitHubActionsPayload): Event {
        val d = w.deployment!!
        val s = w.deploymentStatus!!
        return Event(
            typeKey = "deploy_completed",
            metadata = d.commonMetadata() + ("status" to s.state),
            payload =
                mapOf(
                    "deployment_url" to d.url,
                    "status_url" to s.url,
                    "status" to s.state,
                    "completed_at" to s.createdAt,
                ),
        )
    }

    private fun parseGenericEvent(
        w: GitHubActionsPayload,
        rawPayload: String,
    ) = Event(
        typeKey = w.action ?: "github_actions_event",
        metadata =
            mapOf(
                "repo" to w.repository.fullName,
                "event_type" to (w.action ?: "unknown"),
            ),
        payload = mapOf("event_data" to rawPayload),
    )

    private fun GitHubDeployment.commonMetadata() =
        mapOf(
            "service" to environment,
            "environment" to environment,
            "author" to creator.login,
            "pipeline" to ref,
        )
}
