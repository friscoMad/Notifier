package com.notifier.router.bot.service

import com.notifier.router.bot.client.RouterApiClient
import com.notifier.router.common.domain.Filter
import com.notifier.router.common.dto.ChannelSubscriptionDto
import com.notifier.router.common.dto.SubscriptionDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SubscriptionService(
    private val apiClient: RouterApiClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun subscribeAndActivate(
        userId: String,
        email: String? = null,
        notificationTypeId: String,
        channels: List<String>,
        filters: List<Filter> = emptyList(),
        channelConfig: Map<String, Any> = emptyMap(),
    ): SubscribeResult {
        val subscription =
            try {
                apiClient.subscribe(
                    SubscriptionDto(
                        userId = userId,
                        email = email,
                        notificationTypeId = notificationTypeId,
                        channels = channels,
                        channelConfig = channelConfig,
                        filters = filters,
                    ),
                ) ?: return SubscribeResult.Failure
            } catch (e: org.springframework.web.client.HttpClientErrorException) {
                if (e.statusCode == org.springframework.http.HttpStatus.CONFLICT) {
                    return SubscribeResult.AlreadySubscribed
                }
                logger.error("Failed to subscribe $userId to $notificationTypeId", e)
                return SubscribeResult.Failure
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.error("Failed to subscribe $userId to $notificationTypeId", e)
                return SubscribeResult.Failure
            }

        try {
            logger.info("Creating Slack endpoint for subscriber $userId")
            apiClient.createSlackEndpoint(userId)
        } catch (e: org.springframework.web.client.RestClientException) {
            logger.warn("Could not create Slack endpoint for $userId: ${e.message}", e)
        }

        return SubscribeResult.Success(subscription)
    }

    fun subscribeChannelAndActivate(
        channelId: String,
        channelName: String,
        notificationTypeId: String,
        filters: List<Filter> = emptyList(),
    ): ChannelSubscribeResult {
        val subscription =
            try {
                apiClient.subscribeChannel(
                    ChannelSubscriptionDto(
                        slackChannelId = channelId,
                        slackChannelName = channelName,
                        notificationTypeId = notificationTypeId,
                        filters = filters,
                    ),
                ) ?: return ChannelSubscribeResult.Failure
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.error("Failed to subscribe channel $channelId to $notificationTypeId", e)
                return ChannelSubscribeResult.Failure
            }

        try {
            logger.info("Creating Slack endpoint for channel $channelId")
            apiClient.createSlackEndpoint(channelId)
        } catch (e: org.springframework.web.client.RestClientException) {
            logger.warn("Could not create Slack endpoint for channel $channelId: ${e.message}", e)
        }

        return ChannelSubscribeResult.Success(subscription)
    }

    fun unsubscribe(
        userId: String,
        notificationTypeId: String,
    ): UnsubscribeResult {
        val subs =
            try {
                apiClient.getSubscriptionsForUser(userId)
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.error("Failed to fetch subscriptions for $userId", e)
                return UnsubscribeResult.Failure
            }

        val sub = subs.find { it.notificationTypeId == notificationTypeId }
            ?: return UnsubscribeResult.NotSubscribed

        return try {
            apiClient.unsubscribe(sub.id!!)
            UnsubscribeResult.Success
        } catch (e: org.springframework.web.client.RestClientException) {
            logger.error("Failed to unsubscribe $userId from type $notificationTypeId", e)
            UnsubscribeResult.Failure
        }
    }
}

sealed interface SubscribeResult {
    data class Success(
        val subscription: SubscriptionDto,
    ) : SubscribeResult

    data object AlreadySubscribed : SubscribeResult

    data object Failure : SubscribeResult
}

sealed interface ChannelSubscribeResult {
    data class Success(
        val subscription: ChannelSubscriptionDto,
    ) : ChannelSubscribeResult

    data object Failure : ChannelSubscribeResult
}

sealed interface UnsubscribeResult {
    data object Success : UnsubscribeResult
    data object NotSubscribed : UnsubscribeResult
    data object Failure : UnsubscribeResult
}
