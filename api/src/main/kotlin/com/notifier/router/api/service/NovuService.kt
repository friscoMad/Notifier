package com.notifier.router.api.service

import co.novu.api.events.requests.TriggerEventRequest
import co.novu.common.base.Novu
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class NovuService(
    @Value("\${novu.api-key:not-set}") private val apiKey: String,
    @Value("\${novu.api.url:https://api.novu.co}") private val apiUrl: String,
) {
    private val logger = LoggerFactory.getLogger(NovuService::class.java)
    private lateinit var novuClient: Novu

    @PostConstruct
    fun init() {
        if (apiKey == "not-set" || apiKey.isBlank()) {
            logger.warn("Novu API Key is not set or empty. NovuService will log triggers instead.")
        } else {
            logger.info("Initializing Novu client with API URL: $apiUrl")
            novuClient = Novu(apiKey)
        }
    }

    fun triggerWorkflow(
        workflowId: String,
        subscriberIds: List<String>,
        payload: Map<String, Any>,
    ) {
        if (!this::novuClient.isInitialized) {
            logger.info(
                "MOCK Trigger Workflow: $workflowId to subscribers: $subscriberIds with payload: $payload",
            )
            return
        }

        try {
            val request = TriggerEventRequest()
            request.name = workflowId
            request.to = subscriberIds
            request.payload = payload.toMutableMap()

            val response = novuClient.triggerEvent(request)
            logger.info("Novu trigger response: ${response.data}")
        } catch (e: Exception) {
            logger.error("Failed to trigger Novu workflow $workflowId", e)
        }
    }
}
