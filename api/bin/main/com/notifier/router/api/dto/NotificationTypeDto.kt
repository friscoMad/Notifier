package com.notifier.router.api.dto

import jakarta.validation.constraints.NotBlank

data class NotificationTypeDto(
    val id: String? = null,
    @field:NotBlank val typeKey: String,
    @field:NotBlank val name: String,
    val description: String? = null,
)
