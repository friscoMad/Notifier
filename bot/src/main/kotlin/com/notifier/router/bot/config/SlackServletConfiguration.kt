package com.notifier.router.bot.controller

import com.slack.api.bolt.App
import com.slack.api.bolt.jakarta_servlet.SlackAppServlet
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SlackServletConfiguration {
    @Bean
    fun slackAppServletBean(app: App): ServletRegistrationBean<SlackAppServlet> =
        ServletRegistrationBean(SlackAppServlet(app), "/slack/events")
}
