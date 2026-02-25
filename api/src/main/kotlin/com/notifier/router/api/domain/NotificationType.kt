package com.notifier.router.api.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "notification_types")
data class NotificationType(
        @Id val id: UUID,
        val typeKey: String,
        val name: String,
        val description: String? = null,
        @JdbcTypeCode(SqlTypes.JSON) val defaultChannels: List<String> = listOf("inbox"),
        val createdAt: LocalDateTime = LocalDateTime.now(),
)
