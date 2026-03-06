package com.notifier.router.api.service

import co.novu.api.events.requests.TriggerEventRequest
import co.novu.common.base.Novu
import co.novu.common.base.NovuConfig
import com.notifier.router.api.config.LoggingInterceptor
import com.notifier.router.api.novu.NovuApiClient
import com.notifier.router.api.novu.NovuChannelConnection
import com.notifier.router.api.novu.NovuChannelEndpoint
import com.notifier.router.api.novu.NovuConnectionAuth
import com.notifier.router.api.novu.NovuIntegration
import com.notifier.router.api.novu.NovuSlackCredentials
import com.notifier.router.api.novu.NovuStepTemplate
import com.notifier.router.api.novu.NovuSubscriber
import com.notifier.router.api.novu.NovuWorkflow
import com.notifier.router.api.novu.NovuWorkflowStep
import com.notifier.router.api.novu.NovuWorkspace
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import retrofit2.Retrofit

@Service
@Suppress("LongParameterList")
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
    private var novuApiClient: NovuApiClient? = null
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

            novuApiClient = NovuApiClient(restTemplate, getBaseUrl(), apiKey)
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
                } catch (@Suppress("SwallowedException") e: NoSuchFieldException) {
                    // Some Retrofit handlers have different internal structure — skip silently
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
            novuApiClient!!.upsertSubscriber(NovuSubscriber(subscriberId = subscriberId))
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
            val workflows = novuApiClient!!.listWorkflows()
            val existing = workflows.find { it.name == key }

            if (existing != null) {
                val novuIdentifier = existing.triggers?.firstOrNull()?.identifier
                if (novuIdentifier != null) workflowRegistry[key] = novuIdentifier

                val steps = existing.steps ?: emptyList()
                val hasChatStep = steps.any { it.template.type == "chat" }
                val hasPushStep = steps.any { it.template.type == "push" }

                if (!hasChatStep || hasPushStep) {
                    logger.info("Workflow $key needs patching (hasChatStep=$hasChatStep, hasPushStep=$hasPushStep)")
                    val cleanSteps =
                        steps
                            .filter { it.template.type != "push" }
                            .let { if (!hasChatStep) it + NovuWorkflowStep(NovuStepTemplate("chat", "{{content}}")) else it }
                    novuApiClient!!.updateWorkflow(existing._id!!, existing.copy(steps = cleanSteps))
                    logger.info("Patched workflow $key: removed push, ensured chat step")
                } else {
                    logger.info("Registered existing Novu workflow: $key -> $novuIdentifier")
                }
                return
            }

            val groups = novuApiClient!!.listNotificationGroups()
            val groupId =
                (groups.firstOrNull()?.get("_id") as? String)
                    ?: return logger.error("Could not find any notification groups in Novu")

            val created =
                novuApiClient!!.createWorkflow(
                    NovuWorkflow(
                        name = key,
                        notificationGroupId = groupId,
                        steps =
                        listOf(
                            NovuWorkflowStep(NovuStepTemplate("in_app", "{{content}}")),
                            NovuWorkflowStep(NovuStepTemplate("chat", "{{content}}")),
                        ),
                        active = true,
                        draft = false,
                        critical = false,
                    ),
                )
            val novuIdentifier = created.triggers?.firstOrNull()?.identifier
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
            val credentials = NovuSlackCredentials(slackClientId, slackClientSecret, slackApplicationId, slackBotToken)
            val allIntegrations = novuApiClient!!.listIntegrations()
            val slackIntegrations = allIntegrations.filter { it.providerId == "slack" && it.channel == "chat" }

            val integration =
                if (slackIntegrations.isNotEmpty()) {
                    slackIntegrations.drop(1).forEach {
                        novuApiClient!!.deleteIntegration(it._id!!)
                        logger.info("Deleted duplicate Slack integration: ${it.identifier}")
                    }
                    val updated = novuApiClient!!.updateIntegration(
                        slackIntegrations[0]._id!!,
                        credentials,
                        active = true
                    )
                    logger.info("Updated existing Slack integration: ${updated.identifier}")
                    updated
                } else {
                    val created =
                        novuApiClient!!.createIntegration(
                            NovuIntegration(
                                providerId = "slack",
                                channel = "chat",
                                name = "Slack",
                                active = true,
                                credentials = credentials,
                            ),
                        )
                    logger.info("Created Slack integration: ${created.identifier}")
                    created
                }
            slackIntegrationIdentifier = integration.identifier
        } catch (e: org.springframework.web.client.RestClientResponseException) {
            logger.error(
                "Failed to provision Slack integration. Status: ${e.statusCode}, Body: ${e.responseBodyAsString}"
            )
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
                ?: error("Slack integration not initialized")

        val existing = novuApiClient!!.listChannelConnections().filter { it.providerId == "slack" }
        if (existing.isNotEmpty()) {
            val connId = existing[0].identifier!!
            slackConnectionIdentifier = connId
            logger.info("Reusing existing Slack connection $connId")
            return connId
        }

        val workspaceId = slackWorkspaceId.ifBlank { "unknown" }
        val connId =
            novuApiClient!!
                .createChannelConnection(
                    NovuChannelConnection(
                        subscriberId = subscriberId,
                        integrationIdentifier = integrationId,
                        workspace = NovuWorkspace(workspaceId, slackWorkspaceName),
                        auth = NovuConnectionAuth(slackBotToken),
                    ),
                ).identifier!!
        slackConnectionIdentifier = connId
        logger.info("Created Slack connection $connId")
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
                ?: error("Slack integration not initialized — cannot create channel endpoint")
        val connectionId = resolveSlackConnectionIdentifier(subscriberId)

        val isChannel = subscriberId.startsWith("C") || subscriberId.startsWith("G")
        val existing =
            try {
                novuApiClient!!.listChannelEndpoints(subscriberId)
            } catch (e: Exception) {
                logger.warn("Could not fetch channel endpoints for $subscriberId", e)
                emptyList()
            }

        val alreadyValid = existing.any { it.integrationIdentifier == integrationId && it.connectionIdentifier != null }
        if (alreadyValid) {
            logger.debug("Slack endpoint already up-to-date for $subscriberId, skipping")
            return
        }

        existing.forEach { ep ->
            try {
                novuApiClient!!.deleteChannelEndpoint(ep.identifier!!)
            } catch (e: Exception) {
                logger.warn("Could not delete stale endpoint ${ep.identifier}", e)
            }
        }

        val endpointType = if (isChannel) "slack_channel" else "slack_user"
        val endpointBody = if (isChannel) mapOf("channelId" to subscriberId) else mapOf("userId" to subscriberId)
        novuApiClient!!.createChannelEndpoint(
            NovuChannelEndpoint(
                subscriberId = subscriberId,
                integrationIdentifier = integrationId,
                connectionIdentifier = connectionId,
                type = endpointType,
                endpoint = endpointBody,
            ),
        )
        logger.info(
            "Created Slack ${if (isChannel) "slack_channel" else "slack_user"} endpoint for $subscriberId (connection=$connectionId)",
        )
    }

    /**
     * Upserts the subscriber in Novu so it exists when a workflow is triggered.
     * Slack credentials (webhookUrl) are set automatically by the OAuth callback — not here.
     */
    private fun ensureSubscriberExists(subscriberId: String) {
        if (subscriberId in knownSubscribers) return
        try {
            novuApiClient!!.upsertSubscriber(NovuSubscriber(subscriberId = subscriberId))
            knownSubscribers.add(subscriberId)
            logger.debug("Upserted subscriber $subscriberId in Novu")
        } catch (e: Exception) {
            logger.warn("Could not upsert subscriber $subscriberId in Novu", e)
        }
    }

    private fun getBaseUrl() = if (apiUrl.isNotBlank()) apiUrl else "https://api.novu.co/v1"
}
