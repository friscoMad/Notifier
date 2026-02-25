package com.notifier.router.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class NotificationTypeDto(
    @field:JsonProperty("id")
    val id: String? = null,
    @field:JsonProperty("typeKey")
    @field:NotBlank
    val typeKey: String,
    @field:JsonProperty("name")
    @field:NotBlank
    val name: String,
    @field:JsonProperty("description")
    val description: String? = null,
)
