package com.notifier.router.api.adapter

import com.notifier.router.api.domain.GenericEvent
import com.notifier.router.api.domain.NotificationEvent
import com.notifier.router.api.domain.PrCheckCompletedEvent
import com.notifier.router.api.domain.PrCheckReRequestedEvent
import com.notifier.router.api.domain.PrClosedEvent
import com.notifier.router.api.domain.PrCreatedEvent
import com.notifier.router.api.domain.PrReviewRequestedEvent
import com.notifier.router.api.domain.PrUpdatedEvent
import tools.jackson.module.kotlin.readValue

object GitHubWebhookAdapter {
    fun parse(payload: String): NotificationEvent {
        val w = webhookMapper.readValue<GitHubWebhookPayload>(payload)
        return when (w.action) {
            "opened" -> {
                parsePRCreated(w)
            }

            "review_requested" -> {
                parsePRReviewRequested(w)
            }

            "synchronize" -> {
                parsePRUpdated(w)
            }

            "closed" -> {
                parsePRClosed(w)
            }

            "rerequested" -> {
                parsePRCheckRerequested(w)
            }

            "completed" -> {
                parsePRCheckCompleted(w)
            }

            else -> {
                GenericEvent(
                    typeKey = w.action ?: "github_event",
                    metadata =
                        mapOf(
                            "repo" to w.repository.fullName,
                            "event_type" to (w.action ?: "unknown"),
                        ),
                    rawPayload = mapOf("event_data" to payload),
                )
            }
        }
    }

    private fun parsePRCreated(w: GitHubWebhookPayload): PrCreatedEvent {
        val pr = w.pullRequest!!
        return PrCreatedEvent(
            author = pr.user.login,
            repo = w.repository.fullName,
            baseBranch = pr.base.ref,
            title = pr.title,
            description = pr.body,
            url = pr.htmlUrl,
            createdAt = pr.createdAt,
        )
    }

    private fun parsePRReviewRequested(w: GitHubWebhookPayload): PrReviewRequestedEvent {
        val pr = w.pullRequest!!
        return PrReviewRequestedEvent(
            author = pr.user.login,
            repo = w.repository.fullName,
            reviewer = w.requestedReviewer?.login ?: "",
            baseBranch = pr.base.ref,
            title = pr.title,
            url = pr.htmlUrl,
            requestedAt = w.requestedAt ?: "",
        )
    }

    private fun parsePRUpdated(w: GitHubWebhookPayload): PrUpdatedEvent {
        val pr = w.pullRequest!!
        return PrUpdatedEvent(
            author = pr.user.login,
            repo = w.repository.fullName,
            baseBranch = pr.base.ref,
            title = pr.title,
            url = pr.htmlUrl,
            updatedAt = pr.updatedAt,
        )
    }

    private fun parsePRClosed(w: GitHubWebhookPayload): PrClosedEvent {
        val pr = w.pullRequest!!
        return PrClosedEvent(
            merged = pr.merged,
            author = pr.user.login,
            repo = w.repository.fullName,
            baseBranch = pr.base.ref,
            title = pr.title,
            url = pr.htmlUrl,
            mergedAt = pr.mergedAt ?: "",
            mergedBy = pr.mergedBy?.login ?: "",
        )
    }

    private fun parsePRCheckRerequested(w: GitHubWebhookPayload): PrCheckReRequestedEvent {
        val pr = w.pullRequest!!
        val suite = w.checkSuite!!
        return PrCheckReRequestedEvent(
            author = w.sender.login,
            repo = w.repository.fullName,
            checkName = suite.app.name,
            baseBranch = pr.base.ref,
            checkSuiteUrl = suite.htmlUrl,
            reRequestedAt = w.requestedAt ?: "",
        )
    }

    private fun parsePRCheckCompleted(w: GitHubWebhookPayload): PrCheckCompletedEvent {
        val run = w.checkRun!!
        val pr = w.pullRequest!!
        return PrCheckCompletedEvent(
            conclusion = run.conclusion ?: "",
            author = w.sender.login,
            repo = w.repository.fullName,
            checkName = run.name,
            baseBranch = pr.base.ref,
            checkRunUrl = run.htmlUrl,
            completedAt = run.completedAt ?: "",
        )
    }
}
