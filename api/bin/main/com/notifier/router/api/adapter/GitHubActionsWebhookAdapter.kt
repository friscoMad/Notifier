package com.notifier.router.api.adapter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.notifier.router.api.domain.Event

object GitHubActionsWebhookAdapter {
    private val mapper = jacksonObjectMapper()

    fun parse(payload: String): Event {
        val jsonNode = mapper.readTree(payload)

        return when (jsonNode["action"]?.asText()) {
            "created" -> parseDeploymentCreated(jsonNode)
            "in_progress" -> parseDeploymentInProgress(jsonNode)
            "completed" -> parseDeploymentCompleted(jsonNode)
            else -> parseGenericGitHubActionsEvent(jsonNode)
        }
    }

    private fun parseDeploymentCreated(jsonNode: JsonNode): Event {
        val deployment = jsonNode["deployment"]
        val repository = jsonNode["repository"]

        return Event(
            typeKey = "deploy_started",
            metadata =
                mapOf(
                    "service" to deployment["environment"].asText(),
                    "environment" to deployment["environment"].asText(),
                    "author" to deployment["creator"]["login"].asText(),
                    "pipeline" to deployment["ref"].asText(),
                ),
            payload =
                mapOf(
                    "deployment_url" to deployment["url"].asText(),
                    "created_at" to deployment["created_at"].asText(),
                    "description" to deployment["description"].asText(),
                ),
        )
    }

    private fun parseDeploymentInProgress(jsonNode: JsonNode): Event {
        val deployment = jsonNode["deployment"]
        val repository = jsonNode["repository"]

        return Event(
            typeKey = "deploy_started",
            metadata =
                mapOf(
                    "service" to deployment["environment"].asText(),
                    "environment" to deployment["environment"].asText(),
                    "author" to deployment["creator"]["login"].asText(),
                    "pipeline" to deployment["ref"].asText(),
                ),
            payload =
                mapOf(
                    "deployment_url" to deployment["url"].asText(),
                    "status" to "in_progress",
                    "updated_at" to deployment["updated_at"].asText(),
                ),
        )
    }

    private fun parseDeploymentCompleted(jsonNode: JsonNode): Event {
        val deployment = jsonNode["deployment"]
        val repository = jsonNode["repository"]
        val deploymentStatus = jsonNode["deployment_status"]

        return Event(
            typeKey = "deploy_completed",
            metadata =
                mapOf(
                    "service" to deployment["environment"].asText(),
                    "environment" to deployment["environment"].asText(),
                    "author" to deployment["creator"]["login"].asText(),
                    "pipeline" to deployment["ref"].asText(),
                    "status" to deploymentStatus["state"].asText(),
                ),
            payload =
                mapOf(
                    "deployment_url" to deployment["url"].asText(),
                    "status_url" to deploymentStatus["url"].asText(),
                    "status" to deploymentStatus["state"].asText(),
                    "completed_at" to deploymentStatus["created_at"].asText(),
                ),
        )
    }

    private fun parseGenericGitHubActionsEvent(jsonNode: JsonNode): Event {
        val repository = jsonNode["repository"]

        return Event(
            typeKey = jsonNode["action"]?.asText() ?: "github_actions_event",
            metadata =
                mapOf(
                    "repo" to repository["full_name"].asText(),
                    "event_type" to (jsonNode["action"]?.asText() ?: "unknown"),
                ),
            payload =
                mapOf(
                    "event_data" to jsonNode.toString(),
                ),
        )
    }
}
