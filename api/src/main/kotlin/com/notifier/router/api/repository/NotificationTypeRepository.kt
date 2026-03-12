package com.notifier.router.api.repository

import com.notifier.router.api.domain.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NotificationTypeRepository : JpaRepository<NotificationType, UUID> {
    fun findByTypeKey(typeKey: String): NotificationType?
    fun findAllByOrderByNameAsc(): List<NotificationType>
}
