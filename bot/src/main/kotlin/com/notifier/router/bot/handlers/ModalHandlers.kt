package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.context.builtin.ViewSubmissionContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest
import com.slack.api.bolt.response.Response
import com.slack.api.model.block.Blocks.asBlocks
import com.slack.api.model.block.Blocks.input
import com.slack.api.model.block.Blocks.section
import com.slack.api.model.block.composition.BlockCompositions.plainText
import com.slack.api.model.block.element.BlockElements.checkboxes
import com.slack.api.model.block.element.BlockElements.plainTextInput
import com.slack.api.model.block.element.BlockElements.staticSelect
import com.slack.api.model.view.View
import com.slack.api.model.view.Views.view
import com.slack.api.model.view.Views.viewClose
import com.slack.api.model.view.Views.viewSubmit
import com.slack.api.model.view.Views.viewTitle
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

    private fun handleTypeSelection(req: BlockActionRequest, ctx: ActionContext): Response {
        val selectedType = req.payload.actions[0].selectedOption.value
        logger.info("User selected notification type: $selectedType")

        val filters = apiClient.getFiltersForType(selectedType)

        // Keep the original selection blocks (we need to retain the list of all types and the current selection)
        // For simplicity in this demo, we'll just reconstruct the View here with the new blocks appended.
        
        val newBlocks = mutableListOf<com.slack.api.model.block.LayoutBlock>()

        val types = apiClient.getNotificationTypes()
        val options = types.map { type ->
            val name = type["name"] as? String ?: "Unknown"
            val key = type["typeKey"] as? String ?: "unknown"
            com.slack.api.model.block.composition.BlockCompositions.option(plainText(name), key)
        }
        val currentOption = options.find { it.value == selectedType }

        newBlocks.add(
            section { s ->
                s.blockId("type_block")
                    .text(plainText("Which event do you want to be notified about?"))
                    .accessory(
                        staticSelect { select ->
                            select.actionId("type_select")
                                .placeholder(plainText("Select an event..."))
                                .options(options)
                                .let { if (currentOption != null) it.initialOption(currentOption) else it }
                        }
                    )
            }
        )

        // Dynamically add a text input block for each available filter
        filters.forEach { filterDefinition ->
            val fieldName = filterDefinition["field"] as? String ?: "unknown_field"
            newBlocks.add(
                input { i ->
                    i.blockId("filter_block_$fieldName")
                        .optional(true)
                        .label(plainText("Filter by $fieldName (Optional)"))
                        .element(
                            plainTextInput { pti ->
                                pti.actionId("filter_input_$fieldName")
                            }
                        )
                }
            )
        }

        // Add Delivery Preferences
        newBlocks.add(
            input { i ->
                i.blockId("channels_block")
                    .label(plainText("Delivery Channels"))
                    .element(
                        checkboxes { cb ->
                            cb.actionId("channels_checkboxes")
                                .options(
                                    listOf(
                                        com.slack.api.model.block.composition.BlockCompositions.option(plainText("Slack DM"), "slack_dm"),
                                        com.slack.api.model.block.composition.BlockCompositions.option(plainText("Email"), "email")
                                    )
                                )
                                .initialOptions(
                                    listOf(com.slack.api.model.block.composition.BlockCompositions.option(plainText("Slack DM"), "slack_dm"))
                                )
                        }
                    )
            }
        )

        newBlocks.add(
            input { i ->
                i.blockId("digest_block")
                    .label(plainText("Delivery Speed (Digesting)"))
                    .element(
                        staticSelect { select ->
                            select.actionId("digest_select")
                                .options(
                                    listOf(
                                        com.slack.api.model.block.composition.BlockCompositions.option(plainText("Immediate"), "immediate"),
                                        com.slack.api.model.block.composition.BlockCompositions.option(plainText("Daily (24h)"), "24h"),
                                        com.slack.api.model.block.composition.BlockCompositions.option(plainText("Half-Day (12h)"), "12h")
                                    )
                                )
                                .initialOption(
                                    com.slack.api.model.block.composition.BlockCompositions.option(plainText("Immediate"), "immediate")
                                )
                        }
                    )
            }
        )

        val updateView = view { v ->
            v.callbackId("create_subscription_modal")
                .type("modal")
                .privateMetadata(selectedType) // Save the selected type key to process securely on submit
                .title(viewTitle { t -> t.type("plain_text").text("Configure Notifications") })
                .submit(viewSubmit { s -> s.type("plain_text").text("Save") })
                .close(viewClose { c -> c.type("plain_text").text("Cancel") })
                .blocks(newBlocks)
        }

        ctx.client().viewsUpdate { r ->
            r.viewId(req.payload.view.id)
                .hash(req.payload.view.hash)
                .view(updateView)
        }

        return ctx.ack()
    }

    private fun handleSubscriptionSubmission(
        req: ViewSubmissionRequest,
        ctx: ViewSubmissionContext,
    ): Response {
        val typeKey = req.payload.view.privateMetadata
        if (typeKey.isNullOrEmpty()) {
            return ctx.ackWithErrors(mapOf("type_block" to "You must select a notification type first."))
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
        val selectedChannels = channelsState?.selectedOptions?.map { it.value } ?: listOf("slack_dm")

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
                    val listValues = inputText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    extractedFilters.add(mapOf("field" to fieldName, "operator" to "IN", "value" to listValues))
                } else {
                    extractedFilters.add(mapOf("field" to fieldName, "operator" to "EQ", "value" to inputText.trim()))
                }
            }
        }

        val slackId = req.payload.user.id
        val teamId = req.payload.team?.id ?: ""

        val payload = mapOf(
            "userId" to slackId,
            "slackId" to slackId,
            "slackTeamId" to teamId,
            "notificationTypeId" to typeId,
            "channels" to selectedChannels,
            "channelConfig" to channelConfig,
            "filters" to extractedFilters
        )

        val response = apiClient.subscribe(payload)
        return if (response != null) {
            ctx.ack() // Close modal silently indicating success
        } else {
            ctx.ackWithErrors(mapOf("channels_block" to "Failed to save subscription in the Router API."))
        }
    }
}
