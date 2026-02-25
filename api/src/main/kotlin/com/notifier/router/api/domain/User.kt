package com.notifier.router.api.domain

import java.time.LocalDateTime
import java.util.UUID

data class User(
    val id: UUID,
    val slackId: String,
    val slackTeamId: String? = null,
    val email: String? = null,
    val name: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
