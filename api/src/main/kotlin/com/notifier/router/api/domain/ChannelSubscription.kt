package com.notifier.router.api.domain

import com.notifier.router.common.domain.Filter
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "channel_subscriptions",
    uniqueConstraints =
    [
        UniqueConstraint(
            name = "uc_channel_sub_channel_type",
            columnNames = ["slack_channel_id", "notification_type_id"],
        ),
    ],
    indexes =
    [
        Index(name = "idx_channel_sub_channel_id", columnList = "slack_channel_id"),
        Index(
            name = "idx_channel_sub_type_id",
            columnList = "notification_type_id",
        ),
    ],
)
@EntityListeners(AuditingEntityListener::class)
data class ChannelSubscription(
    @Id val id: UUID,
    @field:NotBlank val slackChannelId: String,
    @field:NotBlank val slackChannelName: String,
    @Column(nullable = false) val notificationTypeId: UUID,
    @JdbcTypeCode(SqlTypes.JSON) val filters: List<Filter> = emptyList(),
    val digestEnabled: Boolean = false,
    val digestInterval: String = "24h",
    val enabled: Boolean = true,
    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
