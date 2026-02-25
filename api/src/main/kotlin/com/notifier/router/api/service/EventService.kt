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
                logger.warn(
                    "Notification type ${event.typeKey} not found. Skipping event processing.",
                )
                return
            }

            // Find matching user subscriptions
            val subscriptions = subscriptionRepository.findByNotificationTypeId(notificationType.id)
            val matchingUserIds =
                subscriptions
                    .filter { it.enabled && filterEvaluator.evaluate(event, it) }
                    .map { it.userId }
                    .distinct()

            // Resolve Novu Subscriber IDs (which we sync as Slack IDs mapping)
            val users = userRepository.findAllById(matchingUserIds)
            val subscriberIds = users.map { it.slackId } // Assuming novu subscriberId == slackId

            // Trigger Novu for users
            if (subscriberIds.isNotEmpty()) {
                logger.info(
                    "Triggering Novu for ${subscriberIds.size} users for event ${event.typeKey}",
                )
                novuService.triggerWorkflow(event.typeKey, subscriberIds, event.payload)
            } else {
                logger.info("No matching individual subscriptions for event ${event.typeKey}")
            }

            // Find matching channel subscriptions
            // Currently channel subscriptions bypass FilterEvaluator because the interface is
            // slightly different,
            // but we can adapt FilterEvaluator to handle both or evaluate manually.
            val channelSubscriptions =
                channelSubscriptionRepository.findByNotificationTypeId(notificationType.id)
            val matchingChannelIds =
                channelSubscriptions
                    .filter { channelSub ->
                        // To reuse FilterEvaluator, we would map channelSub.filters to
                        // normal filters or evaluate directly
                        // Assuming for now channel subscriptions just get all if filters
                        // are empty
                        channelSub.filters.isEmpty() ||
                            channelSub.filters.all { filter ->
                                val value = event.metadata[filter.field]
                                when (filter.operator) {
                                    "EQ" -> {
                                        value == filter.value
                                    }

                                    "IN" -> {
                                        (filter.value as? List<*>)?.contains(
                                            value,
                                        ) == true
                                    }

                                    "CONTAINS" -> {
                                        (value as? String)?.contains(
                                            filter.value as? String ?: "",
                                            ignoreCase = true,
                                        ) == true
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            }
                    }.map { it.slackChannelId }
                    .distinct()

            if (matchingChannelIds.isNotEmpty()) {
                logger.info(
                    "Triggering Novu for ${matchingChannelIds.size} channels for event ${event.typeKey}",
                )
                // Channels can also be represented as specific types of subscribers in Novu or
                // topics
                // Depending on Novu strategy, we trigger them. Here we just trigger by channel
                // string mapping.
                novuService.triggerWorkflow(event.typeKey, matchingChannelIds, event.payload)
            }
        } catch (e: Exception) {
            logger.error("Error processing event ${event.typeKey}", e)
        }
    }
}
