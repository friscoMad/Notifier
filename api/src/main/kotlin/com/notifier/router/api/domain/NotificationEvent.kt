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
                "content" to "🔔 *PR Opened*\n<$url|$title>\n*Repo:* $repo  ·  *Branch:* $baseBranch  ·  *Author:* $author",
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
                "content" to "👀 *Review Requested*\n<$url|$title>\n*Reviewer:* $reviewer  ·  *Repo:* $repo  ·  *Author:* $author",
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
                "content" to "🔄 *PR Updated*\n<$url|$title>\n*Repo:* $repo  ·  *Branch:* $baseBranch  ·  *Author:* $author",
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
                "content" to if (merged) {
                    "✅ *PR Merged*\n<$url|$title>\n*Repo:* $repo  ·  *Merged by:* $mergedBy"
                } else {
                    "❌ *PR Closed (unmerged)*\n<$url|$title>\n*Repo:* $repo  ·  *Author:* $author"
                },
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
                "content" to "🔁 *Check Re-requested:* $checkName\n<$checkSuiteUrl|View suite>  ·  *Repo:* $repo  ·  *Author:* $author",
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
                "content" to if (conclusion == "success") {
                    "✅ *Check Passed:* $checkName\n<$checkRunUrl|View run>  ·  *Repo:* $repo  ·  *Author:* $author"
                } else {
                    "❌ *Check Failed:* $checkName\n<$checkRunUrl|View run>  ·  *Repo:* $repo  ·  *Author:* $author"
                },
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
                "content" to "🚀 *Deploy Started*\n*Service:* $service  ·  *Environment:* $environment  ·  *Author:* $author",
                "title" to "🚀 Deploy Started — $service ($environment)",
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
                "content" to if (status == "success") {
                    "✅ *Deploy Completed*\n<$deploymentUrl|$service>  ·  *Environment:* $environment  ·  *Author:* $author"
                } else {
                    "❌ *Deploy Failed*\n<$deploymentUrl|$service>  ·  *Environment:* $environment  ·  *Status:* $status  ·  *Author:* $author"
                },
                "title" to if (status == "success") {
                    "✅ Deploy Completed — $service ($environment)"
                } else {
                    "❌ Deploy Failed — $service ($environment)"
                },
                "deployment_url" to deploymentUrl,
                "status_url" to statusUrl,
                "status" to status,
                "completed_at" to completedAt,
            )
}

// ── Buildkite Events ──────────────────────────────────────────

data class BuildScheduledEvent(
    val pipeline: String,
    val buildNumber: Int,
    val branch: String,
    val creator: String,
    val buildUrl: String,
    val buildMessage: String = "",
) : NotificationEvent {
    override val typeKey = "buildkite_build_scheduled"
    override val metadata
        get() = mapOf("pipeline" to pipeline, "branch" to branch, "creator" to creator)
    override val payload: Map<String, Any>
        get() {
            val linkText = if (buildMessage.isNotBlank()) buildMessage else "#$buildNumber"
            return mapOf(
                "content" to "🕐 *Build Scheduled* — <$buildUrl|$linkText>\n*Branch:* $branch  ·  *By:* $creator  ·  *Pipeline:* $pipeline  ·  *Build:* <$buildUrl|#$buildNumber>",
                "title" to "🕐 Build Scheduled — $branch ($pipeline #$buildNumber)",
                "build_url" to buildUrl,
            )
        }
}

data class BuildRunningEvent(
    val pipeline: String,
    val buildNumber: Int,
    val branch: String,
    val creator: String,
    val buildUrl: String,
    val buildMessage: String = "",
) : NotificationEvent {
    override val typeKey = "buildkite_build_running"
    override val metadata
        get() = mapOf("pipeline" to pipeline, "branch" to branch, "creator" to creator)
    override val payload: Map<String, Any>
        get() {
            val linkText = if (buildMessage.isNotBlank()) buildMessage else "#$buildNumber"
            return mapOf(
                "content" to "🏃 *Build Running* — <$buildUrl|$linkText>\n*Branch:* $branch  ·  *By:* $creator  ·  *Pipeline:* $pipeline  ·  *Build:* <$buildUrl|#$buildNumber>",
                "title" to "🏃 Build Running — $branch ($pipeline #$buildNumber)",
                "build_url" to buildUrl,
            )
        }
}

data class BuildFinishedEvent(
    val pipeline: String,
    val buildNumber: Int,
    val branch: String,
    val creator: String,
    val status: String,
    val buildUrl: String,
    val finishedAt: String,
    val buildMessage: String = "",
) : NotificationEvent {
    override val typeKey = "buildkite_build_finished"
    override val metadata
        get() = mapOf("pipeline" to pipeline, "branch" to branch, "creator" to creator, "status" to status)
    override val payload: Map<String, Any>
        get() {
            val linkText = if (buildMessage.isNotBlank()) buildMessage else "#$buildNumber"
            val line2 = "*Branch:* $branch  ·  *By:* $creator  ·  *Pipeline:* $pipeline  ·  *Build:* <$buildUrl|#$buildNumber>"
            return mapOf(
                "content" to if (status == "passed") {
                    "✅ *Build Passed* — <$buildUrl|$linkText>\n$line2"
                } else {
                    "❌ *Build Failed* — <$buildUrl|$linkText>\n$line2"
                },
                "title" to if (status == "passed") {
                    "✅ Build Passed — $branch ($pipeline #$buildNumber)"
                } else {
                    "❌ Build Failed — $branch ($pipeline #$buildNumber)"
                },
                "build_url" to buildUrl,
                "finished_at" to finishedAt,
                "state" to status,
            )
        }
}

