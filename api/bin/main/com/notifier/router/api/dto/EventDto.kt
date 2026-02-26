package com.notifier.router.api.dto

data class EventDto(
    val typeKey: String,
    val metadata: Map<String, Any> = emptyMap(),
    val payload: Map<String, Any> = emptyMap(),
)
