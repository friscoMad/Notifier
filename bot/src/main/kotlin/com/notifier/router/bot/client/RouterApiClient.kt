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
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return restTemplate.exchange(
            "$apiUrl/subscriptions",
            HttpMethod.POST,
            HttpEntity(dto, headers),
            object : ParameterizedTypeReference<SubscriptionDto>() {},
        ).body
    }

    fun getSubscriptionsForUser(slackId: String): List<SubscriptionDto> =
        restTemplate.exchange(
            "$apiUrl/subscriptions/users/$slackId",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<SubscriptionDto>>() {},
        ).body ?: emptyList()

    fun unsubscribe(subscriptionId: String) {
        restTemplate.delete("$apiUrl/subscriptions/$subscriptionId")
    }

    fun subscribeChannel(dto: ChannelSubscriptionDto): ChannelSubscriptionDto? {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return restTemplate.exchange(
            "$apiUrl/channel-subscriptions",
            HttpMethod.POST,
            HttpEntity(dto, headers),
            object : ParameterizedTypeReference<ChannelSubscriptionDto>() {},
        ).body
    }

    fun createSlackEndpoint(subscriberId: String) {
        restTemplate.postForObject(
            "$apiUrl/subscribers/$subscriberId/slack/endpoint",
            null,
            Void::class.java,
        )
    }

    fun getFiltersForType(typeKey: String): List<FilterDefinitionDto> =
        restTemplate.exchange(
            "$apiUrl/notification-types/$typeKey/filters",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<FilterDefinitionDto>>() {},
        ).body ?: emptyList()
}
