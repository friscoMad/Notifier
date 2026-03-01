package com.notifier.router.bot.config

import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
class SlackBotConfiguration {
    @Bean
    fun loadSlackApp(env: Environment): App {
        val botToken = env.getProperty("slack.bot-token")?.trim() ?: "xoxb-dummy-token"
        val signingSecret = env.getProperty("slack.signing-secret")?.trim() ?: "dummy-secret"

        val appConfig =
            AppConfig
                .builder()
                .singleTeamBotToken(botToken)
                .signingSecret(signingSecret)
                .build()

        return App(appConfig)
    }
}
