package com.notifier.router.api.adapter

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

/** Shared ObjectMapper configured for snake_case JSON payloads. */
val webhookMapper: JsonMapper =
    jacksonMapperBuilder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()

/** Shared data classes for common webhook payload structures. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubUser(
    val login: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRepository(
    val fullName: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRef(
    val ref: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubPullRequest(
    val user: GitHubUser = GitHubUser(),
    val base: GitHubRef = GitHubRef(),
    val title: String = "",
    val body: String = "",
    val htmlUrl: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val mergedAt: String? = null,
    val mergedBy: GitHubUser? = null,
    val merged: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCheckSuite(
    val app: GitHubApp = GitHubApp(),
    val htmlUrl: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubApp(
    val name: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCheckRun(
    val name: String = "",
    val conclusion: String? = null,
    val htmlUrl: String = "",
    val completedAt: String? = null,
)

/** Top-level GitHub webhook payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubWebhookPayload(
    val action: String? = null,
    val pullRequest: GitHubPullRequest? = null,
    val repository: GitHubRepository = GitHubRepository(),
    val requestedReviewer: GitHubUser? = null,
    val requestedAt: String? = null,
    val sender: GitHubUser = GitHubUser(),
    val checkSuite: GitHubCheckSuite? = null,
    val checkRun: GitHubCheckRun? = null,
)

/** GitHub Actions deployment payload data classes. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubDeployment(
    val environment: String = "",
    val creator: GitHubUser = GitHubUser(),
    val ref: String = "",
    val url: String = "",
    val createdAt: String = "",
    val description: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubDeploymentStatus(
    val state: String = "",
    val url: String = "",
    val createdAt: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubActionsPayload(
    val action: String? = null,
    val deployment: GitHubDeployment? = null,
    val deploymentStatus: GitHubDeploymentStatus? = null,
    val repository: GitHubRepository = GitHubRepository(),
)

/** Buildkite webhook payload data classes. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BuildkiteJob(
    val name: String = "",
    val state: String = "",
    val webUrl: String = "",
    val finishedAt: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BuildkiteBuild(
    val state: String = "",
    val message: String = "",
    val webUrl: String = "",
    val finishedAt: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BuildkitePipeline(
    val slug: String = "",
    val team: String? = null,
    val webUrl: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BuildkitePayload(
    val event: String? = null,
    val job: BuildkiteJob? = null,
    val build: BuildkiteBuild? = null,
    val pipeline: BuildkitePipeline = BuildkitePipeline(),
    val updatedAt: String? = null,
)
