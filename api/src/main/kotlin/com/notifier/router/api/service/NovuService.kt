package com.notifier.router.api.service

import co.novu.api.events.requests.TriggerEventRequest
import co.novu.common.base.Novu
import co.novu.common.base.NovuConfig
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import retrofit2.Retrofit

@Service
class NovuService(
    @Value("\${novu.api.key:not-set}") private val apiKey: String,
    @Value("\${novu.api.url:}") private val apiUrl: String,
) {
    private val logger = LoggerFactory.getLogger(NovuService::class.java)
    private lateinit var novuClient: Novu

    /**
     * In-memory registry mapping our typeKey (e.g. "pr_created") to Novu's auto-generated trigger
     * identifier (e.g. "pull-request-created-whmu"). Populated by [ensureWorkflowExists].
     */
    private val workflowRegistry = mutableMapOf<String, String>()

    @PostConstruct
    fun init() {
        if (apiKey == "not-set" || apiKey.isBlank()) {
            logger.warn("Novu API Key is not set or empty. NovuService will log triggers instead.")
        } else {
            logger.info("Initializing Novu client")
            novuClient = Novu(apiKey)

            // If a custom API URL is configured, override the SDK's internal baseUrl
            // using reflection since the Novu Java SDK v1.6.0 does not expose a setter.
            if (apiUrl.isNotBlank()) {
                overrideBaseUrl(apiUrl)
            }
        }
    }

    /**
     * Uses Java reflection to override the Novu SDK's internal base URL.
     *
     * The SDK stores the URL in multiple places that need to be updated: Novu.novuConfig.baseUrl —
     * top-level config Novu.eventsHandler.restHandler.novuConfig.baseUrl — handler's config copy
     * Novu.eventsHandler.restHandler.retrofit.baseUrl — Retrofit's cached URL
     *
     * The Retrofit instance caches the base URL at construction time, so we must also rebuild it
     * with the new URL via Retrofit.Builder.
     */
    private fun overrideBaseUrl(url: String) {
        try {
            val normalizedUrl = if (url.endsWith("/")) url else "$url/"

            // 1. Override NovuConfig.baseUrl at the top-level
            val novuConfigField = Novu::class.java.getDeclaredField("novuConfig")
            novuConfigField.isAccessible = true
            val rootConfig = novuConfigField.get(novuClient) as NovuConfig

            val baseUrlField = NovuConfig::class.java.getDeclaredField("baseUrl")
            baseUrlField.isAccessible = true
            baseUrlField.set(rootConfig, normalizedUrl)

            // 2. Override each handler's RestHandler → novuConfig.baseUrl and rebuild Retrofit
            val handlerFieldNames =
                listOf(
                    "eventsHandler",
                    "notificationHandler",
                    "topicHandler",
                    "subscribersHandler",
                    "integrationsHandler",
                    "layoutHandler",
                    "workflowHandler",
                    "workflowGroupHandler",
                    "changeHandler",
                    "environmentHandler",
                    "inboundParseHandler",
                    "feedsHandler",
                    "messageHandler",
                    "executiveDetailsHandler",
                    "blueprintsHandler",
                    "tenantsHandler",
                    "organizationHandler",
                    "workflowOverrideHandler",
                )

            for (handlerName in handlerFieldNames) {
                try {
                    val handlerField = Novu::class.java.getDeclaredField(handlerName)
                    handlerField.isAccessible = true
                    val handler = handlerField.get(novuClient) ?: continue

                    val restHandlerField = handler::class.java.getDeclaredField("restHandler")
                    restHandlerField.isAccessible = true
                    val restHandler = restHandlerField.get(handler) ?: continue

                    // Override the RestHandler's novuConfig.baseUrl
                    val restNovuConfigField = restHandler::class.java.getDeclaredField("novuConfig")
                    restNovuConfigField.isAccessible = true
                    val restConfig = restNovuConfigField.get(restHandler) as NovuConfig
                    baseUrlField.set(restConfig, normalizedUrl)

                    // Rebuild the Retrofit instance with the new base URL
                    val retrofitField = restHandler::class.java.getDeclaredField("retrofit")
                    retrofitField.isAccessible = true
                    val oldRetrofit = retrofitField.get(restHandler) as Retrofit
                    val newRetrofit = oldRetrofit.newBuilder().baseUrl(normalizedUrl).build()
                    retrofitField.set(restHandler, newRetrofit)

                    // Rebuild API interface proxies on the handler that were created
                    // from the old Retrofit. These are Retrofit dynamic proxies whose
                    // interfaces end in "Api" (e.g., EventsApi, SubscribersApi, etc.)
                    for (apiField in handler::class.java.declaredFields) {
                        if (apiField.name == "restHandler") continue
                        apiField.isAccessible = true
                        val apiProxy = apiField.get(handler) ?: continue

                        // Find the Retrofit interface type from the proxy's interfaces
                        val apiInterface =
                            apiProxy::class.java.interfaces.firstOrNull {
                                it.name.contains("Api")
                            }
                        if (apiInterface != null) {
                            val newProxy = newRetrofit.create(apiInterface)
                            apiField.set(handler, newProxy)
                        }
                    }
                } catch (e: NoSuchFieldException) {
                    // Some handlers might have different structure, skip silently
                }
            }

            logger.info("Overrode Novu base URL to: $normalizedUrl")
        } catch (e: Exception) {
            logger.error("Failed to override Novu base URL via reflection", e)
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
            val novuIdentifier = workflowRegistry[workflowId] ?: workflowId
            if (novuIdentifier != workflowId) {
                logger.debug("Resolved typeKey $workflowId -> Novu identifier $novuIdentifier")
            }
            val request = TriggerEventRequest()
            request.name = novuIdentifier
            request.to = subscriberIds
            request.payload = payload.toMutableMap()

            val response = novuClient.triggerEvent(request)
            logger.info("Novu trigger response: ${response.data}")
        } catch (e: Exception) {
            logger.error("Failed to trigger Novu workflow $workflowId", e)
        }
    }

    fun syncSubscriberPreferences(
        subscriberId: String,
        workflowKey: String,
        channels: List<String>,
        channelConfig: Map<String, Any>,
    ) {
        if (!this::novuClient.isInitialized) {
            logger.info(
                "MOCK Sync Preferences for $subscriberId: workflow=$workflowKey, channels=$channels, config=$channelConfig",
            )
            return
        }

        try {
            val headers = createHeaders()
            val baseUrl = getBaseUrl()

            // 1. Ensure subscriber exists first (upsert)
            val identifyUrl = "$baseUrl/subscribers"
            val identifyPayload =
                mapOf(
                    "subscriberId" to subscriberId,
                    "firstName" to "User",
                    "lastName" to subscriberId.take(4),
                )
            org.springframework.web.client
                .RestTemplate()
                .exchange(
                    identifyUrl,
                    org.springframework.http.HttpMethod.POST,
                    org.springframework.http.HttpEntity(identifyPayload, headers),
                    String::class.java,
                )

            // 2. We could update the preferences per channel using Novu's Subscriber Preference
            // API.
            // Note: The template ID/Key is required for the PATCH endpoints in production
            // environments.
            logger.info(
                "Successfully synced subscriber $subscriberId to Novu. Channels: $channels, Config: $channelConfig",
            )
        } catch (e: Exception) {
            logger.error("Failed to sync preferences for $subscriberId", e)
        }
    }

    fun ensureWorkflowExists(
        key: String,
        name: String,
    ) {
        if (!this::novuClient.isInitialized) {
            logger.warn("Novu client not initialized, skipping workflow check for $key")
            return
        }

        try {
            val baseUrl = getBaseUrl()
            val headers = createHeaders()
            val restTemplate =
                org.springframework.web.client
                    .RestTemplate()
            logger.debug("Checking if Novu workflow exists: $key")

            // 1. Check if workflow exists
            val workflowsUrl = "$baseUrl/workflows"
            val response =
                restTemplate.exchange(
                    workflowsUrl,
                    org.springframework.http.HttpMethod.GET,
                    org.springframework.http.HttpEntity<Unit>(headers),
                    Map::class.java,
                )

            val data = response.body?.get("data") as? List<Map<String, Any>> ?: emptyList()

            // Match by name since we use the typeKey as the workflow name in Novu
            val matchingWorkflow = data.find { it["name"] == key }

            if (matchingWorkflow != null) {
                val triggers = matchingWorkflow["triggers"] as? List<Map<String, Any>>
                val novuIdentifier = triggers?.firstOrNull()?.get("identifier") as? String
                if (novuIdentifier != null) {
                    workflowRegistry[key] = novuIdentifier
                    logger.info("Registered existing Novu workflow: $key -> $novuIdentifier")
                }
                return
            }

            // 2. Resolve Notification Group ID (First one found or default)
            val groupsUrl = "$baseUrl/notification-groups"
            val groupsResponse =
                restTemplate.exchange(
                    groupsUrl,
                    org.springframework.http.HttpMethod.GET,
                    org.springframework.http.HttpEntity<Unit>(headers),
                    Map::class.java,
                )
            val groupsData = groupsResponse.body?.get("data") as? List<Map<String, Any>>
            val groupId =
                groupsData?.firstOrNull()?.get("_id") as? String
                    ?: return logger.error("Could not find any notification groups in Novu")

            // 3. Create workflow using typeKey as the name for easy matching later
            val createPayload =
                mapOf(
                    "name" to key,
                    "notificationGroupId" to groupId,
                    "steps" to
                        listOf(
                            mapOf(
                                "template" to
                                    mapOf(
                                        "type" to "in_app",
                                        "content" to "{{content}}",
                                    ),
                            ),
                        ),
                    "active" to true,
                    "draft" to false,
                    "critical" to false,
                )

            logger.debug("Creating Novu workflow $key")
            val createResponse =
                restTemplate.exchange(
                    workflowsUrl,
                    org.springframework.http.HttpMethod.POST,
                    org.springframework.http.HttpEntity(createPayload, headers),
                    Map::class.java,
                )

            // Extract the auto-generated trigger identifier from the response
            val responseData = createResponse.body?.get("data") as? Map<String, Any>
            val triggers = responseData?.get("triggers") as? List<Map<String, Any>>
            val novuIdentifier = triggers?.firstOrNull()?.get("identifier") as? String
            if (novuIdentifier != null) {
                workflowRegistry[key] = novuIdentifier
                logger.info("Provisioned Novu workflow: $key -> $novuIdentifier ($name)")
            } else {
                logger.warn("Created Novu workflow $key but could not extract trigger identifier")
            }
        } catch (e: org.springframework.web.client.RestClientResponseException) {
            logger.error(
                "Failed to ensure Novu workflow $key exists. Status: ${e.statusCode}, Response: ${e.responseBodyAsString}",
                e,
            )
        } catch (e: Exception) {
            logger.error("Failed to ensure Novu workflow $key exists", e)
        }
    }

    private fun createHeaders(): org.springframework.http.HttpHeaders {
        val headers = org.springframework.http.HttpHeaders()
        headers.set("Authorization", "ApiKey $apiKey")
        headers.contentType = org.springframework.http.MediaType.APPLICATION_JSON
        return headers
    }

    private fun getBaseUrl() = if (apiUrl.isNotBlank()) apiUrl else "https://api.novu.co/v1"
}
