package com.notifier.router.api.service

import com.notifier.router.api.domain.ChannelSubscription
import com.notifier.router.api.domain.Filter
import com.notifier.router.api.dto.ChannelSubscriptionDto
import com.notifier.router.api.dto.FilterDto
import com.notifier.router.api.repository.ChannelSubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ChannelSubscriptionService(
    private val channelSubscriptionRepository: ChannelSubscriptionRepository,
) {
    @Transactional
    fun createChannelSubscription(channelSubscriptionDto: ChannelSubscriptionDto): ChannelSubscriptionDto {
        val channelSubscription = mapToDomain(channelSubscriptionDto)
        val savedSubscription = channelSubscriptionRepository.save(channelSubscription)
        return mapToDto(savedSubscription)
    }

    @Transactional
    fun deleteChannelSubscription(id: String) {
        channelSubscriptionRepository.deleteById(UUID.fromString(id))
    }

    @Transactional(readOnly = true)
    fun getChannelSubscriptionsByChannelId(slackChannelId: String): List<ChannelSubscriptionDto> {
        val subscriptions = channelSubscriptionRepository.findBySlackChannelId(slackChannelId)
        return subscriptions.map { mapToDto(it) }
    }

    private fun mapToDomain(channelSubscriptionDto: ChannelSubscriptionDto): ChannelSubscription =
        ChannelSubscription(
            id = channelSubscriptionDto.id?.let { UUID.fromString(it) } ?: UUID.randomUUID(),
            slackChannelId = channelSubscriptionDto.slackChannelId,
            slackChannelName = channelSubscriptionDto.slackChannelName,
            notificationTypeId = UUID.fromString(channelSubscriptionDto.notificationTypeId),
            filters = channelSubscriptionDto.filters.map { Filter(it.field, it.operator, it.value) },
            digestEnabled = channelSubscriptionDto.digestEnabled,
            digestInterval = channelSubscriptionDto.digestInterval,
            enabled = channelSubscriptionDto.enabled,
        )

    private fun mapToDto(channelSubscription: ChannelSubscription): ChannelSubscriptionDto =
        ChannelSubscriptionDto(
            id = channelSubscription.id.toString(),
            slackChannelId = channelSubscription.slackChannelId,
            slackChannelName = channelSubscription.slackChannelName,
            notificationTypeId = channelSubscription.notificationTypeId.toString(),
            filters = channelSubscription.filters.map { FilterDto(it.field, it.operator, it.value) },
            digestEnabled = channelSubscription.digestEnabled,
            digestInterval = channelSubscription.digestInterval,
            enabled = channelSubscription.enabled,
        )
}
