package com.notifier.router.api.service

import com.notifier.router.api.config.NovuSlackProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

/**
 * Sends Slack messages directly via the Slack Web API (`chat.postMessage`), bypassing Novu.
 *
 * Novu's chat step delivers mrkdwn content as plain text, which causes `<url|text>` links to
 * appear as literal angle-bracket strings. By calling the Slack API directly with Block Kit
 * sections (`type: mrkdwn`), we guarantee that links and formatting render correctly.
 */
@Service
class SlackNotificationService(
    private val slackProps: NovuSlackProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    fun sendMessage(channel: String, content: String) {
        if (slackProps.botToken.isBlank()) {
            logger.warn("Slack bot token not configured — skipping direct Slack delivery for $channel")
            return
        }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(slackProps.botToken)
        }
        val body = mapOf(
            "channel" to channel,
            "blocks" to listOf(
                mapOf("type" to "section", "text" to mapOf("type" to "mrkdwn", "text" to content)),
            ),
        )

        try {
            @Suppress("UNCHECKED_CAST")
            val response = restTemplate.postForObject(
                "https://slack.com/api/chat.postMessage",
                HttpEntity(body, headers),
                Map::class.java,
            ) as Map<String, Any?>?

            if (response?.get("ok") != true) {
                logger.error("Slack API error for channel $channel: ${response?.get("error")}")
            } else {
                logger.debug("Delivered Slack message to $channel")
            }
        } catch (e: RestClientException) {
            logger.error("Exception while sending Slack message to $channel", e)
        }
    }
}
