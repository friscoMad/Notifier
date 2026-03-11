package com.notifier.router.api.service

import co.novu.api.events.requests.TriggerEventRequest
import co.novu.common.base.Novu
import co.novu.common.base.NovuConfig
import com.fasterxml.jackson.databind.ObjectMapper
import com.notifier.router.api.config.LoggingInterceptor
import com.notifier.router.api.config.NovuApiProperties
import com.notifier.router.api.config.NovuResendProperties
import com.notifier.router.api.config.NovuSesProperties
import com.notifier.router.api.config.NovuSlackProperties
import com.notifier.router.api.novu.NovuApiClient
import com.notifier.router.api.novu.NovuChannelConnection
import com.notifier.router.api.novu.NovuChannelEndpoint
import com.notifier.router.api.novu.NovuConnectionAuth
import com.notifier.router.api.novu.NovuDigestMetadata
import com.notifier.router.api.novu.NovuIntegration
import com.notifier.router.api.novu.NovuResendCredentials
import com.notifier.router.api.novu.NovuSesCredentials
import com.notifier.router.api.novu.NovuSlackCredentials
import com.notifier.router.api.novu.NovuStepTemplate
import com.notifier.router.api.novu.NovuSubscriber
import com.notifier.router.api.novu.NovuWorkflow
import com.notifier.router.api.novu.NovuWorkflowStep
import com.notifier.router.api.novu.NovuWorkspace
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import retrofit2.Retrofit

