package com.notifier.router.api.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "subscriptions")
data class Subscription(
    @Id val id: UUID,
    val userId: UUID,
    val notificationTypeId: UUID,
    @JdbcTypeCode(SqlTypes.JSON) val channels: List<String>,
    @JdbcTypeCode(SqlTypes.JSON) val channelConfig: Map<String, Any> = emptyMap(),
    @JdbcTypeCode(SqlTypes.JSON) val filters: List<Filter> = emptyList(),
    val enabled: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
