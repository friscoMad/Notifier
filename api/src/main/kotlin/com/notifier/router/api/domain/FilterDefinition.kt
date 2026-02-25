package com.notifier.router.api.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "filter_definitions")
data class FilterDefinition(
        @Id val id: UUID,
        val notificationTypeId: UUID,
        val field: String,
        val fieldType: String,
        @JdbcTypeCode(SqlTypes.JSON) val operators: List<String>,
        val createdAt: LocalDateTime = LocalDateTime.now(),
)
