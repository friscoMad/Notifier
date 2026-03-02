package com.notifier.router.api.adapter

import com.notifier.router.api.domain.DeployCompletedEvent
import com.notifier.router.api.domain.DeployStartedEvent
import com.notifier.router.api.domain.GenericEvent
import com.notifier.router.api.domain.NotificationEvent
import tools.jackson.module.kotlin.readValue

object GitHubActionsWebhookAdapter {
    fun parse(payload: String): NotificationEvent {
        val w = webhookMapper.readValue<GitHubActionsPayload>(payload)
        return when (w.action) {
            "created", "in_progress" -> {
                parseDeploymentStarted(w)
            }

            "completed" -> {
                parseDeploymentCompleted(w)
            }

            else -> {
                GenericEvent(
                    typeKey = w.action ?: "github_actions_event",
                    metadata =
                        mapOf(
                            "repo" to w.repository.fullName,
                            "event_type" to (w.action ?: "unknown"),
                        ),
                    payload = mapOf("event_data" to payload),
                )
            }
        }
    }

    private fun parseDeploymentStarted(w: GitHubActionsPayload): DeployStartedEvent {
        val d = w.deployment!!
        return DeployStartedEvent(
            service = d.environment,
            environment = d.environment,
            author = d.creator.login,
            pipeline = d.ref,
            deploymentUrl = d.url,
            status = w.action ?: "created",
            createdAt = d.createdAt,
        )
    }

    private fun parseDeploymentCompleted(w: GitHubActionsPayload): DeployCompletedEvent {
        val d = w.deployment!!
        val s = w.deploymentStatus!!
        return DeployCompletedEvent(
            service = d.environment,
            environment = d.environment,
            author = d.creator.login,
            pipeline = d.ref,
            status = s.state,
            deploymentUrl = d.url,
            statusUrl = s.url,
            completedAt = s.createdAt,
        )
    }
}
