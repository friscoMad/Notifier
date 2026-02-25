package com.notifier.router.api.service

import com.notifier.router.api.domain.Filter
import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.dto.FilterDto
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
    fun createSubscription(subscriptionDto: SubscriptionDto): SubscriptionDto {
        val subscription = mapToDomain(subscriptionDto)
        val savedSubscription = subscriptionRepository.save(subscription)
        return mapToDto(savedSubscription)
    }

    @Transactional
    fun updateSubscription(
        id: String,
        subscriptionDto: SubscriptionDto,
    ): SubscriptionDto {
        val existingSubscription =
            subscriptionRepository.findById(UUID.fromString(id)).orElse(null)
                ?: throw SubscriptionNotFoundException("Subscription not found: $id")

        val updatedSubscription =
            existingSubscription.copy(
                channels = subscriptionDto.channels,
                channelConfig = subscriptionDto.channelConfig,
                filters = subscriptionDto.filters.map { Filter(it.field, it.operator, it.value) },
                enabled = subscriptionDto.enabled,
            )

        val savedSubscription = subscriptionRepository.save(updatedSubscription)
        return mapToDto(savedSubscription)
    }

    @Transactional
    fun deleteSubscription(id: String) {
        subscriptionRepository.deleteById(UUID.fromString(id))
    }

    @Transactional(readOnly = true)
    fun getSubscriptionsByUserId(userId: String): List<SubscriptionDto> {
        val subscriptions = subscriptionRepository.findByUserId(UUID.fromString(userId))
        return subscriptions.map { mapToDto(it) }
    }

    private fun mapToDomain(subscriptionDto: SubscriptionDto): Subscription =
        Subscription(
            id = subscriptionDto.id?.let { UUID.fromString(it) } ?: UUID.randomUUID(),
            userId = UUID.fromString(subscriptionDto.userId),
            notificationTypeId = UUID.fromString(subscriptionDto.notificationTypeId),
            channels = subscriptionDto.channels,
            channelConfig = subscriptionDto.channelConfig,
            filters = subscriptionDto.filters.map { Filter(it.field, it.operator, it.value) },
            enabled = subscriptionDto.enabled,
        )

    private fun mapToDto(subscription: Subscription): SubscriptionDto =
        SubscriptionDto(
            id = subscription.id.toString(),
            userId = subscription.userId.toString(),
            notificationTypeId = subscription.notificationTypeId.toString(),
            channels = subscription.channels,
            channelConfig = subscription.channelConfig,
            filters = subscription.filters.map { FilterDto(it.field, it.operator, it.value) },
            enabled = subscription.enabled,
        )
}
