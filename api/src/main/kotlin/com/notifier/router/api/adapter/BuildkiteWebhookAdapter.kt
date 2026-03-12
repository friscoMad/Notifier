package com.notifier.router.api.adapter

import com.notifier.router.api.domain.AgentConnectedEvent
import com.notifier.router.api.domain.AgentDisconnectedEvent
import com.notifier.router.api.domain.BuildFinishedEvent
import com.notifier.router.api.domain.BuildRunningEvent
import com.notifier.router.api.domain.BuildScheduledEvent
import com.notifier.router.api.domain.GenericEvent
import com.notifier.router.api.domain.JobFinishedEvent
import com.notifier.router.api.domain.JobScheduledEvent
import com.notifier.router.api.domain.JobStartedEvent
import com.notifier.router.api.domain.NotificationEvent
import com.notifier.router.api.domain.PipelineUpdatedEvent
import tools.jackson.module.kotlin.readValue

object BuildkiteWebhookAdapter {
    fun parse(payload: String): NotificationEvent {
        val w = webhookMapper.readValue<BuildkitePayload>(payload)
        return when (w.event) {
            "build.scheduled" -> parseBuildScheduled(w)
            "build.running" -> parseBuildRunning(w)
            "build.finished" -> parseBuildFinished(w)
            "job.scheduled" -> parseJobScheduled(w)
            "job.started" -> parseJobStarted(w)
            "job.finished" -> parseJobFinished(w)
            "pipeline.updated" -> parsePipelineUpdated(w)

            "agent.connected" -> {
                val a = webhookMapper.readValue<BuildkiteAgentPayload>(payload)
                AgentConnectedEvent(
                    agentName = a.agent.name,
                    hostname = a.agent.hostname,
                    agentUrl = a.agent.webUrl,
                )
            }

            "agent.disconnected" -> {
                val a = webhookMapper.readValue<BuildkiteAgentPayload>(payload)
                AgentDisconnectedEvent(
                    agentName = a.agent.name,
                    hostname = a.agent.hostname,
                    agentUrl = a.agent.webUrl,
                )
            }

            "ping" -> {
                GenericEvent(
                    typeKey = "buildkite_ping",
                    metadata = emptyMap(),
                    rawPayload = emptyMap(),
                )
            }

            else -> {
                GenericEvent(
                    typeKey = w.event ?: "buildkite_event",
                    metadata =
                    mapOf(
                        "pipeline" to w.pipeline.slug,
                        "event_type" to (w.event ?: "unknown"),
                    ),
                    rawPayload = mapOf("event_data" to payload),
                )
            }
        }
    }

    private fun parseBuildScheduled(w: BuildkitePayload): BuildScheduledEvent {
        val build = w.build!!
        return BuildScheduledEvent(
            pipeline = w.pipeline.slug,
            buildNumber = build.number,
            branch = build.branch,
            creator = build.creator?.name ?: "",
            buildUrl = build.webUrl,
            buildMessage = build.message,
        )
    }

    private fun parseBuildRunning(w: BuildkitePayload): BuildRunningEvent {
        val build = w.build!!
        return BuildRunningEvent(
            pipeline = w.pipeline.slug,
            buildNumber = build.number,
            branch = build.branch,
            creator = build.creator?.name ?: "",
            buildUrl = build.webUrl,
            buildMessage = build.message,
        )
    }

    private fun parseBuildFinished(w: BuildkitePayload): BuildFinishedEvent {
        val build = w.build!!
        return BuildFinishedEvent(
            pipeline = w.pipeline.slug,
            buildNumber = build.number,
            branch = build.branch,
            creator = build.creator?.name ?: "",
            status = build.state,
            buildUrl = build.webUrl,
            finishedAt = build.finishedAt ?: "",
            buildMessage = build.message,
        )
    }

    private fun parseJobScheduled(w: BuildkitePayload): JobScheduledEvent {
        val job = w.job!!
        val build = w.build!!
        return JobScheduledEvent(
            pipeline = w.pipeline.slug,
            jobName = job.name,
            buildNumber = build.number,
            branch = build.branch,
            creator = build.creator?.name ?: "",
            jobUrl = job.webUrl,
            buildUrl = build.webUrl,
            buildMessage = build.message,
        )
    }

    private fun parseJobStarted(w: BuildkitePayload): JobStartedEvent {
        val job = w.job!!
        val build = w.build!!
        return JobStartedEvent(
            pipeline = w.pipeline.slug,
            jobName = job.name,
            buildNumber = build.number,
            branch = build.branch,
            creator = build.creator?.name ?: "",
            agentName = job.agent?.name ?: "",
            jobUrl = job.webUrl,
            buildUrl = build.webUrl,
            buildMessage = build.message,
        )
    }

    private fun parseJobFinished(w: BuildkitePayload): JobFinishedEvent {
        val job = w.job!!
        val build = w.build!!
        return JobFinishedEvent(
            pipeline = w.pipeline.slug,
            jobName = job.name,
            buildNumber = build.number,
            branch = build.branch,
            creator = build.creator?.name ?: "",
            status = job.state,
            exitStatus = job.exitStatus,
            jobUrl = job.webUrl,
            buildUrl = build.webUrl,
            finishedAt = job.finishedAt ?: "",
            buildMessage = build.message,
        )
    }

    private fun parsePipelineUpdated(w: BuildkitePayload) =
        PipelineUpdatedEvent(
            service = w.pipeline.slug,
            team = w.pipeline.team ?: "",
            pipelineUrl = w.pipeline.webUrl,
            updatedAt = w.updatedAt ?: "",
        )
}
