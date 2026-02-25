package com.notifier.router.api.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "channel_subscriptions")
data class ChannelSubscription(
        @Id val id: UUID,
        val slackChannelId: String,
        val slackChannelName: String,
        val notificationTypeId: UUID,
        @JdbcTypeCode(SqlTypes.JSON) val filters: List<Filter> = emptyList(),
        val digestEnabled: Boolean = false,
        val digestInterval: String = "24h",
        val enabled: Boolean = true,
        val createdAt: LocalDateTime = LocalDateTime.now(),
)
