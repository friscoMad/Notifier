package com.notifier.router.api.repository

import com.notifier.router.api.domain.ChannelSubscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChannelSubscriptionRepository : JpaRepository<ChannelSubscription, UUID> {
    fun findBySlackChannelId(slackChannelId: String): List<ChannelSubscription>

    fun findByNotificationTypeId(notificationTypeId: UUID): List<ChannelSubscription>

    fun findBySlackChannelIdAndEnabledTrue(slackChannelId: String): List<ChannelSubscription>
}
