package com.notifier.router.api.repository

import com.notifier.router.api.domain.FilterDefinition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FilterDefinitionRepository : JpaRepository<FilterDefinition, UUID> {
    fun findByNotificationTypeId(notificationTypeId: UUID): List<FilterDefinition>
}
