package com.notifier.router.api.service

import com.notifier.router.api.domain.Event
import com.notifier.router.api.repository.ChannelSubscriptionRepository
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class EventService(
    private val notificationTypeRepository: NotificationTypeRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val channelSubscriptionRepository: ChannelSubscriptionRepository,
    private val userRepository: UserRepository,
    private val filterEvaluator: FilterEvaluator,
    private val novuService: NovuService,
) {
    private val logger = LoggerFactory.getLogger(EventService::class.java)

    @Async
    fun processEventAsync(event: Event) {
        logger.info("Processing event asynchronously: ${event.typeKey}")
        try {
            val notificationType = notificationTypeRepository.findByTypeKey(event.typeKey)
            if (notificationType == null) {
                logger.warn("Notification type ${event.typeKey} not found. Skipping.")
                return
            }

            // Find matching user subscriptions
            val subscriberIds =
                subscriptionRepository
                    .findByNotificationTypeId(notificationType.id)
                    .filter { it.enabled && filterEvaluator.evaluate(event, it.filters) }
                    .map { it.userId }
                    .distinct()
                    .let { userRepository.findAllById(it) }
                    .map { it.slackId }

            if (subscriberIds.isNotEmpty()) {
                logger.info(
                    "Triggering Novu for ${subscriberIds.size} users for event ${event.typeKey}",
                )
                novuService.triggerWorkflow(event.typeKey, subscriberIds, event.payload)
            } else {
                logger.info("No matching individual subscriptions for event ${event.typeKey}")
            }

            // Find matching channel subscriptions
            val channelIds =
                channelSubscriptionRepository
                    .findByNotificationTypeId(notificationType.id)
                    .filter { filterEvaluator.evaluate(event, it.filters) }
                    .map { it.slackChannelId }
                    .distinct()

            if (channelIds.isNotEmpty()) {
                logger.info(
                    "Triggering Novu for ${channelIds.size} channels for event ${event.typeKey}",
                )
                novuService.triggerWorkflow(event.typeKey, channelIds, event.payload)
            }
        } catch (e: Exception) {
            logger.error("Error processing event ${event.typeKey}", e)
        }
    }
}
