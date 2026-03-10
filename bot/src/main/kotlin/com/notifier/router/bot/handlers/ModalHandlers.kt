package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.notifier.router.bot.service.ChannelSubscribeResult
import com.notifier.router.bot.service.SubscribeResult
import com.notifier.router.bot.service.SubscriptionService
import com.notifier.router.bot.view.ModalViewBuilder
import com.notifier.router.bot.view.ModalViewBuilder.buildDynamicSubscriptionModal
import com.notifier.router.common.domain.Filter
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.context.builtin.ViewSubmissionContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest
import com.slack.api.bolt.response.Response
import com.slack.api.methods.request.views.ViewsUpdateRequest
import com.slack.api.model.kotlin_extension.block.withBlocks
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ModalHandlers(
    private val app: App,
    private val apiClient: RouterApiClient,
    private val subscriptionService: SubscriptionService,
) {
    private val logger = LoggerFactory.getLogger(ModalHandlers::class.java)

    @PostConstruct
    fun registerHandlers() {
        app.blockAction("type_select") { req: BlockActionRequest, ctx: ActionContext ->
            handleTypeSelection(req, ctx)
        }

        app.viewSubmission("create_subscription_modal") {
                req: ViewSubmissionRequest,
                ctx: ViewSubmissionContext,
            ->
            handleSubscriptionSubmission(req, ctx)
        }
    }

    private fun handleTypeSelection(
        req: BlockActionRequest,
        ctx: ActionContext,
    ): Response {
        val selectedType =
            req.payload.actions[0]
                .selectedOption.value
        logger.info("User selected notification type: $selectedType")

        // Preserve channel context from the current view's metadata
        val meta = ModalViewBuilder.decodeMetadata(req.payload.view.privateMetadata)

        val availableTypes =
            try {
                apiClient.getNotificationTypes()
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.error("Failed to fetch notification types for modal update", e)
                return ctx.ack()
            }
        val filters =
            try {
                apiClient.getFiltersForType(selectedType)
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.warn("Failed to fetch filters for $selectedType, showing modal without filters", e)
                emptyList()
            }
        val updateView =
            buildDynamicSubscriptionModal(
                selectedTypeKey = selectedType,
                availableTypes = availableTypes,
                filters = filters,
                channelId = meta.channelId,
                channelName = meta.channelName,
            )

        ctx
            .client()
            .viewsUpdate(
                ViewsUpdateRequest
                    .builder()
                    .viewId(req.payload.view.id)
                    .hash(req.payload.view.hash)
                    .view(updateView)
                    .build(),
            )

        return ctx.ack()
    }

    @Suppress("LongMethod")
    private fun handleSubscriptionSubmission(
        req: ViewSubmissionRequest,
        ctx: ViewSubmissionContext,
    ): Response {
        val meta = ModalViewBuilder.decodeMetadata(req.payload.view.privateMetadata)
        val typeKey = meta.typeKey
        if (typeKey.isBlank()) {
            return ctx.ackWithErrors(mapOf("type_block" to "You must select a notification type first."))
        }

        val stateValues = req.payload.view.state.values
        logger.info("Received view submission for type $typeKey")

        val availableTypes =
            try {
                apiClient.getNotificationTypes()
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.error("Failed to fetch notification types during modal submission", e)
                return ctx.ackWithErrors(mapOf("type_block" to "Service unavailable. Please try again."))
            }
        val typeId =
            availableTypes.find { it.key == typeKey }?.id
                ?: return ctx.ackWithErrors(mapOf("type_block" to "Invalid notification type selected."))

        val typeName = availableTypes.find { it.key == typeKey }?.name ?: typeKey

        // Parse selected delivery channels
        val selectedChannels =
            stateValues["channels_block"]
                ?.get("channels_checkboxes")
                ?.selectedOptions
                ?.map { it.value }
                ?: listOf("slack_dm")

        // Parse digest settings
        val digestValue = stateValues["digest_block"]?.get("digest_select")?.selectedOption?.value ?: "immediate"
        val channelConfig = mutableMapOf<String, Any>()
        if (digestValue != "immediate") {
            channelConfig["digest"] = true
            channelConfig["digestInterval"] = digestValue
        }

        // Parse dynamic filters
        val filterDefinitions =
            try {
                apiClient.getFiltersForType(typeKey)
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.warn("Failed to fetch filter definitions for $typeKey, submitting without filters", e)
                emptyList()
            }
        val extractedFilters = mutableListOf<Filter>()
        filterDefinitions.forEach { filterDef ->
            val fieldName = filterDef.field
            val inputText = stateValues["filter_block_$fieldName"]?.get("filter_input_$fieldName")?.value
            if (!inputText.isNullOrBlank()) {
                if (inputText.contains(",")) {
                    extractedFilters.add(
                        Filter(fieldName, "IN", inputText.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    )
                } else {
                    extractedFilters.add(Filter(fieldName, "EQ", inputText.trim()))
                }
            }
        }

        val slackId = req.payload.user.id

        // Separate DM channels from channel subscriptions
        val dmChannels = selectedChannels.filter { !it.startsWith("channel:") }
        val channelEntries = selectedChannels.filter { it.startsWith("channel:") }

        val results = mutableListOf<String>()
        var anyFailure = false

        // User DM subscription
        if (dmChannels.isNotEmpty()) {
            when (
                subscriptionService.subscribeAndActivate(
                    userId = slackId,
                    notificationTypeId = typeId,
                    channels = dmChannels,
                    filters = extractedFilters,
                    channelConfig = channelConfig,
                )
            ) {
                is SubscribeResult.Success -> results.add("✅ You'll receive `$typeName` notifications via DM")
                SubscribeResult.AlreadySubscribed -> results.add("ℹ️ You're already subscribed to `$typeName` via DM")
                SubscribeResult.Failure -> {
                    results.add("❌ Failed to create DM subscription")
                    anyFailure = true
                }
            }
        }

        // Channel subscriptions
        channelEntries.forEach { entry ->
            val channelId = entry.removePrefix("channel:")
            val channelName = meta.channelName ?: channelId
            when (
                subscriptionService.subscribeChannelAndActivate(
                    channelId = channelId,
                    channelName = channelName,
                    notificationTypeId = typeId,
                    filters = extractedFilters,
                )
            ) {
                is ChannelSubscribeResult.Success -> results.add(
                    "✅ *#$channelName* will receive `$typeName` notifications"
                )
                ChannelSubscribeResult.Failure -> {
                    results.add("❌ Failed to subscribe *#$channelName*")
                    anyFailure = true
                }
            }
        }

        if (anyFailure && results.all { it.startsWith("❌") }) {
            return ctx.ackWithErrors(mapOf("channels_block" to "Failed to save subscription. Please try again."))
        }

        val filterSummary =
            if (extractedFilters.isNotEmpty()) {
                "\nFilters: " + extractedFilters.joinToString(" | ") { "`${it.field}` ${it.operator} `${it.value}`" }
            } else {
                ""
            }

        val confirmationMessage =
            """
            *Subscription Created!* ✅
            Event: `$typeName`
            ${results.joinToString("\n")}$filterSummary
            """.trimIndent()

        ctx.client().chatPostMessage { r ->
            r
                .channel(slackId)
                .text("Subscribed to $typeName")
                .blocks(withBlocks { section { markdownText(confirmationMessage) } })
        }
        return ctx.ack()
    }
}
