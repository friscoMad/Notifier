package com.notifier.router.api.domain

import java.time.Instant

data class Event(
    val typeKey: String,
    val metadata: Map<String, Any> = emptyMap(),
    val payload: Map<String, Any> = emptyMap(),
    val timestamp: Instant = Instant.now(),
)
