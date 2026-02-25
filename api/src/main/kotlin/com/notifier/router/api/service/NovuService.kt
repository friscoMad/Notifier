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
) {
    private val logger = LoggerFactory.getLogger(NovuService::class.java)
    private lateinit var novuClient: Novu

    @PostConstruct
    fun init() {
        if (apiKey == "not-set" || apiKey.isBlank()) {
            logger.warn("Novu API Key is not set or empty. NovuService will log triggers instead.")
        } else {
            logger.info("Initializing Novu client")
            novuClient = Novu(apiKey)
            // Or novuAsync = NovuAsync(apiKey) if non-blocking is preferred
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
            // Note: TriggerEventRequest expects a 'to' object or a list.
            // In Novu Java SDK, we might need to send to each, or map the list depending on version
            // support.
            // Using standard Novu list triggers for broadcasts/multi-subscriber:
            val request = TriggerEventRequest()
            request.name = workflowId
            // The Novu Java SDK might not perfectly map `List<String>` subscriber ids to
            // `request.to`.
            // Some versions require SubscriberRequest objects.
            request.to = subscriberIds
            request.payload = payload.toMutableMap()

            val response = novuClient.triggerEvent(request)
            logger.info("Novu trigger response: ${response.data}")
        } catch (e: Exception) {
            logger.error("Failed to trigger Novu workflow $workflowId", e)
        }
    }
}
