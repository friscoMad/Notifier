package com.notifier.router.api.domain

import java.time.LocalDateTime
import java.util.UUID

data class ChannelSubscription(
    val id: UUID,
    val slackChannelId: String,
    val slackChannelName: String,
    val notificationTypeId: UUID,
    val filters: List<Filter> = emptyList(),
    val digestEnabled: Boolean = false,
    val digestInterval: String = "24h",
    val enabled: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
