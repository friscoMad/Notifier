package com.notifier.router.bot.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class RouterApiClient(
    @Value("\${router.api.url:http://localhost:8080/api/v1}") private val apiUrl: String,
) {
    private val restTemplate = RestTemplate()

    fun getNotificationTypes(): List<Map<String, String>> {
        val url = "$apiUrl/notification-types"
        val response =
            restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<List<Map<String, String>>>() {},
            )
        return response.body ?: emptyList()
    }

    fun subscribe(payload: Map<String, Any>): Map<String, Any>? {
        val url = "$apiUrl/subscriptions"
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(payload, headers)

        return try {
            val response =
                restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    object : ParameterizedTypeReference<Map<String, Any>>() {},
                )
            response.body
        } catch (e: Exception) {
            null
        }
    }

    fun getSubscriptionsForUser(slackId: String): List<Map<String, Any>> {
        val url = "$apiUrl/users/$slackId/subscriptions"
        return try {
            val response =
                restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    object : ParameterizedTypeReference<List<Map<String, Any>>>() {},
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

    fun getFiltersForType(typeKey: String): List<Map<String, Any>> {
        val url = "$apiUrl/notification-types/$typeKey/filters"
        return try {
            val response =
                restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    object : ParameterizedTypeReference<List<Map<String, Any>>>() {},
                )
            response.body ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
