package com.notifier.router.bot.config

import com.slack.api.bolt.App
import com.slack.api.bolt.socket_mode.SocketModeApp
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!test") // Don't start Socket Mode during tests
class SocketModeService(
    private val app: App,
    @Value("\${slack.socket-mode-enabled:false}") private val enabled: Boolean,
    @Value("\${slack.app-token:}") private val appToken: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var socketModeApp: SocketModeApp? = null

    @PostConstruct
    fun start() {
        if (enabled) {
            if (appToken.isBlank()) {
                logger.error("Slack Socket Mode is enabled but slack.app-token is missing!")
                return
            }

            logger.info(
                "Starting Slack Socket Mode app with app token (len: ${appToken.length}, starts with: ${appToken.take(
                    10
                )}...)",
            )

            logger.info("Starting Slack Socket Mode app...")
            socketModeApp = SocketModeApp(appToken, app)
            socketModeApp?.startAsync()
            logger.info("Slack Socket Mode app started successfully.")
        } else {
            logger.info("Slack Socket Mode is disabled.")
        }
    }

    @PreDestroy
    fun stop() {
        socketModeApp?.stop()
        logger.info("Slack Socket Mode app stopped.")
    }
}
