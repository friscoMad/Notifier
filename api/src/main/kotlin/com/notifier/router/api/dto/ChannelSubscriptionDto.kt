package com.notifier.router.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class ChannelSubscriptionDto(
    @field:JsonProperty("id")
    val id: String? = null,
    @field:JsonProperty("slackChannelId")
    @field:NotBlank
    val slackChannelId: String,
    @field:JsonProperty("slackChannelName")
    @field:NotBlank
    val slackChannelName: String,
    @field:JsonProperty("notificationTypeId")
    @field:NotBlank
    val notificationTypeId: String,
    @field:JsonProperty("filters")
    val filters: List<FilterDto> = emptyList(),
    @field:JsonProperty("digestEnabled")
    val digestEnabled: Boolean = false,
    @field:JsonProperty("digestInterval")
    val digestInterval: String = "24h",
    @field:JsonProperty("enabled")
    val enabled: Boolean = true,
)
