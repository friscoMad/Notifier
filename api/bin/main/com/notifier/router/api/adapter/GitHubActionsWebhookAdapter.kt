package com.notifier.router.api.adapter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.notifier.router.api.domain.Event

object GitHubActionsWebhookAdapter {
    private val mapper = jacksonObjectMapper()

    fun parse(payload: String): Event {
        val json = mapper.readTree(payload)
        return when (json["action"]?.asText()) {
            "created", "in_progress" -> parseDeploymentStarted(json)
            "completed" -> parseDeploymentCompleted(json)
            else -> parseGenericEvent(json)
        }
    }

    private fun parseDeploymentStarted(json: JsonNode): Event {
        val deployment = json["deployment"]
        return Event(
            typeKey = "deploy_started",
            metadata = deployment.commonMetadata(),
            payload =
                mapOf(
                    "deployment_url" to deployment["url"].asText(),
                    "status" to (json["action"]?.asText() ?: "created"),
                    "created_at" to deployment["created_at"].asText(),
                ),
        )
    }

    private fun parseDeploymentCompleted(json: JsonNode): Event {
        val deployment = json["deployment"]
        val status = json["deployment_status"]
        return Event(
            typeKey = "deploy_completed",
            metadata =
                deployment.commonMetadata() +
                    mapOf(
                        "status" to status["state"].asText(),
                    ),
            payload =
                mapOf(
                    "deployment_url" to deployment["url"].asText(),
                    "status_url" to status["url"].asText(),
                    "status" to status["state"].asText(),
                    "completed_at" to status["created_at"].asText(),
                ),
        )
    }

    private fun parseGenericEvent(json: JsonNode) =
        Event(
            typeKey = json["action"]?.asText() ?: "github_actions_event",
            metadata =
                mapOf(
                    "repo" to json["repository"]["full_name"].asText(),
                    "event_type" to (json["action"]?.asText() ?: "unknown"),
                ),
            payload = mapOf("event_data" to json.toString()),
        )

    private fun JsonNode.commonMetadata() =
        mapOf(
            "service" to this["environment"].asText(),
            "environment" to this["environment"].asText(),
            "author" to this["creator"]["login"].asText(),
            "pipeline" to this["ref"].asText(),
        )
}
