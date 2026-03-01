package com.notifier.router.api.domain

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "notification_types",
    uniqueConstraints =
        [UniqueConstraint(name = "uc_notification_type_key", columnNames = ["typeKey"])],
    indexes = [Index(name = "idx_notification_type_key", columnList = "typeKey")],
)
@EntityListeners(AuditingEntityListener::class)
data class NotificationType(
    @Id val id: UUID,
    @field:NotBlank val typeKey: String,
    @field:NotBlank val name: String,
    val description: String? = null,
    @JdbcTypeCode(SqlTypes.JSON) val defaultChannels: List<String> = listOf("inbox"),
    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
