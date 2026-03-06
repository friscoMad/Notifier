package com.notifier.router.api.domain

import com.notifier.router.common.domain.Filter
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "subscriptions",
    uniqueConstraints =
    [
        UniqueConstraint(
            name = "uc_subscription_user_type",
            columnNames = ["user_id", "notification_type_id"],
        ),
    ],
    indexes =
    [
        Index(name = "idx_subscription_user_id", columnList = "user_id"),
        Index(
            name = "idx_subscription_type_id",
            columnList = "notification_type_id",
        ),
    ],
)
@EntityListeners(AuditingEntityListener::class)
data class Subscription(
    @Id val id: UUID,
    @Column(nullable = false) val userId: UUID,
    @Column(nullable = false) val notificationTypeId: UUID,
    @JdbcTypeCode(SqlTypes.JSON) val channels: List<String>,
    @JdbcTypeCode(SqlTypes.JSON) val channelConfig: Map<String, Any> = emptyMap(),
    @JdbcTypeCode(SqlTypes.JSON) val filters: List<Filter> = emptyList(),
    val enabled: Boolean = true,
    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
