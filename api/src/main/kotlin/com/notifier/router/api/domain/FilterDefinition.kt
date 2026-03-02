package com.notifier.router.api.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "filter_definitions",
    indexes = [Index(name = "idx_filter_def_type_id", columnList = "notification_type_id")],
)
@EntityListeners(AuditingEntityListener::class)
data class FilterDefinition(
    @Id val id: UUID,
    @Column(nullable = false) val notificationTypeId: UUID,
    @Column(nullable = false) val field: String,
    @Column(nullable = false) val fieldType: String,
    @JdbcTypeCode(SqlTypes.JSON) val operators: List<String>,
    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
