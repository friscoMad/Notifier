package com.notifier.router.bot.client

import com.notifier.router.bot.config.LoggingInterceptor
import com.notifier.router.common.dto.ChannelSubscriptionDto
import com.notifier.router.common.dto.FilterDefinitionDto
import com.notifier.router.common.dto.NotificationTypeDto
import com.notifier.router.common.dto.SubscriptionDto
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class RouterApiClient(
    @Value("\${router.api.url:http://localhost:8080/api/v1}") private val apiUrl: String,
) {
    private val restTemplate =
        RestTemplate(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())).apply {
            interceptors = listOf(LoggingInterceptor())
        }

    fun getNotificationTypes(): List<NotificationTypeDto> {
        val url = "$apiUrl/notification-types"
        val response =
            restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<List<NotificationTypeDto>>() {},
            )
        return response.body ?: emptyList()
    }

    fun subscribe(dto: SubscriptionDto): SubscriptionDto? {
        val url = "$apiUrl/subscriptions"
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(dto, headers)

        return try {
            val response =
                restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    object : ParameterizedTypeReference<SubscriptionDto>() {},
                )
            response.body
        } catch (e: Exception) {
            null
        }
    }

    fun getSubscriptionsForUser(slackId: String): List<SubscriptionDto> {
        val url = "$apiUrl/subscriptions/users/$slackId"
        return try {
            val response =
                restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    object : ParameterizedTypeReference<List<SubscriptionDto>>() {},
                )
            response.body ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun unsubscribe(subscriptionId: String): Boolean {
        val url = "$apiUrl/subscriptions/$subscriptionId"
        return try {
            restTemplate.delete(url)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun subscribeChannel(dto: ChannelSubscriptionDto): ChannelSubscriptionDto? {
        val url = "$apiUrl/channel-subscriptions"
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return try {
            restTemplate
                .exchange(
                    url,
                    HttpMethod.POST,
                    HttpEntity(dto, headers),
                    object : ParameterizedTypeReference<ChannelSubscriptionDto>() {},
                ).body
        } catch (e: Exception) {
            null
        }
    }

    fun createSlackEndpoint(subscriberId: String) {
        restTemplate.postForObject(
            "$apiUrl/subscribers/$subscriberId/slack/endpoint",
            null,
            Void::class.java,
        )
    }

    fun getFiltersForType(typeKey: String): List<FilterDefinitionDto> {
        val url = "$apiUrl/notification-types/$typeKey/filters"
        return try {
            val response =
                restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    object : ParameterizedTypeReference<List<FilterDefinitionDto>>() {},
                )
            response.body ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
