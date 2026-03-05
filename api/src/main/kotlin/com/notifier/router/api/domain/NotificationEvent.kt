package com.notifier.router.api.domain

import java.time.Instant

/** Sealed interface for all notification events. Each subtype has typed fields. */
sealed interface NotificationEvent {
    val typeKey: String
    val metadata: Map<String, Any>
    val payload: Map<String, Any>
    val timestamp: Instant
        get() = Instant.now()
}

// ── GitHub PR Events ──────────────────────────────────────────

data class PrCreatedEvent(
    val author: String,
    val repo: String,
    val baseBranch: String,
    val title: String,
    val description: String,
    val url: String,
    val createdAt: String,
) : NotificationEvent {
    override val typeKey = "pr_created"
    override val metadata
        get() = mapOf("author" to author, "repo" to repo, "base_branch" to baseBranch)
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "PR opened by $author: $title ($repo)",
                "title" to title,
                "description" to description,
                "url" to url,
                "created_at" to createdAt,
            )
}

data class PrReviewRequestedEvent(
    val author: String,
    val repo: String,
    val reviewer: String,
    val baseBranch: String,
    val title: String,
    val url: String,
    val requestedAt: String,
) : NotificationEvent {
    override val typeKey = "pr_review_requested"
    override val metadata
        get() =
            mapOf(
                "author" to author,
                "repo" to repo,
                "reviewer" to reviewer,
                "base_branch" to baseBranch,
            )
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "$reviewer requested to review: $title",
                "title" to title,
                "url" to url,
                "requested_at" to requestedAt,
            )
}

data class PrUpdatedEvent(
    val author: String,
    val repo: String,
    val baseBranch: String,
    val title: String,
    val url: String,
    val updatedAt: String,
) : NotificationEvent {
    override val typeKey = "pr_updated"
    override val metadata
        get() = mapOf("author" to author, "repo" to repo, "base_branch" to baseBranch)
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "PR updated by $author: $title ($repo)",
                "title" to title,
                "url" to url,
                "updated_at" to updatedAt,
            )
}

data class PrClosedEvent(
    val merged: Boolean,
    val author: String,
    val repo: String,
    val baseBranch: String,
    val title: String,
    val url: String,
    val mergedAt: String,
    val mergedBy: String,
) : NotificationEvent {
    override val typeKey = if (merged) "pr_merged_master_success" else "pr_merged_master_error"
    override val metadata
        get() = mapOf("author" to author, "repo" to repo, "base_branch" to baseBranch)
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "PR ${if (merged) "merged" else "closed"}: $title ($repo)",
                "title" to title,
                "url" to url,
                "merged_at" to mergedAt,
                "merged_by" to mergedBy,
            )
}

// ── GitHub Check Events ───────────────────────────────────────

data class PrCheckReRequestedEvent(
    val author: String,
    val repo: String,
    val checkName: String,
    val baseBranch: String,
    val checkSuiteUrl: String,
    val reRequestedAt: String,
) : NotificationEvent {
    override val typeKey = "pr_checks_failed"
    override val metadata
        get() =
            mapOf(
                "author" to author,
                "repo" to repo,
                "check_name" to checkName,
                "base_branch" to baseBranch,
            )
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "Check $checkName re-requested on $repo",
                "check_suite_url" to checkSuiteUrl,
                "rerequested_at" to reRequestedAt,
            )
}

data class PrCheckCompletedEvent(
    val conclusion: String,
    val author: String,
    val repo: String,
    val checkName: String,
    val baseBranch: String,
    val checkRunUrl: String,
    val completedAt: String,
) : NotificationEvent {
    override val typeKey = if (conclusion == "success") "pr_checks_passed" else "pr_checks_failed"
    override val metadata
        get() =
            mapOf(
                "author" to author,
                "repo" to repo,
                "check_name" to checkName,
                "base_branch" to baseBranch,
            )
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "Check $checkName $conclusion on $repo",
                "check_run_url" to checkRunUrl,
                "conclusion" to conclusion,
                "completed_at" to completedAt,
            )
}

// ── Deployment Events ─────────────────────────────────────────

data class DeployStartedEvent(
    val service: String,
    val environment: String,
    val author: String,
    val pipeline: String,
    val deploymentUrl: String,
    val status: String,
    val createdAt: String,
) : NotificationEvent {
    override val typeKey = "deploy_started"
    override val metadata
        get() =
            mapOf(
                "service" to service,
                "environment" to environment,
                "author" to author,
                "pipeline" to pipeline,
            )
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "Deploy of $service to $environment started",
                "deployment_url" to deploymentUrl,
                "status" to status,
                "created_at" to createdAt,
            )
}

data class DeployCompletedEvent(
    val service: String,
    val environment: String,
    val author: String,
    val pipeline: String,
    val status: String,
    val deploymentUrl: String,
    val statusUrl: String,
    val completedAt: String,
) : NotificationEvent {
    override val typeKey = "deploy_completed"
    override val metadata
        get() =
            mapOf(
                "service" to service,
                "environment" to environment,
                "author" to author,
                "pipeline" to pipeline,
                "status" to status,
            )
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "Deploy of $service to $environment: $status",
                "deployment_url" to deploymentUrl,
                "status_url" to statusUrl,
                "status" to status,
                "completed_at" to completedAt,
            )
}

// ── Buildkite Events ──────────────────────────────────────────

data class JobFinishedEvent(
    val service: String,
    val testName: String,
    val team: String,
    val status: String,
    val jobUrl: String,
    val buildUrl: String,
    val finishedAt: String,
) : NotificationEvent {
    override val typeKey = if (status == "passed") "pr_checks_passed" else "pr_checks_failed"
    override val metadata
        get() =
            mapOf(
                "service" to service,
                "test_name" to testName,
                "team" to team,
                "status" to status,
            )
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "Build $status for $service ($testName)",
                "job_url" to jobUrl,
                "build_url" to buildUrl,
                "finished_at" to finishedAt,
                "state" to status,
            )
}

data class PipelineUpdatedEvent(
    val service: String,
    val team: String,
    val pipelineUrl: String,
    val updatedAt: String,
) : NotificationEvent {
    override val typeKey = "pipeline_updated"
    override val metadata
        get() = mapOf("service" to service, "team" to team)
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "Pipeline updated for $service",
                "pipeline_url" to pipelineUrl,
                "updated_at" to updatedAt,
            )
}

data class BuildFinishedEvent(
    val service: String,
    val environment: String,
    val status: String,
    val buildUrl: String,
    val finishedAt: String,
) : NotificationEvent {
    override val typeKey = if (status == "passed") "deploy_completed" else "deploy_failed"
    override val metadata
        get() =
            mapOf(
                "service" to service,
                "environment" to environment,
                "status" to status,
            )
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "Build $status for $service on $environment",
                "build_url" to buildUrl,
                "finished_at" to finishedAt,
                "state" to status,
            )
}

// ── Generic / Fallback ────────────────────────────────────────

/** Generic event for the REST API (EventController) and unknown webhook types. */
data class GenericEvent(
    override val typeKey: String,
    override val metadata: Map<String, Any> = emptyMap(),
    private val rawPayload: Map<String, Any> = emptyMap(),
) : NotificationEvent {
    override val payload
        get() = mapOf("content" to "Notification: $typeKey") + rawPayload
}
