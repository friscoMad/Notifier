package com.notifier.router.api.repository

import com.notifier.router.api.domain.Subscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SubscriptionRepository : JpaRepository<Subscription, UUID> {
    fun findByUserId(userId: UUID): List<Subscription>

    fun findByUserIdAndNotificationTypeId(
        userId: UUID,
        notificationTypeId: UUID,
    ): Subscription?

    fun findByUserIdAndEnabledTrue(userId: UUID): List<Subscription>
}
