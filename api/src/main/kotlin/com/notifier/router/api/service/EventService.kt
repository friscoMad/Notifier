package com.notifier.router.api.service

import com.notifier.router.api.domain.NotificationEvent
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
    fun processEventAsync(event: NotificationEvent) {
        logger.info("Processing event: ${event.typeKey}")
        try {
            val typeId =
                notificationTypeRepository.findByTypeKey(event.typeKey)?.id
                    ?: return logger.warn(
                        "Notification type ${event.typeKey} not found. Skipping.",
                    )

            // Collect matching user Slack IDs
            val userSlackIds =
                subscriptionRepository
                    .findByNotificationTypeId(typeId)
                    .filter { it.enabled && filterEvaluator.evaluate(event, it.filters) }
                    .map { it.userId }
                    .distinct()
                    .let { userRepository.findAllById(it) }
                    .map { it.slackId }

            // Collect matching Channel IDs
            val channelSlackIds =
                channelSubscriptionRepository
                    .findByNotificationTypeId(typeId)
                    .filter { filterEvaluator.evaluate(event, it.filters) }
                    .map { it.slackChannelId }
                    .distinct()

            val allTargets = (userSlackIds + channelSlackIds).distinct()

            if (allTargets.isEmpty()) {
                logger.info(
                    "No active subscriptions found for event ${event.typeKey}. Skipping trigger.",
                )
                return
            }

            logger.info(
                "Triggering Novu for event ${event.typeKey} with ${userSlackIds.size} users and ${channelSlackIds.size} channels",
            )

            triggerIfNotEmpty(event.typeKey, allTargets, event.payload, "targets")
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // @Async boundary: must catch all unchecked exceptions so the thread does not die silently.
            logger.error("Error processing event ${event.typeKey}", e)
        }
    }

    private fun triggerIfNotEmpty(
        typeKey: String,
        ids: List<String>,
        payload: Map<String, Any>,
        label: String,
    ) {
        if (ids.isEmpty()) return
        logger.info("Triggering Novu for ${ids.size} $label for event $typeKey")
        novuService.triggerWorkflow(typeKey, ids, payload)
    }
}
