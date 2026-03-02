package com.notifier.router.common.dto

import com.notifier.router.common.domain.Filter
import jakarta.validation.constraints.NotBlank

data class ChannelSubscriptionDto(
    val id: String? = null,
    @field:NotBlank val slackChannelId: String,
    @field:NotBlank val slackChannelName: String,
    @field:NotBlank val notificationTypeId: String,
    val filters: List<Filter> = emptyList(),
    val digestEnabled: Boolean = false,
    val digestInterval: String = "24h",
    val enabled: Boolean = true,
)