data class JobScheduledEvent(
    val pipeline: String,
    val jobName: String,
    val buildNumber: Int,
    val branch: String,
    val creator: String,
    val jobUrl: String,
    val buildUrl: String,
    val buildMessage: String = "",
) : NotificationEvent {
    override val typeKey = "buildkite_job_scheduled"
    override val metadata
        get() = mapOf("pipeline" to pipeline, "job_name" to jobName, "branch" to branch)
    override val payload: Map<String, Any>
        get() {
            val linkText = if (buildMessage.isNotBlank()) "$buildMessage - $jobName" else jobName
            return mapOf(
                "content" to "🕐 *Job Scheduled* — <$jobUrl|$linkText>\n*Branch:* $branch  ·  *By:* $creator  ·  *Pipeline:* $pipeline  ·  *Build:* <$buildUrl|#$buildNumber>",
                "title" to "🕐 Job Scheduled — $jobName | $pipeline #$buildNumber",
                "job_url" to jobUrl,
                "build_url" to buildUrl,
            )
        }
}

data class JobStartedEvent(
    val pipeline: String,
    val jobName: String,
    val buildNumber: Int,
    val branch: String,
    val creator: String,
    val agentName: String,
    val jobUrl: String,
    val buildUrl: String,
) : NotificationEvent {
    override val typeKey = "buildkite_job_started"
    override val metadata
        get() = mapOf("pipeline" to pipeline, "job_name" to jobName, "branch" to branch)
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "🏃 *Job Started* — <$jobUrl|$jobName>\n*Branch:* $branch  ·  *By:* $creator  ·  *Pipeline:* $pipeline  ·  *Build:* <$buildUrl|#$buildNumber>",
                "title" to "🏃 Job Started — $jobName | $pipeline #$buildNumber",
                "job_url" to jobUrl,
                "build_url" to buildUrl,
            )
}

data class JobFinishedEvent(
    val pipeline: String,
    val jobName: String,
    val buildNumber: Int,
    val branch: String,
    val creator: String,
    val status: String,
    val exitStatus: Int?,
    val jobUrl: String,
    val buildUrl: String,
    val finishedAt: String,
) : NotificationEvent {
    override val typeKey = "buildkite_job_finished"
    override val metadata
        get() =
            mapOf(
                "pipeline" to pipeline,
                "job_name" to jobName,
                "branch" to branch,
                "status" to status,
            )
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to if (status == "passed") {
                    "✅ *Job Passed* — <$jobUrl|$jobName>\n*Branch:* $branch  ·  *By:* $creator  ·  *Pipeline:* $pipeline  ·  *Build:* <$buildUrl|#$buildNumber>"
                } else {
                    "❌ *Job Failed* — <$jobUrl|$jobName>\n*Branch:* $branch  ·  *By:* $creator  ·  *Pipeline:* $pipeline  ·  *Build:* <$buildUrl|#$buildNumber>  ·  *Exit:* ${exitStatus ?: "?"}"
                },
                "title" to if (status == "passed") {
                    "✅ Job Passed — $jobName | $pipeline #$buildNumber"
                } else {
                    "❌ Job Failed — $jobName | $pipeline #$buildNumber"
                },
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
                "content" to "🔧 *Pipeline Updated*\n<$pipelineUrl|$service>  ·  *Team:* $team",
                "title" to "🔧 Pipeline Updated — $service",
                "pipeline_url" to pipelineUrl,
                "updated_at" to updatedAt,
            )
}

data class AgentConnectedEvent(
    val agentName: String,
    val hostname: String,
    val agentUrl: String,
) : NotificationEvent {
    override val typeKey = "buildkite_agent_connected"
    override val metadata
        get() = mapOf("agent_name" to agentName, "hostname" to hostname)
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "🟢 *Agent Connected:* $agentName\n*Host:* $hostname\n<$agentUrl|View agent>",
                "title" to "🟢 Agent Connected — $agentName",
                "agent_url" to agentUrl,
            )
}

data class AgentDisconnectedEvent(
    val agentName: String,
    val hostname: String,
    val agentUrl: String,
) : NotificationEvent {
    override val typeKey = "buildkite_agent_disconnected"
    override val metadata
        get() = mapOf("agent_name" to agentName, "hostname" to hostname)
    override val payload
        get() =
            mapOf<String, Any>(
                "content" to "🔴 *Agent Disconnected:* $agentName\n*Host:* $hostname\n<$agentUrl|View agent>",
                "title" to "🔴 Agent Disconnected — $agentName",
                "agent_url" to agentUrl,
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
        get() = mapOf("content" to "🔔 *Notification:* $typeKey") + rawPayload
}
