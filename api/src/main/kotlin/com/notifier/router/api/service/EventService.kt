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

    private companion object {
        val DIGEST_ELIGIBLE_CHANNELS = setOf("chat", "email")
        val DIGEST_SUPPORTED_TYPE_KEYS = setOf("pr_created")
        const val DEFAULT_DIGEST_INTERVAL = "1d"
    }

    @Async
    fun processEventAsync(event: NotificationEvent) {
        logger.info("Processing event: ${event.typeKey}")
        try {
            val typeId =
                notificationTypeRepository.findByTypeKey(event.typeKey)?.id
                    ?: return logger.warn(
                        "Notification type ${event.typeKey} not found. Skipping.",
                    )

            // Collect matching user subscriptions
            val matchingSubs =
                subscriptionRepository
                    .findByNotificationTypeId(typeId)
                    .filter { it.enabled && filterEvaluator.evaluate(event, it.filters) }

            val userIds = matchingSubs.map { it.userId }.distinct()
            val usersById = userRepository.findAllById(userIds).associateBy { it.id }

            // Group subscribers by channel: immediate vs digest delivery
            // digestWorkflowToSubscribers key = "${channel}_digest_${intervalKey}"
            val channelToSubscribers = mutableMapOf<String, MutableSet<String>>()
            val digestWorkflowToSubscribers = mutableMapOf<String, MutableSet<String>>()
            matchingSubs.forEach { sub ->
                val slackId = usersById[sub.userId]?.slackId ?: return@forEach
                // Always deliver in_app immediately (web dashboard)
                channelToSubscribers.getOrPut("in_app") { mutableSetOf() }.add(slackId)
                sub.channels.filter { it != "in_app" }.forEach { channel ->
                    // Normalize slack_dm -> chat (Novu channel type)
                    val novuChannel = if (channel == "slack_dm") "chat" else channel
                    val digestEnabled = sub.channelConfig["digest"] == true &&
                        novuChannel in DIGEST_ELIGIBLE_CHANNELS &&
                        event.typeKey in DIGEST_SUPPORTED_TYPE_KEYS
                    if (digestEnabled) {
                        val intervalKey = sub.channelConfig["digestInterval"] as? String ?: DEFAULT_DIGEST_INTERVAL
                        digestWorkflowToSubscribers
                            .getOrPut("${novuChannel}_digest_$intervalKey") { mutableSetOf() }
                            .add(slackId)
                    } else {
                        channelToSubscribers.getOrPut(novuChannel) { mutableSetOf() }.add(slackId)
                    }
                }
            }

            // Collect matching Channel subscriptions
            val matchingChannelSubs =
                channelSubscriptionRepository
                    .findByNotificationTypeId(typeId)
                    .filter { it.enabled && filterEvaluator.evaluate(event, it.filters) }

            // Channel subscriptions only get chat delivery (Slack channel IDs
            // are not valid Novu subscriber IDs for in_app workflows)
            matchingChannelSubs.forEach { chanSub ->
                val digestEligible = chanSub.digestEnabled &&
                    event.typeKey in DIGEST_SUPPORTED_TYPE_KEYS
                if (digestEligible) {
                    digestWorkflowToSubscribers
                        .getOrPut("chat_digest_${chanSub.digestInterval}") { mutableSetOf() }
                        .add(chanSub.slackChannelId)
                } else {
                    channelToSubscribers.getOrPut("chat") { mutableSetOf() }.add(chanSub.slackChannelId)
                }
            }

            if (channelToSubscribers.isEmpty() && digestWorkflowToSubscribers.isEmpty()) {
                logger.info(
                    "No active subscriptions found for event ${event.typeKey}. Skipping trigger.",
                )
                return
            }

            logger.info(
                "Triggering Novu for event ${event.typeKey}: " +
                    channelToSubscribers.entries.joinToString { "${it.key}=${it.value.size}" },
            )

            // Trigger each channel-specific workflow with the right subscribers
            channelToSubscribers.forEach { (channel, subscribers) ->
                novuService.triggerChannelWorkflow(
                    event.typeKey,
                    channel,
                    subscribers.toList(),
                    event.payload,
                )
            }

            // Trigger digest workflows for digest-enabled subscribers
            digestWorkflowToSubscribers.forEach { (workflowSuffix, subscribers) ->
                novuService.triggerWorkflow(
                    "${event.typeKey}_$workflowSuffix",
                    subscribers.toList(),
                    event.payload,
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // @Async boundary: must catch all unchecked exceptions so the thread does not die silently.
            logger.error("Error processing event ${event.typeKey}", e)
        }
    }
}
