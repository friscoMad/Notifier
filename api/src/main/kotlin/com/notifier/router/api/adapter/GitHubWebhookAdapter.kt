package com.notifier.router.api.adapter

import com.fasterxml.jackson.module.kotlin.readValue
import com.notifier.router.api.domain.Event

object GitHubWebhookAdapter {
    fun parse(payload: String): Event {
        val webhook = webhookMapper.readValue<GitHubWebhookPayload>(payload)
        return when (webhook.action) {
            "opened" -> parsePRCreated(webhook)
            "review_requested" -> parsePRReviewRequested(webhook)
            "synchronize" -> parsePRUpdated(webhook)
            "closed" -> parsePRClosed(webhook)
            "rerequested" -> parsePRCheckRerequested(webhook)
            "completed" -> parsePRCheckCompleted(webhook)
            else -> parseGenericEvent(webhook, payload)
        }
    }

    private fun parsePRCreated(w: GitHubWebhookPayload): Event {
        val pr = w.pullRequest!!
        return Event(
            typeKey = "pr_created",
            metadata =
                mapOf(
                    "author" to pr.user.login,
                    "repo" to w.repository.fullName,
                    "base_branch" to pr.base.ref,
                ),
            payload =
                mapOf(
                    "title" to pr.title,
                    "description" to pr.body,
                    "url" to pr.htmlUrl,
                    "created_at" to pr.createdAt,
                ),
        )
    }

    private fun parsePRReviewRequested(w: GitHubWebhookPayload): Event {
        val pr = w.pullRequest!!
        return Event(
            typeKey = "pr_review_requested",
            metadata =
                mapOf(
                    "author" to pr.user.login,
                    "repo" to w.repository.fullName,
                    "reviewer" to (w.requestedReviewer?.login ?: ""),
                    "base_branch" to pr.base.ref,
                ),
            payload =
                mapOf(
                    "title" to pr.title,
                    "url" to pr.htmlUrl,
                    "requested_at" to (w.requestedAt ?: ""),
                ),
        )
    }

    private fun parsePRUpdated(w: GitHubWebhookPayload): Event {
        val pr = w.pullRequest!!
        return Event(
            typeKey = "pr_updated",
            metadata =
                mapOf(
                    "author" to pr.user.login,
                    "repo" to w.repository.fullName,
                    "base_branch" to pr.base.ref,
                ),
            payload =
                mapOf(
                    "title" to pr.title,
                    "url" to pr.htmlUrl,
                    "updated_at" to pr.updatedAt,
                ),
        )
    }

    private fun parsePRClosed(w: GitHubWebhookPayload): Event {
        val pr = w.pullRequest!!
        return Event(
            typeKey = if (pr.merged) "pr_merged_master_success" else "pr_merged_master_error",
            metadata =
                mapOf(
                    "author" to pr.user.login,
                    "repo" to w.repository.fullName,
                    "base_branch" to pr.base.ref,
                ),
            payload =
                mapOf(
                    "title" to pr.title,
                    "url" to pr.htmlUrl,
                    "merged_at" to (pr.mergedAt ?: ""),
                    "merged_by" to (pr.mergedBy?.login ?: ""),
                ),
        )
    }

    private fun parsePRCheckRerequested(w: GitHubWebhookPayload): Event {
        val pr = w.pullRequest!!
        val suite = w.checkSuite!!
        return Event(
            typeKey = "pr_checks_failed",
            metadata =
                mapOf(
                    "author" to w.sender.login,
                    "repo" to w.repository.fullName,
                    "check_name" to suite.app.name,
                    "base_branch" to pr.base.ref,
                ),
            payload =
                mapOf(
                    "check_suite_url" to suite.htmlUrl,
                    "rerequested_at" to (w.requestedAt ?: ""),
                ),
        )
    }

    private fun parsePRCheckCompleted(w: GitHubWebhookPayload): Event {
        val run = w.checkRun!!
        val pr = w.pullRequest!!
        return Event(
            typeKey =
                if (run.conclusion == "success") "pr_checks_passed" else "pr_checks_failed",
            metadata =
                mapOf(
                    "author" to w.sender.login,
                    "repo" to w.repository.fullName,
                    "check_name" to run.name,
                    "base_branch" to pr.base.ref,
                ),
            payload =
                mapOf(
                    "check_run_url" to run.htmlUrl,
                    "conclusion" to (run.conclusion ?: ""),
                    "completed_at" to (run.completedAt ?: ""),
                ),
        )
    }

    private fun parseGenericEvent(
        w: GitHubWebhookPayload,
        rawPayload: String,
    ) = Event(
        typeKey = w.action ?: "github_event",
        metadata =
            mapOf(
                "repo" to w.repository.fullName,
                "event_type" to (w.action ?: "unknown"),
            ),
        payload = mapOf("event_data" to rawPayload),
    )
}
