package com.notifier.router.api.dto

import com.notifier.router.api.domain.Filter
import jakarta.validation.constraints.NotBlank

data class SubscriptionDto(
    val id: String? = null,
    @field:NotBlank val userId: String,
    @field:NotBlank val notificationTypeId: String,
    val channels: List<String>,
    val channelConfig: Map<String, Any> = emptyMap(),
    val filters: List<Filter> = emptyList(),
    val enabled: Boolean = true,
)
