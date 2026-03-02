package com.notifier.router.common.dto

data class EventDto(
    val id: String? = null,
    val typeKey: String,
    val payload: Map<String, Any> = emptyMap(),
    val metadata: Map<String, Any> = emptyMap(),
)
