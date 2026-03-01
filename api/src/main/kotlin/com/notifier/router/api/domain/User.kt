package com.notifier.router.api.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotBlank
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "users",
    uniqueConstraints =
        [UniqueConstraint(name = "uc_user_slack_id", columnNames = ["slackId"])],
    indexes = [Index(name = "idx_user_slack_id", columnList = "slackId")],
)
@EntityListeners(AuditingEntityListener::class)
data class User(
    @Id val id: UUID,
    @field:NotBlank val slackId: String,
    val slackTeamId: String? = null,
    val email: String? = null,
    val name: String? = null,
    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
