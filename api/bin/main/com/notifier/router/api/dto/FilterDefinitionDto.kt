package com.notifier.router.api.dto

import jakarta.validation.constraints.NotBlank

data class FilterDefinitionDto(
    val id: String? = null,
    @field:NotBlank val notificationTypeId: String,
    @field:NotBlank val field: String,
    @field:NotBlank val fieldType: String,
    val operators: List<String>,
)
