package com.notifier.router.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class FilterDto(
    @field:JsonProperty("field")
    @field:NotBlank
    val field: String,
    @field:JsonProperty("operator")
    @field:NotBlank
    val operator: String,
    @field:JsonProperty("value")
    val value: Any,
)
