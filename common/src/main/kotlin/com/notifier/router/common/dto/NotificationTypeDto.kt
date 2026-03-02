package com.notifier.router.common.dto

import jakarta.validation.constraints.NotBlank

data class NotificationTypeDto(
    val id: String? = null,
    @field:NotBlank val key: String,
    @field:NotBlank val name: String,
    val description: String? = null,
)
