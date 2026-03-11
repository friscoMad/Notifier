package com.notifier.router.api.novu

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate

class NovuApiClient(
    private val restTemplate: RestTemplate,
    private val baseUrl: String,
    private val apiKey: String,
    private val objectMapper: ObjectMapper,
) {
    // ── Integrations ──────────────────────────────────────────────────────────

    fun listIntegrations(): List<NovuIntegration> =
        restTemplate
            .exchange("$baseUrl/integrations", HttpMethod.GET, HttpEntity<Unit>(headers()), Map::class.java)
            .body
            .dataRawList()
            .map { objectMapper.readValue(objectMapper.writeValueAsString(it), NovuIntegration::class.java) }

    fun createIntegration(integration: NovuIntegration): NovuIntegration =
        restTemplate
            .exchange("$baseUrl/integrations", HttpMethod.POST, HttpEntity(integration, headers()), Map::class.java)
            .body
            .dataRawObject()
            .let { objectMapper.readValue(objectMapper.writeValueAsString(it), NovuIntegration::class.java) }

    fun updateIntegration(
        id: String,
        credentials: NovuCredentials,
        active: Boolean,
    ): NovuIntegration =
        restTemplate
            .exchange(
                "$baseUrl/integrations/$id",
                HttpMethod.PUT,
                HttpEntity(mapOf("credentials" to credentials, "active" to active), headers()),
                Map::class.java,
            ).body
            .dataRawObject()
            .let { objectMapper.readValue(objectMapper.writeValueAsString(it), NovuIntegration::class.java) }

    fun deleteIntegration(id: String) {
        restTemplate.exchange(
            "$baseUrl/integrations/$id",
            HttpMethod.DELETE,
            HttpEntity<Unit>(headers()),
            Map::class.java
        )
    }

    // ── Workflows ─────────────────────────────────────────────────────────────

    fun listWorkflows(): List<NovuWorkflow> =
        restTemplate
            .exchange(
                "$baseUrl/workflows?limit=$MAX_WORKFLOWS_PAGE_SIZE",
                HttpMethod.GET,
                HttpEntity<Unit>(headers()),
                Map::class.java,
            ).body
            .dataRawList()
            .map { objectMapper.convertValue(it, NovuWorkflow::class.java) }

    fun createWorkflow(workflow: NovuWorkflow): NovuWorkflow =
        restTemplate
            .exchange(
                "$baseUrl/workflows",
                HttpMethod.POST,
                HttpEntity(workflow, headers()),
                Map::class.java,
            ).body
            .dataRawObject()
            .let { objectMapper.convertValue(it, NovuWorkflow::class.java) }

    fun updateWorkflow(
        id: String,
        workflow: NovuWorkflow,
    ): NovuWorkflow =
        restTemplate
            .exchange(
                "$baseUrl/workflows/$id",
                HttpMethod.PUT,
                HttpEntity(workflow, headers()),
                Map::class.java,
            ).body
            .dataRawObject()
            .let { objectMapper.convertValue(it, NovuWorkflow::class.java) }

    // ── Notification Groups ───────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    fun listNotificationGroups(): List<Map<String, Any>> =
        restTemplate
            .exchange(
                "$baseUrl/notification-groups",
                HttpMethod.GET,
                HttpEntity<Unit>(headers()),
                Map::class.java,
            ).body
            .dataRawList() as List<Map<String, Any>>

    // ── Channel Connections ───────────────────────────────────────────────────

    fun listChannelConnections(): List<NovuChannelConnection> =
        restTemplate
            .exchange("$baseUrl/channel-connections", HttpMethod.GET, HttpEntity<Unit>(headers()), Map::class.java)
            .body
            .dataRawList()
            .map { objectMapper.convertValue(it, NovuChannelConnection::class.java) }

    fun createChannelConnection(connection: NovuChannelConnection): NovuChannelConnection =
        restTemplate
            .exchange(
                "$baseUrl/channel-connections",
                HttpMethod.POST,
                HttpEntity(connection, headers()),
                Map::class.java,
            ).body
            .dataRawObject()
            .let { objectMapper.convertValue(it, NovuChannelConnection::class.java) }

    fun deleteChannelConnection(identifier: String) {
        restTemplate.exchange(
            "$baseUrl/channel-connections/$identifier",
            HttpMethod.DELETE,
            HttpEntity<Unit>(headers()),
            Map::class.java,
        )
    }

    // ── Channel Endpoints ─────────────────────────────────────────────────────

    fun listChannelEndpoints(subscriberId: String): List<NovuChannelEndpoint> =
        restTemplate
            .exchange(
                "$baseUrl/channel-endpoints?subscriberId=$subscriberId",
                HttpMethod.GET,
                HttpEntity<Unit>(headers()),
                Map::class.java,
            ).body
            .dataRawList()
            .map { objectMapper.convertValue(it, NovuChannelEndpoint::class.java) }

    fun createChannelEndpoint(endpoint: NovuChannelEndpoint): NovuChannelEndpoint =
        restTemplate
            .exchange(
                "$baseUrl/channel-endpoints",
                HttpMethod.POST,
                HttpEntity(endpoint, headers()),
                Map::class.java,
            ).body
            .dataRawObject()
            .let { objectMapper.convertValue(it, NovuChannelEndpoint::class.java) }

    fun deleteChannelEndpoint(identifier: String) {
        restTemplate.exchange(
            "$baseUrl/channel-endpoints/$identifier",
            HttpMethod.DELETE,
            HttpEntity<Unit>(headers()),
            Map::class.java,
        )
    }

    // ── Subscribers ───────────────────────────────────────────────────────────

    fun upsertSubscriber(subscriber: NovuSubscriber) {
        restTemplate.exchange(
            "$baseUrl/subscribers",
            HttpMethod.POST,
            HttpEntity(subscriber, headers()),
            Map::class.java,
        )
    }

    // ── Notifications ────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    fun listNotifications(subscriberId: String): List<Map<String, Any?>> =
        restTemplate
            .exchange(
                "$baseUrl/notifications?subscriberIds=$subscriberId&limit=$NOTIFICATION_PAGE_SIZE",
                HttpMethod.GET,
                HttpEntity<Unit>(headers()),
                Map::class.java,
            ).body
            .dataRawList() as List<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    fun listAllNotifications(): List<Map<String, Any?>> =
        restTemplate
            .exchange(
                "$baseUrl/notifications?limit=$NOTIFICATION_PAGE_SIZE",
                HttpMethod.GET,
                HttpEntity<Unit>(headers()),
                Map::class.java,
            ).body
            .dataRawList() as List<Map<String, Any?>>

    // ── Private Helpers ───────────────────────────────────────────────────────

    private fun headers(): HttpHeaders =
        HttpHeaders().also {
            it.set(HttpHeaders.AUTHORIZATION, "ApiKey $apiKey")
            it.contentType = MediaType.APPLICATION_JSON
        }

    companion object {
        private const val NOTIFICATION_PAGE_SIZE = 50
        private const val MAX_WORKFLOWS_PAGE_SIZE = 100
    }
}

@Suppress("UNCHECKED_CAST")
private fun Map<*, *>?.dataRawList(): List<Map<String, Any?>> =
    (this?.get("data") as? List<*>)
        ?.filterIsInstance<Map<String, Any?>>()
        ?: emptyList()

@Suppress("UNCHECKED_CAST")
private fun Map<*, *>?.dataRawObject(): Map<String, Any?> =
    (this?.get("data") as? Map<String, Any?>)
        ?: error("Missing 'data' field in Novu response")
