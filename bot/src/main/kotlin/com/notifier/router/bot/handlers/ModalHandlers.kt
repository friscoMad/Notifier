package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.context.builtin.ViewSubmissionContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest
import com.slack.api.bolt.response.Response
import com.slack.api.methods.request.views.ViewsUpdateRequest
import com.slack.api.model.view.Views.view
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ModalHandlers(
        private val app: App,
        private val apiClient: RouterApiClient,
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
        val selectedType = req.payload.actions[0].selectedOption.value
        logger.info("User selected notification type: $selectedType")

        val availableTypes = apiClient.getNotificationTypes()
        val filters = apiClient.getFiltersForType(selectedType)

        val updateView =
                com.notifier.router.bot.view.ModalViewBuilder.buildDynamicSubscriptionModal(
                        selectedTypeKey = selectedType,
                        availableTypes = availableTypes,
                        filters = filters,
                )

        ctx.client()
                .viewsUpdate(
                        ViewsUpdateRequest.builder()
                                .viewId(req.payload.view.id)
                                .hash(req.payload.view.hash)
                                .view(updateView)
                                .build()
                )

        return ctx.ack()
    }

    private fun handleSubscriptionSubmission(
            req: ViewSubmissionRequest,
            ctx: ViewSubmissionContext,
    ): Response {
        val typeKey = req.payload.view.privateMetadata
        if (typeKey.isNullOrEmpty()) {
            return ctx.ackWithErrors(
                    mapOf("type_block" to "You must select a notification type first."),
            )
        }

        val stateValues = req.payload.view.state.values
        logger.info("Received view submission for type $typeKey")

        // 1. Resolve Type ID
        val availableTypes = apiClient.getNotificationTypes()
        val typeId = availableTypes.find { it["typeKey"] == typeKey }?.get("id") as? String

        if (typeId == null) {
            return ctx.ackWithErrors(mapOf("type_block" to "Invalid notification type selected."))
        }

        // 2. Parse Channels
        val channelsState = stateValues["channels_block"]?.get("channels_checkboxes")
        val selectedChannels =
                channelsState?.selectedOptions?.map { it.value } ?: listOf("slack_dm")

        // 3. Parse Digest Settings
        val digestState = stateValues["digest_block"]?.get("digest_select")
        val digestValue = digestState?.selectedOption?.value ?: "immediate"
        val channelConfig = mutableMapOf<String, Any>()
        if (digestValue != "immediate") {
            channelConfig["digest"] = true
            channelConfig["digestInterval"] = digestValue
        }

        // 4. Parse Dynamic Filters
        val filterDefinitions = apiClient.getFiltersForType(typeKey)
        val extractedFilters = mutableListOf<Map<String, Any>>()

        filterDefinitions.forEach { filterDef ->
            val fieldName = filterDef["field"] as String
            val blockId = "filter_block_$fieldName"
            val actionId = "filter_input_$fieldName"

            val inputText = stateValues[blockId]?.get(actionId)?.value
            if (!inputText.isNullOrBlank()) {
                // If they provided multiple comma-separated values, treat as IN, otherwise EQ
                if (inputText.contains(",")) {
                    val listValues =
                            inputText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    extractedFilters.add(
                            mapOf("field" to fieldName, "operator" to "IN", "value" to listValues),
                    )
                } else {
                    extractedFilters.add(
                            mapOf(
                                    "field" to fieldName,
                                    "operator" to "EQ",
                                    "value" to inputText.trim(),
                            ),
                    )
                }
            }
        }

        val slackId = req.payload.user.id
        val teamId = req.payload.team?.id ?: ""

        val payload =
                mapOf(
                        "userId" to slackId,
                        "slackId" to slackId,
                        "slackTeamId" to teamId,
                        "notificationTypeId" to typeId,
                        "channels" to selectedChannels,
                        "channelConfig" to channelConfig,
                        "filters" to extractedFilters,
                )

        val response = apiClient.subscribe(payload)
        return if (response != null) {
            ctx.ack() // Close modal silently indicating success
        } else {
            ctx.ackWithErrors(
                    mapOf("channels_block" to "Failed to save subscription in the Router API."),
            )
        }
    }
}
