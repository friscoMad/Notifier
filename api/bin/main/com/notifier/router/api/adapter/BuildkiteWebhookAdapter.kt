package com.notifier.router.api.adapter

import com.fasterxml.jackson.module.kotlin.readValue
import com.notifier.router.api.domain.Event

object BuildkiteWebhookAdapter {
    fun parse(payload: String): Event {
        val webhook = webhookMapper.readValue<BuildkitePayload>(payload)
        return when (webhook.event) {
            "job.finished" -> parseJobFinished(webhook)
            "pipeline.updated" -> parsePipelineUpdated(webhook)
            "build.finished" -> parseBuildFinished(webhook)
            else -> parseGenericEvent(webhook, payload)
        }
    }

    private fun parseJobFinished(w: BuildkitePayload): Event {
        val job = w.job!!
        val build = w.build!!
        return Event(
            typeKey = if (job.state == "passed") "pr_checks_passed" else "pr_checks_failed",
            metadata =
                mapOf(
                    "service" to w.pipeline.slug,
                    "test_name" to job.name,
                    "team" to (w.pipeline.team ?: ""),
                    "status" to job.state,
                ),
            payload =
                mapOf(
                    "job_url" to job.webUrl,
                    "build_url" to build.webUrl,
                    "finished_at" to (job.finishedAt ?: ""),
                    "state" to job.state,
                ),
        )
    }

    private fun parsePipelineUpdated(w: BuildkitePayload) =
        Event(
            typeKey = "pipeline_updated",
            metadata =
                mapOf(
                    "service" to w.pipeline.slug,
                    "team" to (w.pipeline.team ?: ""),
                ),
            payload =
                mapOf(
                    "pipeline_url" to w.pipeline.webUrl,
                    "updated_at" to (w.updatedAt ?: ""),
                ),
        )

    private fun parseBuildFinished(w: BuildkitePayload): Event {
        val build = w.build!!
        return Event(
            typeKey = if (build.state == "passed") "deploy_completed" else "deploy_failed",
            metadata =
                mapOf(
                    "service" to w.pipeline.slug,
                    "environment" to build.message,
                    "status" to build.state,
                ),
            payload =
                mapOf(
                    "build_url" to build.webUrl,
                    "finished_at" to (build.finishedAt ?: ""),
                    "state" to build.state,
                ),
        )
    }

    private fun parseGenericEvent(
        w: BuildkitePayload,
        rawPayload: String,
    ) = Event(
        typeKey = w.event ?: "buildkite_event",
        metadata =
            mapOf(
                "service" to w.pipeline.slug,
                "event_type" to (w.event ?: "unknown"),
            ),
        payload = mapOf("event_data" to rawPayload),
    )
}
