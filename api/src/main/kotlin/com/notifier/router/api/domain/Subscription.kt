package com.notifier.router.api.domain

import java.time.LocalDateTime
import java.util.UUID

data class Subscription(
    val id: UUID,
    val userId: UUID,
    val notificationTypeId: UUID,
    val channels: List<String>,
    val channelConfig: Map<String, Any> = emptyMap(),
    val filters: List<Filter> = emptyList(),
    val enabled: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
