package com.notifier.router.api.service

import co.novu.api.events.requests.TriggerEventRequest
import co.novu.common.base.Novu
import co.novu.common.base.NovuConfig
import com.notifier.router.api.config.LoggingInterceptor
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import retrofit2.Retrofit

@Service
class NovuService(
    @Value("\${novu.api.key:not-set}") private val apiKey: String,
    @Value("\${novu.api.url:}") private val apiUrl: String,
    @Value("\${novu.slack.client-id:}") private val slackClientId: String,
    @Value("\${novu.slack.client-secret:}") private val slackClientSecret: String,
    @Value("\${novu.slack.application-id:}") private val slackApplicationId: String,
    @Value("\${novu.slack.bot-token:}") private val slackBotToken: String,
    @Value("\${novu.slack.workspace-id:}") private val slackWorkspaceId: String,
    @Value("\${novu.slack.workspace-name:Slack Workspace}") private val slackWorkspaceName: String,
) {
    private val logger = LoggerFactory.getLogger(NovuService::class.java)
    private lateinit var novuClient: Novu
    private val restTemplate =
        RestTemplate(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())).apply {
            interceptors = listOf(LoggingInterceptor())
        }

    /**
     * In-memory registry mapping our typeKey (e.g. "pr_created") to Novu's auto-generated trigger
     * identifier (e.g. "pull-request-created-whmu"). Populated by [ensureWorkflowExists].
     */
    private val workflowRegistry = mutableMapOf<String, String>()

    /**
     * The Novu integration identifier for Slack (e.g. "slack-abc123").
     * Set by [ensureSlackIntegrationExists].
     */
    private var slackIntegrationIdentifier: String? = null

    /**
     * The Novu channel connection identifier for the Slack workspace (e.g. "chconn-abc123").
     * Set by [ensureSlackWorkspaceConnectionExists]. Referenced when creating per-subscriber
     * channel endpoints so Novu can resolve the bot token at delivery time.
     */
    private var slackConnectionIdentifier: String? = null

    /** Tracks subscribers already upserted into Novu (avoids redundant API calls). */
    private val knownSubscribers = mutableSetOf<String>()

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
            subscriberIds.forEach { ensureSubscriberExists(it) }

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
            restTemplate
                .exchange(
                    identifyUrl,
                    HttpMethod.POST,
                    HttpEntity(identifyPayload, headers),
                    String::class.java,
                )

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
            val restTemplate = restTemplate
            logger.debug("Checking if Novu workflow exists: $key")

            // 1. Check if workflow exists
            val workflowsUrl = "$baseUrl/workflows"
            val response =
                restTemplate.exchange(
                    workflowsUrl,
                    HttpMethod.GET,
                    HttpEntity<Unit>(headers),
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
                }

                // Ensure workflow has in_app and chat steps (remove push if present)
                val steps = matchingWorkflow["steps"] as? List<Map<String, Any>> ?: emptyList()
                val hasInAppStep = steps.any { (it["template"] as? Map<*, *>)?.get("type") == "in_app" }
                val hasChatStep = steps.any { (it["template"] as? Map<*, *>)?.get("type") == "chat" }
                val hasPushStep = steps.any { (it["template"] as? Map<*, *>)?.get("type") == "push" }

                if (!hasChatStep || hasPushStep) {
                    logger.info("Workflow $key needs patching (hasChatStep=$hasChatStep, hasPushStep=$hasPushStep)")
                    val workflowId = matchingWorkflow["_id"] as? String
                    if (workflowId != null) {
                        val cleanSteps =
                            steps
                                .filter { (it["template"] as? Map<*, *>)?.get("type") != "push" }
                                .let { filtered ->
                                    if (!hasChatStep) {
                                        filtered +
                                            mapOf("template" to mapOf("type" to "chat", "content" to "{{content}}"))
                                    } else {
                                        filtered
                                    }
                                }
                        restTemplate.exchange(
                            "$workflowsUrl/$workflowId",
                            HttpMethod.PUT,
                            HttpEntity(matchingWorkflow + mapOf("steps" to cleanSteps), headers),
                            Map::class.java,
                        )
                        logger.info("Patched workflow $key: removed push, ensured chat step")
                    }
                } else {
                    logger.info("Registered existing Novu workflow: $key -> $novuIdentifier")
                }
                return
            }

            // 2. Resolve Notification Group ID (First one found or default)
            val groupsUrl = "$baseUrl/notification-groups"
            val groupsResponse =
                restTemplate.exchange(
                    groupsUrl,
                    HttpMethod.GET,
                    HttpEntity<Unit>(headers),
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
                            mapOf(
                                "template" to
                                    mapOf(
                                        "type" to "chat",
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
                    HttpMethod.POST,
                    HttpEntity(createPayload, headers),
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

    fun ensureSlackIntegrationExists() {
        if (!this::novuClient.isInitialized) {
            logger.warn("Novu client not initialized, skipping Slack integration check")
            return
        }

        if (slackClientId.isBlank() || slackClientSecret.isBlank() || slackApplicationId.isBlank()) {
            logger.warn("novu.slack credentials not configured — skipping Slack integration setup")
            return
        }

        try {
            val baseUrl = getBaseUrl()
            val headers = createHeaders()
            val integrationsUrl = "$baseUrl/integrations"
            val credentials =
                mapOf(
                    "clientId" to slackClientId,
                    "secretKey" to slackClientSecret,
                    "applicationId" to slackApplicationId,
                    "token" to slackBotToken,
                )

            val existing =
                (
                    restTemplate
                        .exchange(integrationsUrl, HttpMethod.GET, HttpEntity<Unit>(headers), Map::class.java)
                        .body
                        ?.get("data") as? List<Map<String, Any>> ?: emptyList()
                ).filter { it["providerId"] == "slack" && it["channel"] == "chat" }

            val integration: Map<*, *>
            if (existing.isNotEmpty()) {
                // Update credentials on the existing integration — do NOT delete it,
                // as that would orphan all channel endpoints referencing its identifier.
                val existingId = existing[0]["_id"] as String
                integration =
                    restTemplate
                        .exchange(
                            "$integrationsUrl/$existingId",
                            HttpMethod.PUT,
                            HttpEntity(mapOf("credentials" to credentials, "active" to true), headers),
                            Map::class.java,
                        ).body
                        ?.get("data") as? Map<*, *> ?: existing[0]
                logger.info("Updated existing Slack integration: ${integration["identifier"]}")

                // Delete any extra integrations beyond the first
                existing.drop(1).forEach { old ->
                    restTemplate.exchange(
                        "$integrationsUrl/${old["_id"]}",
                        HttpMethod.DELETE,
                        HttpEntity<Unit>(headers),
                        Map::class.java,
                    )
                    logger.info("Deleted duplicate Slack integration: ${old["identifier"]}")
                }
            } else {
                integration =
                    restTemplate
                        .exchange(
                            integrationsUrl,
                            HttpMethod.POST,
                            HttpEntity(
                                mapOf(
                                    "providerId" to "slack",
                                    "channel" to "chat",
                                    "name" to "Slack",
                                    "active" to true,
                                    "credentials" to credentials,
                                ),
                                headers,
                            ),
                            Map::class.java,
                        ).body
                        ?.get("data") as? Map<*, *> ?: return logger.error("Failed to create Slack integration")
                logger.info("Created Slack integration: ${integration["identifier"]}")
            }

            slackIntegrationIdentifier = integration["identifier"] as? String
        } catch (e: org.springframework.web.client.RestClientResponseException) {
            logger.error("Failed to provision Slack integration. Status: ${e.statusCode}, Body: ${e.responseBodyAsString}")
        } catch (e: Exception) {
            logger.error("Failed to provision Slack integration", e)
        }
    }

    /**
     * Resolves the Slack channel connection identifier lazily.
     *
     * Reuses any existing connection (all subscribers can reference the same connection since
     * the bot token is workspace-wide). If none exists, creates one for [subscriberId].
     * The result is cached in [slackConnectionIdentifier] for subsequent calls.
     */
    private fun resolveSlackConnectionIdentifier(subscriberId: String): String {
        slackConnectionIdentifier?.let { return it }

        val integrationId =
            slackIntegrationIdentifier
                ?: throw IllegalStateException("Slack integration not initialized")
        val baseUrl = getBaseUrl()
        val headers = createHeaders()
        val connectionsUrl = "$baseUrl/channel-connections"

        // Find any existing Slack connection — one connection serves all subscribers
        val existing =
            (
                restTemplate
                    .exchange(connectionsUrl, HttpMethod.GET, HttpEntity<Unit>(headers), Map::class.java)
                    .body
                    ?.get("data") as? List<Map<String, Any>> ?: emptyList()
            ).filter { it["providerId"] == "slack" }

        if (existing.isNotEmpty()) {
            val connId = existing[0]["identifier"] as? String ?: throw IllegalStateException("Connection has no identifier")
            slackConnectionIdentifier = connId
            logger.info("Reusing existing Slack connection $connId")
            return connId
        }

        // Create a new connection. subscriberId is required by the API.
        val workspaceId = slackWorkspaceId.ifBlank { "unknown" }
        val created =
            restTemplate
                .exchange(
                    connectionsUrl,
                    HttpMethod.POST,
                    HttpEntity(
                        mapOf(
                            "subscriberId" to subscriberId,
                            "integrationIdentifier" to integrationId,
                            "workspace" to mapOf("id" to workspaceId, "name" to slackWorkspaceName),
                            "auth" to mapOf("accessToken" to slackBotToken),
                        ),
                        headers,
                    ),
                    Map::class.java,
                ).body
                ?.get("data") as? Map<*, *>
                ?: throw IllegalStateException("Failed to create Slack channel connection")

        val connId = created["identifier"] as? String ?: throw IllegalStateException("Created connection has no identifier")
        slackConnectionIdentifier = connId
        logger.info("Created Slack connection $connId for subscriber $subscriberId (workspace=$workspaceId)")
        return connId
    }

    /**
     * Creates a Slack channel endpoint for the given ID.
     * Detects whether it is a user DM (IDs starting with U/W → slack_user) or a Slack
     * channel/group (IDs starting with C/G → slack_channel) and creates the appropriate
     * endpoint type. Deletes stale endpoints first so delivery always uses the current
     * integration and connection.
     */
    fun createSlackEndpoint(subscriberId: String) {
        ensureSubscriberExists(subscriberId)

        val integrationId =
            slackIntegrationIdentifier
                ?: throw IllegalStateException("Slack integration not initialized — cannot create channel endpoint")
        val connectionId = resolveSlackConnectionIdentifier(subscriberId)

        val isSlackChannel = subscriberId.startsWith("C") || subscriberId.startsWith("G")
        val endpointType = if (isSlackChannel) "slack_channel" else "slack_user"
        val endpointBody = if (isSlackChannel) mapOf("channelId" to subscriberId) else mapOf("userId" to subscriberId)

        val baseUrl = getBaseUrl()
        val headers = createHeaders()

        // Fetch all existing endpoints for this subscriber
        val existing =
            try {
                (
                    restTemplate
                        .exchange(
                            "$baseUrl/channel-endpoints?subscriberId=$subscriberId",
                            HttpMethod.GET,
                            HttpEntity<Unit>(headers),
                            Map::class.java,
                        ).body
                        ?.get("data") as? List<Map<String, Any>> ?: emptyList()
                )
            } catch (e: Exception) {
                logger.warn("Could not fetch channel endpoints for $subscriberId", e)
                emptyList()
            }

        // If a valid endpoint already exists, nothing to do
        val alreadyValid =
            existing.any {
                it["integrationIdentifier"] == integrationId && it["connectionIdentifier"] != null
            }
        if (alreadyValid) {
            logger.debug("Slack endpoint already up-to-date for $subscriberId, skipping")
            return
        }

        // Delete stale endpoints (wrong integration or missing connection)
        try {
            existing.forEach { endpoint ->
                restTemplate.exchange(
                    "$baseUrl/channel-endpoints/${endpoint["identifier"]}",
                    HttpMethod.DELETE,
                    HttpEntity<Unit>(headers),
                    Map::class.java,
                )
                logger.debug("Deleted stale channel endpoint: ${endpoint["identifier"]}")
            }
        } catch (e: Exception) {
            logger.warn("Could not clean up stale channel endpoints for $subscriberId", e)
        }

        val payload =
            mapOf(
                "subscriberId" to subscriberId,
                "integrationIdentifier" to integrationId,
                "connectionIdentifier" to connectionId,
                "type" to endpointType,
                "endpoint" to endpointBody,
            )
        restTemplate.exchange(
            "$baseUrl/channel-endpoints",
            HttpMethod.POST,
            HttpEntity(payload, headers),
            Map::class.java,
        )
        logger.info("Created Slack $endpointType endpoint for $subscriberId (connection=$connectionId)")
    }

    /**
     * Upserts the subscriber in Novu so it exists when a workflow is triggered.
     * Slack credentials (webhookUrl) are set automatically by the OAuth callback — not here.
     */
    private fun ensureSubscriberExists(subscriberId: String) {
        if (subscriberId in knownSubscribers) return
        try {
            restTemplate.exchange(
                "${getBaseUrl()}/subscribers",
                HttpMethod.POST,
                HttpEntity(mapOf("subscriberId" to subscriberId), createHeaders()),
                Map::class.java,
            )
            knownSubscribers.add(subscriberId)
            logger.debug("Upserted subscriber $subscriberId in Novu")
        } catch (e: Exception) {
            logger.warn("Could not upsert subscriber $subscriberId in Novu", e)
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
