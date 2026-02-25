package com.notifier.router.api.domain

import java.time.LocalDateTime
import java.util.UUID

data class FilterDefinition(
    val id: UUID,
    val notificationTypeId: UUID,
    val field: String,
    val fieldType: String,
    val operators: List<String>,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
