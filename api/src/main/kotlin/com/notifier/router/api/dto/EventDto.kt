package com.notifier.router.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class EventDto(
    @field:JsonProperty("typeKey")
    val typeKey: String,
    @field:JsonProperty("metadata")
    val metadata: Map<String, Any> = emptyMap(),
    @field:JsonProperty("payload")
    val payload: Map<String, Any> = emptyMap(),
)
