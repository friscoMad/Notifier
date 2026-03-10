package com.notifier.router.api.service

import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.domain.User
import com.notifier.router.api.exception.SubscriptionNotFoundException
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import com.notifier.router.common.dto.SubscriptionDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val notificationTypeRepository: NotificationTypeRepository,
    private val userRepository: UserRepository,
    private val novuService: NovuService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createSubscription(dto: SubscriptionDto): SubscriptionDto {
        val user =
            userRepository.findBySlackId(dto.userId)
                ?: userRepository.save(
                    User(
                        id = UUID.randomUUID(),
                        slackId = dto.userId,
                    ),
                )

        val subscription = dto.toDomain(user.id)
        val saved = subscriptionRepository.save(subscription)
        syncWithNovu(saved)
        return saved.toDto(user.slackId)
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
        val saved = subscriptionRepository.save(updated)
        syncWithNovu(saved)

        val user = userRepository.findById(saved.userId).orElseThrow()
        return saved.toDto(user.slackId)
    }

    private fun syncWithNovu(subscription: Subscription) {
        val type =
            notificationTypeRepository.findById(subscription.notificationTypeId).orElse(null)
                ?: return

        try {
            novuService.syncSubscriberPreferences(
                subscriberId = subscription.userId.toString(),
                workflowKey = type.typeKey,
                channels = subscription.channels,
                channelConfig = subscription.channelConfig,
            )
        } catch (e: org.springframework.web.client.RestClientException) {
            // Novu registration is best-effort — subscriber will be created lazily on first trigger.
            // Do not roll back the subscription transaction.
            logger.warn("Could not pre-register subscriber ${subscription.userId} in Novu", e)
        }
    }

    @Transactional
    fun deleteSubscription(id: String) {
        val uuid = UUID.fromString(id)
        val subscription = subscriptionRepository.findById(uuid).orElse(null)
            ?: throw SubscriptionNotFoundException("Subscription not found: $id")

        subscriptionRepository.deleteById(uuid)

        val remaining = subscriptionRepository.findByUserId(subscription.userId)
        if (remaining.isEmpty()) {
            val user = userRepository.findById(subscription.userId).orElse(null) ?: return
            try {
                novuService.cleanupSubscriber(user.slackId)
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.warn("Could not clean up Novu data for subscriber ${user.slackId}", e)
            }
        }
    }

    @Transactional(readOnly = true)
    fun getSubscriptionsByUserId(userId: String): List<SubscriptionDto> {
        val user =
            userRepository.findBySlackId(userId)
                ?: try {
                    userRepository.findById(UUID.fromString(userId)).orElse(null)
                } catch (_: IllegalArgumentException) {
                    null
                }

        return user?.let { u ->
            subscriptionRepository.findByUserId(u.id).map { it.toDto(u.slackId) }
        }
            ?: emptyList()
    }

    private fun SubscriptionDto.toDomain(userUuid: UUID) =
        Subscription(
            id = id?.let { UUID.fromString(it) } ?: UUID.randomUUID(),
            userId = userUuid,
            notificationTypeId = UUID.fromString(notificationTypeId),
            channels = channels,
            channelConfig = channelConfig,
            filters = filters,
            enabled = enabled,
        )

    private fun Subscription.toDto(slackId: String) =
        SubscriptionDto(
            id = id.toString(),
            userId = slackId,
            notificationTypeId = notificationTypeId.toString(),
            channels = channels,
            channelConfig = channelConfig,
            filters = filters,
            enabled = enabled,
        )
}
