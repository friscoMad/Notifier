package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.SlashCommandContext
import com.slack.api.bolt.request.builtin.SlashCommandRequest
import com.slack.api.bolt.response.Response
import com.slack.api.model.block.Blocks.asBlocks
import com.slack.api.model.block.Blocks.input
import com.slack.api.model.block.Blocks.section
import com.slack.api.model.block.composition.BlockCompositions.dispatchActionConfig
import com.slack.api.model.block.composition.BlockCompositions.plainText
import com.slack.api.model.block.element.BlockElements.staticSelect
import com.slack.api.model.view.Views.view
import com.slack.api.model.view.Views.viewClose
import com.slack.api.model.view.Views.viewSubmit
import com.slack.api.model.view.Views.viewTitle
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SlashCommandHandlers(
    private val app: App,
    private val apiClient: RouterApiClient,
) {
    private val logger = LoggerFactory.getLogger(SlashCommandHandlers::class.java)

    @PostConstruct
    fun registerHandlers() {
        app.command("/notifyme") { req: SlashCommandRequest, ctx: SlashCommandContext ->
            handleCommand(req, ctx)
        }
    }

    private fun handleCommand(
        req: SlashCommandRequest,
        ctx: SlashCommandContext,
    ): Response {
        val text = req.payload.text?.trim() ?: ""
        logger.info("Received /notifyme command with args: $text")

        if (text.isEmpty()) {
            return handleOpenModal(req, ctx)
        }

        if (text.equals("help", ignoreCase = true)) {
            val helpText =
                """
                *Notification Router Bot*
                Usage:
                `/notifyme` - Open interactive subscription modal
                `/notifyme help` - Show this message
                `/notifyme list` - List your active subscriptions
                `/notifyme subscribe <type>` - Subscribe to a notification type
                `/notifyme subscribe <type> <field>=<value>` - Subscribe with filters (e.g. `repo=api`)
                `/notifyme unsubscribe <type>` - Unsubscribe from a notification type
                """.trimIndent()
            return ctx.ack(helpText)
        }

        val parts = text.split("\\s+".toRegex())
        val action = parts[0].lowercase()

        return when (action) {
            "list" -> handleListCommand(req, ctx)
            "subscribe" -> handleSubscribeCommand(parts.drop(1), req, ctx)
            "unsubscribe" -> handleUnsubscribeCommand(parts.drop(1), req, ctx)
            else -> ctx.ack("Unknown command: $action. Use `/notifyme help` for usage.")
        }
    }

    private fun handleOpenModal(
        req: SlashCommandRequest,
        ctx: SlashCommandContext,
    ): Response {
        val triggerId = req.payload.triggerId
        val types = apiClient.getNotificationTypes()

        val options = types.map { type ->
            val name = type["name"] as? String ?: "Unknown"
            val key = type["typeKey"] as? String ?: "unknown"
            com.slack.api.model.block.composition.BlockCompositions.option(plainText(name), key)
        }

        val modalView = view { v ->
            v.callbackId("create_subscription_modal")
                .type("modal")
                .notifyOnClose(true)
                .title(viewTitle { t -> t.type("plain_text").text("Configure Notifications") })
                .submit(viewSubmit { s -> s.type("plain_text").text("Save") })
                .close(viewClose { c -> c.type("plain_text").text("Cancel") })
                .blocks(
                    asBlocks(
                        section { s ->
                            s.blockId("type_block")
                                .text(plainText("Which event do you want to be notified about?"))
                                .accessory(
                                    staticSelect { select ->
                                        select.actionId("type_select")
                                            .placeholder(plainText("Select an event..."))
                                            .options(options)
                                    }
                                )
                        }
                    )
                )
        }

        val response = ctx.client().viewsOpen { r -> r.triggerId(triggerId).view(modalView) }
        
        if (!response.isOk) {
            logger.error("Failed to open modal: ${response.error}")
            return ctx.ack("Could not open configuration modal. Please try again.")
        }

        return ctx.ack()
    }

    private fun handleListCommand(
        req: SlashCommandRequest,
        ctx: SlashCommandContext,
    ): Response {
        val slackId = req.payload.userId
        val subs = apiClient.getSubscriptionsForUser(slackId)

        if (subs.isEmpty()) {
            return ctx.ack("You have no active subscriptions.")
        }

        val sb = StringBuilder("*Your Subscriptions:*\n")
        subs.forEach { sub ->
            val type = sub["notificationTypeId"] as? String ?: "Unknown"
            val numFilters = (sub["filters"] as? List<*>)?.size ?: 0
            sb.append("• Type: $type | Filters: $numFilters active rules\n")
        }
        return ctx.ack(sb.toString())
    }

    private fun handleSubscribeCommand(
        args: List<String>,
        req: SlashCommandRequest,
        ctx: SlashCommandContext,
    ): Response {
        if (args.isEmpty()) {
            return ctx.ack(
                "Please specify a notification type. E.g., `/notifyme subscribe pr_created`",
            )
        }

        val typeKey = args[0]
        val availableTypes = apiClient.getNotificationTypes()
        val match = availableTypes.find { (it["typeKey"] as? String) == typeKey }

        if (match == null) {
            return ctx.ack(
                "Invalid notification type: `$typeKey`. Available types are: ${availableTypes.map { it["typeKey"] }.joinToString()}",
            )
        }

        val typeId = match["id"] as String
        val filters = mutableListOf<Map<String, Any>>()

        // Parse filter arguments (e.g., repo=api)
        if (args.size > 1) {
            for (i in 1 until args.size) {
                val pair = args[i].split("=", limit = 2)
                if (pair.size == 2) {
                    filters.add(mapOf("field" to pair[0], "operator" to "EQ", "value" to pair[1]))
                }
            }
        }

        val payload =
            mapOf(
                "userId" to
                    req.payload
                        .userId, // We pass slackId directly. In a real system the
                // API resolves matching UUIDs or creates the user
                // lazily.
                "slackId" to req.payload.userId,
                "slackTeamId" to req.payload.teamId,
                "notificationTypeId" to typeId,
                "channels" to listOf("slack_dm"),
                "filters" to filters,
            )

        val response = apiClient.subscribe(payload)
        return if (response != null) {
            ctx.ack(
                "Successfully subscribed to `$typeKey`" +
                    (if (filters.isNotEmpty()) " with filters." else ""),
            )
        } else {
            ctx.ack("Failed to subscribe to `$typeKey`. Please try again later.")
        }
    }

    private fun handleUnsubscribeCommand(
        args: List<String>,
        req: SlashCommandRequest,
        ctx: SlashCommandContext,
    ): Response {
        // Unsubscribe logic goes here...
        // For simplicity we will mock it
        return ctx.ack("Unsubscribe functionality is WIP.")
    }
}
