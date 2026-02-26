package com.notifier.router.api.adapter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.notifier.router.api.domain.Event

object GitHubWebhookAdapter {
    private val mapper = jacksonObjectMapper()

    fun parse(payload: String): Event {
        val jsonNode = mapper.readTree(payload)

        return when (jsonNode["action"]?.asText()) {
            "opened" -> parsePRCreated(jsonNode)
            "review_requested" -> parsePRReviewRequested(jsonNode)
            "synchronize" -> parsePRUpdated(jsonNode)
            "closed" -> parsePRClosed(jsonNode)
            "rerequested" -> parsePRCheckRerequested(jsonNode)
            "completed" -> parsePRCheckCompleted(jsonNode)
            else -> parseGenericGitHubEvent(jsonNode)
        }
    }

    private fun parsePRCreated(jsonNode: JsonNode): Event {
        val pr = jsonNode["pull_request"]
        val repository = jsonNode["repository"]

        return Event(
            typeKey = "pr_created",
            metadata =
                mapOf(
                    "author" to pr["user"]["login"].asText(),
                    "repo" to repository["full_name"].asText(),
                    "base_branch" to pr["base"]["ref"].asText(),
                ),
            payload =
                mapOf(
                    "title" to pr["title"].asText(),
                    "description" to pr["body"].asText(),
                    "url" to pr["html_url"].asText(),
                    "created_at" to pr["created_at"].asText(),
                ),
        )
    }

    private fun parsePRReviewRequested(jsonNode: JsonNode): Event {
        val pr = jsonNode["pull_request"]
        val repository = jsonNode["repository"]
        val requestedReviewer = jsonNode["requested_reviewer"]

        return Event(
            typeKey = "pr_review_requested",
            metadata =
                mapOf(
                    "author" to pr["user"]["login"].asText(),
                    "repo" to repository["full_name"].asText(),
                    "reviewer" to requestedReviewer["login"].asText(),
                    "base_branch" to pr["base"]["ref"].asText(),
                ),
            payload =
                mapOf(
                    "title" to pr["title"].asText(),
                    "url" to pr["html_url"].asText(),
                    "requested_at" to jsonNode["requested_at"].asText(),
                ),
        )
    }

    private fun parsePRUpdated(jsonNode: JsonNode): Event {
        val pr = jsonNode["pull_request"]
        val repository = jsonNode["repository"]

        return Event(
            typeKey = "pr_updated",
            metadata =
                mapOf(
                    "author" to pr["user"]["login"].asText(),
                    "repo" to repository["full_name"].asText(),
                    "base_branch" to pr["base"]["ref"].asText(),
                ),
            payload =
                mapOf(
                    "title" to pr["title"].asText(),
                    "url" to pr["html_url"].asText(),
                    "updated_at" to pr["updated_at"].asText(),
                ),
        )
    }

    private fun parsePRClosed(jsonNode: JsonNode): Event {
        val pr = jsonNode["pull_request"]
        val repository = jsonNode["repository"]

        val typeKey = if (pr["merged"].asBoolean()) "pr_merged_master_success" else "pr_merged_master_error"

        return Event(
            typeKey = typeKey,
            metadata =
                mapOf(
                    "author" to pr["user"]["login"].asText(),
                    "repo" to repository["full_name"].asText(),
                    "base_branch" to pr["base"]["ref"].asText(),
                ),
            payload =
                mapOf(
                    "title" to pr["title"].asText(),
                    "url" to pr["html_url"].asText(),
                    "merged_at" to pr["merged_at"].asText(),
                    "merged_by" to pr["merged_by"]["login"].asText(),
                ),
        )
    }

    private fun parsePRCheckRerequested(jsonNode: JsonNode): Event {
        val repository = jsonNode["repository"]
        val checkSuite = jsonNode["check_suite"]

        return Event(
            typeKey = "pr_checks_failed",
            metadata =
                mapOf(
                    "author" to jsonNode["sender"]["login"].asText(),
                    "repo" to repository["full_name"].asText(),
                    "check_name" to checkSuite["app"]["name"].asText(),
                    "base_branch" to jsonNode["pull_request"]["base"]["ref"].asText(),
                ),
            payload =
                mapOf(
                    "check_suite_url" to checkSuite["html_url"].asText(),
                    "rerequested_at" to jsonNode["requested_at"].asText(),
                ),
        )
    }

    private fun parsePRCheckCompleted(jsonNode: JsonNode): Event {
        val repository = jsonNode["repository"]
        val checkRun = jsonNode["check_run"]

        val typeKey = if (checkRun["conclusion"].asText() == "success") "pr_checks_passed" else "pr_checks_failed"

        return Event(
            typeKey = typeKey,
            metadata =
                mapOf(
                    "author" to jsonNode["sender"]["login"].asText(),
                    "repo" to repository["full_name"].asText(),
                    "check_name" to checkRun["name"].asText(),
                    "base_branch" to jsonNode["pull_request"]["base"]["ref"].asText(),
                ),
            payload =
                mapOf(
                    "check_run_url" to checkRun["html_url"].asText(),
                    "conclusion" to checkRun["conclusion"].asText(),
                    "completed_at" to checkRun["completed_at"].asText(),
                ),
        )
    }

    private fun parseGenericGitHubEvent(jsonNode: JsonNode): Event {
        val repository = jsonNode["repository"]

        return Event(
            typeKey = jsonNode["action"]?.asText() ?: "github_event",
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