// All ensure*/trigger*/cleanup methods belong together as the single Novu integration surface.
@Suppress("LargeClass")
@Service
class NovuService(
    private val novuApiProps: NovuApiProperties,
    private val slackProps: NovuSlackProperties,
    private val sesProps: NovuSesProperties,
    private val resendProps: NovuResendProperties,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(NovuService::class.java)
    private lateinit var novuClient: Novu
    private var novuApiClient: NovuApiClient? = null
    private val restTemplate =
        RestTemplate(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())).apply {
            interceptors = listOf(LoggingInterceptor())
            messageConverters.filterIsInstance<MappingJackson2HttpMessageConverter>()
                .forEach { it.objectMapper.findAndRegisterModules() }
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
     * The Novu integration identifier for SES (e.g. "ses-abc123").
     * Set by [ensureSesIntegrationExists].
     */
    private var sesIntegrationIdentifier: String? = null

    /**
     * The Novu integration identifier for Resend (e.g. "resend-abc123").
     * Set by [ensureResendIntegrationExists].
     */
    private var resendIntegrationIdentifier: String? = null

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
        if (novuApiProps.key == "not-set" || novuApiProps.key.isBlank()) {
            logger.warn("Novu API Key is not set or empty. NovuService will log triggers instead.")
        } else {
            logger.info("Initializing Novu client")
            novuClient = Novu(novuApiProps.key)

            // If a custom API URL is configured, override the SDK's internal baseUrl
            // using reflection since the Novu Java SDK v1.6.0 does not expose a setter.
            if (novuApiProps.url.isNotBlank()) {
                overrideBaseUrl(novuApiProps.url)
            }

            novuApiClient = NovuApiClient(restTemplate, getBaseUrl(), novuApiProps.key, objectMapper)
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

        subscriberIds.forEach { subscriberId ->
            try {
                ensureSubscriberExists(subscriberId)
            } catch (e: org.springframework.web.client.RestClientException) {
                // Pre-registration is best-effort — subscriber may already exist in Novu.
                // Log and continue; the trigger attempt below will reveal if they truly don't exist.
                logger.warn("Could not pre-register subscriber $subscriberId in Novu, attempting trigger anyway", e)
            }
        }

        val novuIdentifier = workflowRegistry[workflowId] ?: workflowId
        if (novuIdentifier != workflowId) {
            logger.debug("Resolved typeKey $workflowId -> Novu identifier $novuIdentifier")
        }
        val request = TriggerEventRequest()
        request.name = novuIdentifier
        request.to = subscriberIds
        val enrichedPayload = payload.toMutableMap()
        if ("subject" !in enrichedPayload) {
            enrichedPayload["subject"] = enrichedPayload["title"] ?: "Notification"
        }
        request.payload = enrichedPayload

        val response = novuClient.triggerEvent(request)
        logger.info("Novu trigger response: ${response.data}")
    }

    /**
     * Triggers the channel-specific workflow variant for the given channel
     * (e.g. `pr_created_chat` for Slack, `pr_created_email` for email).
     */
    fun triggerChannelWorkflow(
        typeKey: String,
        channel: String,
        subscriberIds: List<String>,
        payload: Map<String, Any>,
    ) {
        val workflowKey = "${typeKey}_$channel"
        if (!this::novuClient.isInitialized) {
            logger.info("MOCK Trigger Channel Workflow: $workflowKey to $subscriberIds")
            return
        }

        subscriberIds.forEach { subscriberId ->
            try {
                ensureSubscriberExists(subscriberId)
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.warn("Could not pre-register subscriber $subscriberId in Novu, attempting trigger anyway", e)
            }
        }

        val novuIdentifier = workflowRegistry[workflowKey] ?: workflowKey
        val request = TriggerEventRequest()
        request.name = novuIdentifier
        request.to = subscriberIds
        val enrichedPayload = payload.toMutableMap()
        if ("subject" !in enrichedPayload) {
            enrichedPayload["subject"] = enrichedPayload["title"] ?: "Notification"
        }
        request.payload = enrichedPayload

        val response = novuClient.triggerEvent(request)
        logger.info("Novu channel trigger ($workflowKey) response: ${response.data}")
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

        novuApiClient!!.upsertSubscriber(NovuSubscriber(subscriberId = subscriberId))
        logger.info(
            "Successfully synced subscriber $subscriberId to Novu. Channels: $channels, Config: $channelConfig",
        )
    }

    fun ensureWorkflowExists(
        key: String,
        name: String,
    ) {
        if (!this::novuClient.isInitialized) {
            logger.warn("Novu client not initialized, skipping workflow check for $key")
            return
        }

        val workflows = novuApiClient!!.listWorkflows()
        val existing = workflows.find { it.name == key }

        if (existing != null) {
            val novuIdentifier = existing.triggers?.firstOrNull()?.identifier
            if (novuIdentifier != null) workflowRegistry[key] = novuIdentifier

            val steps = existing.steps ?: emptyList()
            val hasChatStep = steps.any { it.template.type == "chat" }
            val hasEmailStep = steps.any { it.template.type == "email" }
            val hasPushStep = steps.any { it.template.type == "push" }
            val hasWrongTemplate = steps.any {
                when (it.template.type) {
                    "chat" -> it.template.content != "{{{content}}}"
                    "email" ->
                        it.template.content != "{{{content}}}" ||
                            it.template.contentType != "customHtml" ||
                            it.template.subject.isNullOrBlank()
                    else -> false
                }
            }

            val needsPatching = !hasChatStep || !hasEmailStep || hasPushStep || hasWrongTemplate
            if (needsPatching) {
                patchWorkflow(key, existing, steps, hasChatStep, hasEmailStep)
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
                        NovuWorkflowStep(NovuStepTemplate("chat", "{{{content}}}")),
                        NovuWorkflowStep(NovuStepTemplate("email", "{{{content}}}", "customHtml", "{{subject}}")),
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
    }

    /**
     * Ensures a single-channel workflow variant exists in Novu (e.g. `pr_created_chat`).
     * Each variant has only one step matching its channel type.
     */
    /**
     * Fetches all existing workflow names from Novu in a single call.
     * Used by the seeder to avoid repeated API calls per workflow.
     */
    fun listExistingWorkflowNames(): Map<String, NovuWorkflow> {
        if (!this::novuClient.isInitialized || novuApiClient == null) return emptyMap()
        return novuApiClient!!.listWorkflows().associateBy { it.name ?: "" }
    }

    fun ensureChannelWorkflowExists(
        typeKey: String,
        name: String,
        channel: String,
        existingNames: Map<String, NovuWorkflow> = emptyMap(),
    ) {
        if (!this::novuClient.isInitialized) {
            logger.warn("Novu client not initialized, skipping workflow check for ${typeKey}_$channel")
            return
        }

        val workflowKey = "${typeKey}_$channel"
        val existing = existingNames[workflowKey]

        if (existing != null) {
            val novuIdentifier = existing.triggers?.firstOrNull()?.identifier
            if (novuIdentifier != null) workflowRegistry[workflowKey] = novuIdentifier
            val steps = existing.steps ?: emptyList()
            val hasWrongTemplate = steps.any {
                when (it.template.type) {
                    "chat" -> it.template.content != "{{{content}}}"
                    "email" ->
                        it.template.content != "{{{content}}}" ||
                            it.template.contentType != "customHtml" ||
                            it.template.subject.isNullOrBlank()
                    else -> false
                }
            }
            if (hasWrongTemplate) {
                val fixedSteps = steps.map {
                    when (it.template.type) {
                        "chat" -> NovuWorkflowStep(NovuStepTemplate("chat", "{{{content}}}"))
                        "email" -> NovuWorkflowStep(
                            NovuStepTemplate("email", "{{{content}}}", "customHtml", "{{subject}}")
                        )
                        else -> it
                    }
                }
                novuApiClient!!.updateWorkflow(existing._id!!, existing.copy(steps = fixedSteps))
                logger.info("Patched channel workflow $workflowKey: corrected template")
            } else {
                logger.info("Registered existing channel workflow: $workflowKey -> $novuIdentifier")
            }
            return
        }

        val groups = novuApiClient!!.listNotificationGroups()
        val groupId =
            (groups.firstOrNull()?.get("_id") as? String)
                ?: return logger.error("Could not find any notification groups in Novu")

        val step = when (channel) {
            "email" -> NovuWorkflowStep(NovuStepTemplate("email", "{{{content}}}", "customHtml", "{{subject}}"))
            else -> NovuWorkflowStep(NovuStepTemplate(channel, "{{{content}}}"))
        }

        val created =
            novuApiClient!!.createWorkflow(
                NovuWorkflow(
                    name = workflowKey,
                    notificationGroupId = groupId,
                    steps = listOf(step),
                    active = true,
                    draft = false,
                    critical = false,
                ),
            )
        val novuIdentifier = created.triggers?.firstOrNull()?.identifier
        if (novuIdentifier != null) {
            workflowRegistry[workflowKey] = novuIdentifier
            logger.info("Provisioned channel workflow: $workflowKey -> $novuIdentifier ($name)")
        } else {
            logger.warn("Created channel workflow $workflowKey but could not extract trigger identifier")
        }
    }

    private fun patchWorkflow(
        key: String,
        existing: NovuWorkflow,
        steps: List<NovuWorkflowStep>,
        hasChatStep: Boolean,
        hasEmailStep: Boolean,
    ) {
        logger.info("Workflow $key needs patching — ensuring chat and email steps with correct templates")
        val cleanSteps =
            steps
                .filter { it.template.type != "push" }
                .map {
                    when (it.template.type) {
                        "chat" -> NovuWorkflowStep(NovuStepTemplate("chat", "{{{content}}}"))
                        "email" -> NovuWorkflowStep(
                            NovuStepTemplate("email", "{{{content}}}", "customHtml", "{{subject}}")
                        )
                        else -> it
                    }
                }
                .let { if (!hasChatStep) it + NovuWorkflowStep(NovuStepTemplate("chat", "{{{content}}}")) else it }
                .let {
                    if (!hasEmailStep) {
                        it + NovuWorkflowStep(NovuStepTemplate("email", "{{{content}}}", "customHtml", "{{subject}}"))
                    } else {
                        it
                    }
                }
        novuApiClient!!.updateWorkflow(existing._id!!, existing.copy(steps = cleanSteps))
        logger.info("Patched workflow $key: correct templates, removed push, ensured chat and email steps")
    }

    fun ensureSlackIntegrationExists() {
        if (!this::novuClient.isInitialized) {
            logger.warn("Novu client not initialized, skipping Slack integration check")
            return
        }

        if (slackProps.clientId.isBlank() || slackProps.clientSecret.isBlank() || slackProps.applicationId.isBlank()) {
            logger.warn("novu.slack credentials not configured — skipping Slack integration setup")
            return
        }

        val credentials = NovuSlackCredentials(
            slackProps.clientId,
            slackProps.clientSecret,
            slackProps.applicationId,
            slackProps.botToken,
        )
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
                            credentials = credentials
                        ),
                    )
                logger.info("Created Slack integration: ${created.identifier}")
                created
            }
        slackIntegrationIdentifier = integration.identifier
    }

    fun ensureSesIntegrationExists() {
        if (!this::novuClient.isInitialized) {
            logger.warn("Novu client not initialized, skipping SES integration check")
            return
        }

        if (sesProps.accessKeyId.isBlank() || sesProps.secretAccessKey.isBlank() || sesProps.from.isBlank()) {
            logger.warn("novu.ses credentials not configured — skipping SES integration setup")
            return
        }

        val credentials = NovuSesCredentials(
            apiKey = sesProps.accessKeyId,
            secretKey = sesProps.secretAccessKey,
            region = sesProps.region,
            from = sesProps.from,
            senderName = sesProps.senderName,
        )
        val allIntegrations = novuApiClient!!.listIntegrations()
        val sesIntegrations = allIntegrations.filter { it.providerId == "ses" && it.channel == "email" }

        val integration =
            if (sesIntegrations.isNotEmpty()) {
                sesIntegrations.drop(1).forEach {
                    novuApiClient!!.deleteIntegration(it._id!!)
                    logger.info("Deleted duplicate SES integration: ${it.identifier}")
                }
                val updated = novuApiClient!!.updateIntegration(
                    sesIntegrations[0]._id!!,
                    credentials,
                    active = true,
                )
                logger.info("Updated existing SES integration: ${updated.identifier}")
                updated
            } else {
                val created =
                    novuApiClient!!.createIntegration(
                        NovuIntegration(
                            providerId = "ses",
                            channel = "email",
                            name = "SES",
                            active = true,
                            credentials = credentials
                        ),
                    )
                logger.info("Created SES integration: ${created.identifier}")
                created
            }
        sesIntegrationIdentifier = integration.identifier
    }

    fun ensureResendIntegrationExists() {
        if (!this::novuClient.isInitialized) {
            logger.warn("Novu client not initialized, skipping Resend integration check")
            return
        }

        if (resendProps.apiKey.isBlank() || resendProps.from.isBlank()) {
            logger.warn("novu.resend credentials not configured — skipping Resend integration setup")
            return
        }

        val credentials = NovuResendCredentials(
            apiKey = resendProps.apiKey,
            from = resendProps.from,
            senderName = resendProps.senderName,
        )
        val allIntegrations = novuApiClient!!.listIntegrations()
        val resendIntegrations = allIntegrations.filter { it.providerId == "resend" && it.channel == "email" }

        val integration =
            if (resendIntegrations.isNotEmpty()) {
                resendIntegrations.drop(1).forEach {
                    novuApiClient!!.deleteIntegration(it._id!!)
                    logger.info("Deleted duplicate Resend integration: ${it.identifier}")
                }
                val updated = novuApiClient!!.updateIntegration(resendIntegrations[0]._id!!, credentials, active = true)
                logger.info("Updated existing Resend integration: ${updated.identifier}")
                updated
            } else {
                val created = novuApiClient!!.createIntegration(
                    NovuIntegration(
                        providerId = "resend",
                        channel = "email",
                        name = "Resend",
                        active = true,
                        credentials = credentials
                    ),
                )
                logger.info("Created Resend integration: ${created.identifier}")
                created
            }
        resendIntegrationIdentifier = integration.identifier
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
        val reused = existing.firstOrNull()?.identifier
        if (reused != null) {
            slackConnectionIdentifier = reused
            logger.debug("Reusing existing Slack channel connection $reused")
            return reused
        }

        val workspaceId = slackProps.workspaceId.ifBlank { "unknown" }
        val connId =
            novuApiClient!!
                .createChannelConnection(
                    NovuChannelConnection(
                        subscriberId = subscriberId,
                        integrationIdentifier = integrationId,
                        workspace = NovuWorkspace(workspaceId, slackProps.workspaceName),
                        auth = NovuConnectionAuth(slackProps.botToken),
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
    fun createSlackEndpoint(subscriberId: String, email: String? = null) {
        ensureSubscriberExists(subscriberId, email)

        val integrationId =
            slackIntegrationIdentifier
                ?: error("Slack integration not initialized — cannot create channel endpoint")
        val connectionId = resolveSlackConnectionIdentifier(subscriberId)

        val isChannel = subscriberId.startsWith("C") || subscriberId.startsWith("G")
        val existing =
            try {
                novuApiClient!!.listChannelEndpoints(subscriberId)
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.warn("Could not fetch channel endpoints for $subscriberId", e)
                emptyList()
            }

        val alreadyValid = existing.any { ep ->
            ep.integrationIdentifier == integrationId &&
                ep.connectionIdentifier != null &&
                (!isChannel || !ep.endpoint?.get("token").isNullOrBlank())
        }
        if (alreadyValid) {
            logger.debug("Slack endpoint already up-to-date for $subscriberId, skipping")
            return
        }

        existing.forEach { ep ->
            try {
                novuApiClient!!.deleteChannelEndpoint(ep.identifier!!)
            } catch (e: org.springframework.web.client.RestClientException) {
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
    private fun ensureSubscriberExists(subscriberId: String, email: String? = null) {
        if (subscriberId in knownSubscribers && email == null) return
        novuApiClient!!.upsertSubscriber(NovuSubscriber(subscriberId = subscriberId, email = email))
        knownSubscribers.add(subscriberId)
        logger.debug("Upserted subscriber $subscriberId in Novu${if (email != null) " (with email)" else ""}")
    }

    /**
     * Deletes all Novu channel endpoints for the given subscriber (by Slack ID).
     * Used during full-reset seeding to clean up Novu state alongside the DB wipe.
     */
    fun cleanupSubscriber(slackId: String) {
        val client = novuApiClient ?: return
        try {
            client.listChannelEndpoints(slackId)
                .mapNotNull { it.identifier }
                .forEach { identifier ->
                    client.deleteChannelEndpoint(identifier)
                    logger.info("Deleted Novu channel endpoint $identifier for subscriber $slackId")
                }
            knownSubscribers.remove(slackId)
        } catch (e: org.springframework.web.client.RestClientException) {
            logger.warn("Failed to clean up Novu data for subscriber $slackId: ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun listInAppNotifications(subscriberId: String): List<Map<String, Any?>> {
        if (novuApiClient == null) return emptyList()
        return try {
            novuApiClient!!.listNotifications(subscriberId).mapNotNull { raw ->
                val template = raw["template"] as? Map<String, Any?>
                val templateName = template?.get("name") as? String ?: ""

                // Only show in_app workflow notifications to avoid duplicates
                if (!templateName.endsWith("_in_app")) return@mapNotNull null

                val payload = raw["payload"] as? Map<String, Any?> ?: emptyMap()
                val typeKey = templateName.removeSuffix("_in_app")
                val content = payload["content"] as? String ?: "Notification: $typeKey"

                mapOf(
                    "typeKey" to typeKey,
                    "content" to content,
                    "payload" to payload,
                    "channels" to (raw["channels"] ?: emptyList<String>()),
                    "createdAt" to raw["createdAt"],
                )
            }
        } catch (e: org.springframework.web.client.RestClientException) {
            logger.warn("Failed to fetch notifications for $subscriberId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Ensures a digest-variant channel workflow exists in Novu (e.g. `pr_created_chat_digest_1d`).
     * The workflow has two steps: a digest step (accumulates events over a time window) followed
     * by the delivery step for the given channel.
     *
     * @param intervalKey either `"1d"` or `"1w"` — determines the Novu digest window.
     */
    fun ensureDigestChannelWorkflowExists(
        typeKey: String,
        name: String,
        channel: String,
        intervalKey: String,
        existingNames: Map<String, NovuWorkflow> = emptyMap(),
    ) {
        if (!this::novuClient.isInitialized) {
            logger.warn(
                "Novu client not initialized, skipping digest workflow check for ${typeKey}_${channel}_digest_$intervalKey"
            )
            return
        }

        val workflowKey = "${typeKey}_${channel}_digest_$intervalKey"
        val digestAmount = DIGEST_AMOUNT_BY_INTERVAL[intervalKey] ?: DIGEST_AMOUNT_24H

        val digestStep = NovuWorkflowStep(
            template = NovuStepTemplate(type = "digest"),
            metadata = NovuDigestMetadata(type = "regular", amount = digestAmount, unit = DIGEST_UNIT),
        )
        // Delivery step iterates over all events accumulated by the digest step.
        // Within {{#each step.events}}, payload fields are accessed directly (each item IS the payload).
        val deliveryStep = when (channel) {
            "email" -> NovuWorkflowStep(
                NovuStepTemplate(
                    "email",
                    DIGEST_EMAIL_TEMPLATE,
                    "customHtml",
                    "🔔 PR Digest — {{step.total_count}} pull request(s)",
                ),
            )
            else -> NovuWorkflowStep(NovuStepTemplate(channel, DIGEST_CHAT_TEMPLATE))
        }
        val steps = listOf(digestStep, deliveryStep)

        val existing = existingNames[workflowKey]
        if (existing != null) {
            val novuIdentifier = existing.triggers?.firstOrNull()?.identifier
            novuApiClient!!.updateWorkflow(existing._id!!, existing.copy(steps = steps))
            if (novuIdentifier != null) workflowRegistry[workflowKey] = novuIdentifier
            logger.info("Updated existing digest workflow: $workflowKey -> $novuIdentifier")
            return
        }

        val groups = novuApiClient!!.listNotificationGroups()
        val groupId =
            (groups.firstOrNull()?.get("_id") as? String)
                ?: return logger.error("Could not find any notification groups in Novu")

        val created =
            novuApiClient!!.createWorkflow(
                NovuWorkflow(
                    name = workflowKey,
                    notificationGroupId = groupId,
                    steps = steps,
                    active = true,
                    draft = false,
                    critical = false,
                ),
            )
        val novuIdentifier = created.triggers?.firstOrNull()?.identifier
        if (novuIdentifier != null) {
            workflowRegistry[workflowKey] = novuIdentifier
            logger.info("Provisioned digest workflow: $workflowKey -> $novuIdentifier ($name)")
        } else {
            logger.warn("Created digest workflow $workflowKey but could not extract trigger identifier")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun listAllInAppNotifications(): List<Map<String, Any?>> {
        if (novuApiClient == null) return emptyList()
        return try {
            novuApiClient!!.listAllNotifications().mapNotNull { raw ->
                val template = raw["template"] as? Map<String, Any?>
                val templateName = template?.get("name") as? String ?: ""

                if (!templateName.endsWith("_in_app")) return@mapNotNull null

                val payload = raw["payload"] as? Map<String, Any?> ?: emptyMap()
                val typeKey = templateName.removeSuffix("_in_app")
                val content = payload["content"] as? String ?: "Notification: $typeKey"
                val subscriber = raw["subscriber"] as? Map<String, Any?>

                mapOf(
                    "typeKey" to typeKey,
                    "content" to content,
                    "payload" to payload,
                    "channels" to (raw["channels"] ?: emptyList<String>()),
                    "createdAt" to raw["createdAt"],
                    "subscriberId" to (subscriber?.get("subscriberId") ?: ""),
                )
            }
        } catch (e: org.springframework.web.client.RestClientException) {
            logger.warn("Failed to fetch all notifications: ${e.message}")
            emptyList()
        }
    }

    private fun getBaseUrl() = if (novuApiProps.url.isNotBlank()) novuApiProps.url else "https://api.novu.co/v1"

    companion object {
        private const val DIGEST_AMOUNT_12H = 1
        private const val DIGEST_AMOUNT_24H = 2
        private const val DIGEST_UNIT = "minutes"
        private val DIGEST_AMOUNT_BY_INTERVAL = mapOf("1d" to DIGEST_AMOUNT_12H, "1w" to DIGEST_AMOUNT_24H)

        // Novu v3 legacy workflow template context exposes digest data under `step`:
        //   step.events      — array of accumulated trigger payloads (each item IS the payload)
        //   step.total_count — total number of digested events
        // Within {{#each step.events}}, payload fields are accessed directly (no `payload.` prefix).
        private const val DIGEST_CHAT_TEMPLATE =
            "📋 *PR Digest — {{step.total_count}} new PR(s)*\n" +
                "{{#each step.events}}\n" +
                "─────────────────────\n" +
                "{{{content}}}\n" +
                "{{/each}}"

        private const val DIGEST_EMAIL_TEMPLATE =
            "<div style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;" +
                "max-width:600px;margin:0 auto;color:#24292f\">" +
                "<div style=\"background:#f6f8fa;border:1px solid #d0d7de;border-radius:6px;" +
                "padding:16px 20px;margin-bottom:16px\">" +
                "<h2 style=\"margin:0;font-size:18px\">🔔 PR Digest</h2>" +
                "<p style=\"margin:6px 0 0;color:#57606a;font-size:14px\">" +
                "{{step.total_count}} pull request(s) to review</p>" +
                "</div>" +
                "{{#each step.events}}" +
                "<div style=\"border:1px solid #d0d7de;border-radius:6px;padding:16px;margin-bottom:12px\">" +
                "{{{content}}}" +
                "</div>" +
                "{{/each}}" +
                "</div>"
    }
}
