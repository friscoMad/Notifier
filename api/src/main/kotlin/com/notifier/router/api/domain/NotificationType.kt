package com.notifier.router.api.domain

import java.time.LocalDateTime
import java.util.UUID

data class NotificationType(
    val id: UUID,
    val typeKey: String,
    val name: String,
    val description: String? = null,
    val defaultChannels: List<String> = listOf("inbox"),
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
