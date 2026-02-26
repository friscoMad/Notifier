package com.notifier.router.api.service

import com.notifier.router.api.domain.ChannelSubscription
import com.notifier.router.api.dto.ChannelSubscriptionDto
import com.notifier.router.api.repository.ChannelSubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ChannelSubscriptionService(
    private val channelSubscriptionRepository: ChannelSubscriptionRepository,
) {
    @Transactional
    fun createChannelSubscription(dto: ChannelSubscriptionDto): ChannelSubscriptionDto =
        channelSubscriptionRepository.save(dto.toDomain()).toDto()

    @Transactional
    fun deleteChannelSubscription(id: String) {
        channelSubscriptionRepository.deleteById(UUID.fromString(id))
    }

    @Transactional(readOnly = true)
    fun getChannelSubscriptionsByChannelId(slackChannelId: String): List<ChannelSubscriptionDto> =
        channelSubscriptionRepository.findBySlackChannelId(slackChannelId).map { it.toDto() }

    private fun ChannelSubscriptionDto.toDomain() =
        ChannelSubscription(
            id = id?.let { UUID.fromString(it) } ?: UUID.randomUUID(),
            slackChannelId = slackChannelId,
            slackChannelName = slackChannelName,
            notificationTypeId = UUID.fromString(notificationTypeId),
            filters = filters,
            digestEnabled = digestEnabled,
            digestInterval = digestInterval,
            enabled = enabled,
        )

    private fun ChannelSubscription.toDto() =
        ChannelSubscriptionDto(
            id = id.toString(),
            slackChannelId = slackChannelId,
            slackChannelName = slackChannelName,
            notificationTypeId = notificationTypeId.toString(),
            filters = filters,
            digestEnabled = digestEnabled,
            digestInterval = digestInterval,
            enabled = enabled,
        )
}
