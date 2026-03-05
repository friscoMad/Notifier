package com.notifier.router.bot.service

import com.slack.api.bolt.App
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SlackDeliveryService(
    private val app: App,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun sendNotification(
        slackTargetId: String,
        content: String,
    ) {
        logger.info("Delivering notification to Slack target: $slackTargetId")
        try {
            val response =
                app.client().chatPostMessage { req -> req.channel(slackTargetId).text(content) }
            if (response.isOk) {
                logger.debug("Successfully sent message to $slackTargetId. TS: ${response.ts}")
            } else {
                logger.error("Failed to send message to $slackTargetId. Error: ${response.error}")
            }
        } catch (e: Exception) {
            logger.error("Exception while sending message to $slackTargetId", e)
        }
    }
}
