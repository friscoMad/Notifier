package com.notifier.router.api.adapter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.notifier.router.api.domain.Event

object BuildkiteWebhookAdapter {
    private val mapper = jacksonObjectMapper()

    fun parse(payload: String): Event {
        val jsonNode = mapper.readTree(payload)

        return when (jsonNode["event"]?.asText()) {
            "job.finished" -> parseJobFinished(jsonNode)
            "pipeline.updated" -> parsePipelineUpdated(jsonNode)
            "build.finished" -> parseBuildFinished(jsonNode)
            else -> parseGenericBuildkiteEvent(jsonNode)
        }
    }

    private fun parseJobFinished(jsonNode: JsonNode): Event {
        val job = jsonNode["job"]
        val build = jsonNode["build"]
        val pipeline = jsonNode["pipeline"]

        val typeKey = if (job["state"].asText() == "passed") "pr_checks_passed" else "pr_checks_failed"

        return Event(
            typeKey = typeKey,
            metadata =
                mapOf(
                    "service" to pipeline["slug"].asText(),
                    "test_name" to job["name"].asText(),
                    "team" to pipeline["team"].asText(),
                    "status" to job["state"].asText(),
                ),
            payload =
                mapOf(
                    "job_url" to job["web_url"].asText(),
                    "build_url" to build["web_url"].asText(),
                    "finished_at" to job["finished_at"].asText(),
                    "state" to job["state"].asText(),
                ),
        )
    }

    private fun parsePipelineUpdated(jsonNode: JsonNode): Event {
        val pipeline = jsonNode["pipeline"]

        return Event(
            typeKey = "pipeline_updated",
            metadata =
                mapOf(
                    "service" to pipeline["slug"].asText(),
                    "team" to pipeline["team"].asText(),
                ),
            payload =
                mapOf(
                    "pipeline_url" to pipeline["web_url"].asText(),
                    "updated_at" to jsonNode["updated_at"].asText(),
                ),
        )
    }

    private fun parseBuildFinished(jsonNode: JsonNode): Event {
        val build = jsonNode["build"]
        val pipeline = jsonNode["pipeline"]

        val typeKey = if (build["state"].asText() == "passed") "deploy_completed" else "deploy_failed"

        return Event(
            typeKey = typeKey,
            metadata =
                mapOf(
                    "service" to pipeline["slug"].asText(),
                    "environment" to build["message"].asText(),
                    "status" to build["state"].asText(),
                ),
            payload =
                mapOf(
                    "build_url" to build["web_url"].asText(),
                    "finished_at" to build["finished_at"].asText(),
                    "state" to build["state"].asText(),
                ),
        )
    }

    private fun parseGenericBuildkiteEvent(jsonNode: JsonNode): Event {
        val pipeline = jsonNode["pipeline"]

        return Event(
            typeKey = jsonNode["event"]?.asText() ?: "buildkite_event",
            metadata =
                mapOf(
                    "service" to pipeline["slug"].asText(),
                    "event_type" to (jsonNode["event"]?.asText() ?: "unknown"),
                ),
            payload =
                mapOf(
                    "event_data" to jsonNode.toString(),
                ),
        )
    }
}
