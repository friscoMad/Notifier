package com.notifier.router.api.service

import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.dto.SubscriptionDto
import com.notifier.router.api.exception.SubscriptionNotFoundException
import com.notifier.router.api.repository.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
) {
    @Transactional
    fun createSubscription(dto: SubscriptionDto): SubscriptionDto {
        val subscription = dto.toDomain()
        return subscriptionRepository.save(subscription).toDto()
    }

    @Transactional
    fun updateSubscription(
        id: String,
        dto: SubscriptionDto,
    ): SubscriptionDto {
        val existing =
            subscriptionRepository.findById(UUID.fromString(id)).orElse(null)
                ?: throw SubscriptionNotFoundException("Subscription not found: $id")

        val updated =
            existing.copy(
                channels = dto.channels,
                channelConfig = dto.channelConfig,
                filters = dto.filters,
                enabled = dto.enabled,
            )
        return subscriptionRepository.save(updated).toDto()
    }

    @Transactional
    fun deleteSubscription(id: String) {
        subscriptionRepository.deleteById(UUID.fromString(id))
    }

    @Transactional(readOnly = true)
    fun getSubscriptionsByUserId(userId: String): List<SubscriptionDto> =
        subscriptionRepository.findByUserId(UUID.fromString(userId)).map { it.toDto() }

    private fun SubscriptionDto.toDomain() =
        Subscription(
            id = id?.let { UUID.fromString(it) } ?: UUID.randomUUID(),
            userId = UUID.fromString(userId),
            notificationTypeId = UUID.fromString(notificationTypeId),
            channels = channels,
            channelConfig = channelConfig,
            filters = filters,
            enabled = enabled,
        )

    private fun Subscription.toDto() =
        SubscriptionDto(
            id = id.toString(),
            userId = userId.toString(),
            notificationTypeId = notificationTypeId.toString(),
            channels = channels,
            channelConfig = channelConfig,
            filters = filters,
            enabled = enabled,
        )
}
