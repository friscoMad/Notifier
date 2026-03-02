package com.notifier.router.api.adapter

import com.notifier.router.api.domain.BuildFinishedEvent
import com.notifier.router.api.domain.GenericEvent
import com.notifier.router.api.domain.JobFinishedEvent
import com.notifier.router.api.domain.NotificationEvent
import com.notifier.router.api.domain.PipelineUpdatedEvent
import tools.jackson.module.kotlin.readValue

object BuildkiteWebhookAdapter {
    fun parse(payload: String): NotificationEvent {
        val w = webhookMapper.readValue<BuildkitePayload>(payload)
        return when (w.event) {
            "job.finished" -> {
                parseJobFinished(w)
            }

            "pipeline.updated" -> {
                parsePipelineUpdated(w)
            }

            "build.finished" -> {
                parseBuildFinished(w)
            }

            else -> {
                GenericEvent(
                    typeKey = w.event ?: "buildkite_event",
                    metadata =
                        mapOf(
                            "service" to w.pipeline.slug,
                            "event_type" to (w.event ?: "unknown"),
                        ),
                    payload = mapOf("event_data" to payload),
                )
            }
        }
    }

    private fun parseJobFinished(w: BuildkitePayload): JobFinishedEvent {
        val job = w.job!!
        return JobFinishedEvent(
            service = w.pipeline.slug,
            testName = job.name,
            team = w.pipeline.team ?: "",
            status = job.state,
            jobUrl = job.webUrl,
            buildUrl = w.build!!.webUrl,
            finishedAt = job.finishedAt ?: "",
        )
    }

    private fun parsePipelineUpdated(w: BuildkitePayload) =
        PipelineUpdatedEvent(
            service = w.pipeline.slug,
            team = w.pipeline.team ?: "",
            pipelineUrl = w.pipeline.webUrl,
            updatedAt = w.updatedAt ?: "",
        )

    private fun parseBuildFinished(w: BuildkitePayload): BuildFinishedEvent {
        val build = w.build!!
        return BuildFinishedEvent(
            service = w.pipeline.slug,
            environment = build.message,
            status = build.state,
            buildUrl = build.webUrl,
            finishedAt = build.finishedAt ?: "",
        )
    }
}
