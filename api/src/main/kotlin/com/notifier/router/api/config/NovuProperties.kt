package com.notifier.router.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "novu.api")
data class NovuApiProperties(
    val key: String = "not-set",
    val url: String = "",
)

@ConfigurationProperties(prefix = "novu.slack")
data class NovuSlackProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val applicationId: String = "",
    val botToken: String = "",
    val workspaceId: String = "",
    val workspaceName: String = "Slack Workspace",
)

@ConfigurationProperties(prefix = "novu.ses")
data class NovuSesProperties(
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val region: String = "us-east-1",
    val from: String = "",
    val senderName: String = "Notifier",
)

@ConfigurationProperties(prefix = "novu.resend")
data class NovuResendProperties(
    val apiKey: String = "",
    val from: String = "",
    val senderName: String = "Notifier",
)
