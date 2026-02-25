package com.notifier.router.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class FilterDefinitionDto(
    @field:JsonProperty("id")
    val id: String? = null,
    @field:JsonProperty("notificationTypeId")
    @field:NotBlank
    val notificationTypeId: String,
    @field:JsonProperty("field")
    @field:NotBlank
    val field: String,
    @field:JsonProperty("fieldType")
    @field:NotBlank
    val fieldType: String,
    @field:JsonProperty("operators")
    val operators: List<String>,
)
