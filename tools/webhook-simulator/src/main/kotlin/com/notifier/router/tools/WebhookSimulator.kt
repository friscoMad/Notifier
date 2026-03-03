package com.notifier.router.tools

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.shell.jline.PromptProvider
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import org.springframework.web.client.RestTemplate

@SpringBootApplication
open class WebhookSimulatorApp {
    @Bean
    open fun customPromptProvider(): PromptProvider = PromptProvider {
        AttributedString(
                "notifier-simulator:>",
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)
        )
    }
}

@ShellComponent
open class WebhookSimulator {

    private val logger = LoggerFactory.getLogger(WebhookSimulator::class.java)
    private val restTemplate: RestTemplate = RestTemplate()
    private val mapper = jacksonObjectMapper()

    @Value("\${notifier.api.webhook-base-url:http://localhost:8082/api/v1/webhooks}")
    private lateinit var apiBaseUrl: String

    @ShellMethod(value = "Simulate a GitHub Pull Request Created event", key = ["gh-pr", "gh"])
    fun sendGitHubPr(
            @ShellOption(defaultValue = "owner/repo") repo: String,
            @ShellOption(defaultValue = "octocat") author: String,
            @ShellOption(defaultValue = "Initial commit") title: String
    ): String {
        val payload =
                mapOf(
                        "action" to "opened",
                        "pull_request" to
                                mapOf(
                                        "title" to title,
                                        "user" to mapOf("login" to author),
                                        "base" to mapOf("ref" to "main")
                                ),
                        "repository" to mapOf("full_name" to repo)
                )

        return sendRequest("$apiBaseUrl/github", payload, "sha256=dummy-secret")
    }

    @ShellMethod(value = "Simulate a Buildkite Deployment Started event", key = ["bk-deploy", "bk"])
    fun sendBuildkiteDeploy(
            @ShellOption(defaultValue = "api") service: String,
            @ShellOption(defaultValue = "production") env: String
    ): String {
        val payload =
                mapOf(
                        "event" to "build.running",
                        "pipeline" to mapOf("name" to service),
                        "build" to mapOf("id" to "01234567-89ab-cdef"),
                        "metadata" to mapOf("environment" to env)
                )

        return sendRequest("$apiBaseUrl/buildkite", payload)
    }

    @ShellMethod(
            value = "Send a custom JSON payload to a specific endpoint",
            key = ["custom", "send"]
    )
    fun sendCustom(
            @ShellOption(help = "Endpoint relative to base URL (e.g., github, buildkite)")
            endpoint: String,
            @ShellOption(help = "Raw JSON payload") json: String
    ): String {
        return try {
            val payload = mapper.readValue(json, Map::class.java)
            sendRequest("$apiBaseUrl/$endpoint", payload)
        } catch (e: Exception) {
            "\u001B[31mInvalid JSON: ${e.message}\u001B[0m"
        }
    }

    private fun sendRequest(url: String, payload: Any, githubSecret: String? = null): String {
        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
        val sb = StringBuilder()
        sb.append("\n\u001B[36mSending to $url:\u001B[0m\n")
        sb.append(json).append("\n")

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        if (githubSecret != null) {
            headers.set("X-Hub-Signature-256", "sha256=dummy-signature")
        }

        val entity = HttpEntity(payload, headers)

        return try {
            val response = restTemplate.postForEntity(url, entity, String::class.java)
            val statusColor =
                    if (response.statusCode.is2xxSuccessful) "\u001B[32m" else "\u001B[31m"
            sb.append("${statusColor}Response: ${response.statusCode}\u001B[0m\n")
            sb.append(response.body ?: "No body").toString()
        } catch (e: Exception) {
            sb.append("\u001B[31mError: ${e.message}\u001B[0m").toString()
        }
    }
}

fun main(args: Array<String>) {
    SpringApplicationBuilder(WebhookSimulatorApp::class.java)
            .web(WebApplicationType.NONE)
            .properties("spring.shell.interactive.enabled=true")
            .run(*args)
}
