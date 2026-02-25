package com.notifier.router.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class SubscriptionDto(
    @field:JsonProperty("id")
    val id: String? = null,
    @field:JsonProperty("userId")
    @field:NotBlank
    val userId: String,
    @field:JsonProperty("notificationTypeId")
    @field:NotBlank
    val notificationTypeId: String,
    @field:JsonProperty("channels")
    val channels: List<String>,
    @field:JsonProperty("channelConfig")
    val channelConfig: Map<String, Any> = emptyMap(),
    @field:JsonProperty("filters")
    val filters: List<FilterDto> = emptyList(),
    @field:JsonProperty("enabled")
    val enabled: Boolean = true,
)
