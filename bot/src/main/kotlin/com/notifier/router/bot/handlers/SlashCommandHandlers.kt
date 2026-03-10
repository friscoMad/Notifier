package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.notifier.router.bot.service.ChannelSubscribeResult
import com.notifier.router.bot.service.SubscribeResult
import com.notifier.router.bot.service.SubscriptionService
import com.notifier.router.bot.service.UnsubscribeResult
import com.notifier.router.bot.view.ModalViewBuilder
import com.notifier.router.common.domain.Filter
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.context.builtin.SlashCommandContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import com.slack.api.bolt.request.builtin.SlashCommandRequest
import com.slack.api.bolt.response.Response
import com.slack.api.methods.request.views.ViewsOpenRequest
import com.slack.api.model.kotlin_extension.block.withBlocks
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SlashCommandHandlers(
    private val app: App,
    private val apiClient: RouterApiClient,
    private val subscriptionService: SubscriptionService,
) {
    private val logger = LoggerFactory.getLogger(SlashCommandHandlers::class.java)

    @PostConstruct
    fun registerHandlers() {
        app.command("/notifyme.*".toPattern()) { req: SlashCommandRequest, ctx: SlashCommandContext ->
            handleCommand(req, ctx)
        }

        app.blockAction("^delete_subscription_.*$".toPattern()) {
                req: BlockActionRequest,
                ctx: ActionContext,
            ->
            handleDeleteAction(req, ctx)
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
                `/notifyme subscribe <type>` - Subscribe to a notification type (DM)
                `/notifyme subscribe <type> <field>=<value>` - Subscribe with filters (e.g. `repo=api`)
                `/notifyme channel #channel <type>` - Subscribe a channel to a notification type
                `/notifyme unsubscribe <type>` - Unsubscribe from a notification type
                """.trimIndent()
            return ctx.ack(helpText)
        }

        val parts = text.split("\\s+".toRegex())
        val action = parts[0].lowercase()

        return when (action) {
            "list" -> handleListCommand(req, ctx)
            "subscribe" -> handleSubscribeCommand(parts.drop(1), req, ctx)
            "channel" -> handleChannelSubscribeCommand(parts.drop(1), req, ctx)
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

        // Pass channel context so the modal can offer channel subscription when opened in a channel
        val channelId = req.payload.channelId?.takeIf { it.startsWith("C") || it.startsWith("G") }
        val channelName = req.payload.channelName?.takeIf { channelId != null }

        val modalView = ModalViewBuilder.buildInitialTypeSelectionModal(types, channelId, channelName)

        val response =
            ctx
                .client()
                .viewsOpen(
                    ViewsOpenRequest
                        .builder()
                        .triggerId(triggerId)
                        .view(modalView)
                        .build(),
                )

        if (!response.isOk) {
            logger.error("Failed to open modal: ${response.error} ${response.responseMetadata}")
            return ctx.ack("Could not open configuration modal. Please try again.")
        }

        return ctx.ack()
    }

    private fun handleListCommand(
        req: SlashCommandRequest,
        ctx: SlashCommandContext,
    ): Response {
        val slackId = req.payload.userId
        val subs =
            try {
                apiClient.getSubscriptionsForUser(slackId)
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.error("Failed to fetch subscriptions for $slackId", e)
                return ctx.ack("❌ Failed to fetch subscriptions. Please try again.")
            }

        if (subs.isEmpty()) {
            return ctx.ack("You have no active subscriptions.")
        }

        val availableTypes = apiClient.getNotificationTypes()
        val blocks =
            withBlocks {
                section { markdownText("*Your Subscriptions:*") }
                divider()

                subs.forEach { sub ->
                    val typeName =
                        availableTypes.find { it.id == sub.notificationTypeId }?.name
                            ?: "Unknown Type"
                    val channels = sub.channels.joinToString { "`$it`" }
                    val status = if (sub.enabled) "✅ Enabled" else "❌ Disabled"

                    section {
                        markdownText(
                            """
                            *Type:* `$typeName` ($status)
                            *Channels:* $channels
                            """.trimIndent(),
                        )
                        accessory {
                            button {
                                text(text = "Delete", emoji = true)
                                actionId("delete_subscription_${sub.id}")
                                value(sub.id ?: "")
                                style("danger")
                            }
                        }
                    }

                    if (sub.filters.isNotEmpty()) {
                        context {
                            elements {
                                markdownText(
                                    text =
                                    "*Filters:* " +
                                        sub.filters.joinToString(" | ") {
                                            "`${it.field}` ${it.operator} `${it.value}`"
                                        },
                                )
                            }
                        }
                    }
                    divider()
                }
            }

        ctx.client().chatPostMessage { r ->
            r.channel(slackId).blocks(blocks).text("Your Notification Subscriptions")
        }

        return ctx.ack()
    }

    private fun handleDeleteAction(
        req: BlockActionRequest,
        ctx: ActionContext,
    ): Response {
        val subscriptionId = req.payload.actions[0].value
        logger.info("User requested deletion of subscription: $subscriptionId")

        val message =
            try {
                apiClient.unsubscribe(subscriptionId)
                "✅ Subscription deleted successfully."
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.error("Failed to delete subscription $subscriptionId", e)
                "❌ Failed to delete subscription. Please try again."
            }

        ctx.client().chatPostEphemeral { r ->
            r.channel(req.payload.channel.id).user(req.payload.user.id).text(message)
        }

        return ctx.ack()
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
        val match = availableTypes.find { it.key == typeKey }

        if (match == null) {
            val typeKeys = availableTypes.joinToString { it.key }
            return ctx.ack("Invalid notification type: `$typeKey`. Available types are: $typeKeys")
        }

        val typeId = match.id!!
        val filters = mutableListOf<Filter>()

        if (args.size > 1) {
            val validFields = try {
                apiClient.getFiltersForType(typeKey).map { it.field }.toSet()
            } catch (e: org.springframework.web.client.RestClientException) {
                logger.warn("Could not fetch filter definitions for $typeKey", e)
                emptySet()
            }

            for (i in 1 until args.size) {
                val filterArg = args[i]
                val pair = filterArg.split("=", limit = 2)
                if (pair.size != 2) {
                    return ctx.ack(
                        "Invalid filter format: `$filterArg`. Filters must use `field=value` syntax " +
                            "(e.g. `repo=api`).",
                    )
                }
                val field = pair[0]
                val rawValue = pair[1]

                if (validFields.isNotEmpty() && field !in validFields) {
                    val validList = validFields.joinToString { "`$it`" }
                    return ctx.ack(
                        "Unknown filter field: `$field`. Valid fields for `$typeKey` are: $validList",
                    )
                }

                filters.add(
                    if (rawValue.contains(",")) {
                        Filter(field, "IN", rawValue.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    } else {
                        Filter(field, "EQ", rawValue)
                    },
                )
            }
        }

        val filtersSuffix = if (filters.isNotEmpty()) " with filters" else ""
        return when (
            subscriptionService.subscribeAndActivate(
                userId = req.payload.userId,
                notificationTypeId = typeId,
                channels = listOf("slack_dm"),
                filters = filters,
            )
        ) {
            is SubscribeResult.Success ->
                ctx.ack("✅ Subscribed to `$typeKey`$filtersSuffix. You'll receive notifications here.")
            SubscribeResult.Failure ->
                ctx.ack("❌ Failed to subscribe to `$typeKey`. Please try again later.")
        }
    }

    private fun handleChannelSubscribeCommand(
        args: List<String>,
        req: SlashCommandRequest,
        ctx: SlashCommandContext,
    ): Response {
        if (args.size < 2) {
            return ctx.ack(
                "Usage: `/notifyme channel #channel <type>` (e.g. `/notifyme channel #dev-alerts pr_created`)"
            )
        }

        val channelArg = args[0]
        val mentionMatch =
            Regex("<#([A-Z0-9]+)\\|([^>]+)>").find(channelArg)
                ?: return ctx.ack("Please mention the channel using #channel-name (e.g. `/notifyme channel #dev-alerts pr_created`)")

        val channelId = mentionMatch.groupValues[1]
        val channelName = mentionMatch.groupValues[2]

        // Verify the bot is a member before subscribing
        val infoResponse = ctx.client().conversationsInfo { it.channel(channelId) }
        if (!infoResponse.isOk) {
            return ctx.ack(
                "Could not look up <#$channelId|$channelName>. Make sure I'm invited first: `/invite @NotifyMe` in that channel.",
            )
        }
        if (infoResponse.channel?.isMember != true) {
            return ctx.ack(
                "I'm not in <#$channelId|$channelName> yet.\n" +
                    "Run `/invite @NotifyMe` in that channel, then try again.",
            )
        }

        val typeKey = args[1]
        val availableTypes = apiClient.getNotificationTypes()
        val match =
            availableTypes.find { it.key == typeKey }
                ?: run {
                    val typeKeys = availableTypes.joinToString { it.key }
                    return ctx.ack("Unknown notification type: `$typeKey`. Available: $typeKeys")
                }

        val filters = mutableListOf<Filter>()
        for (i in 2 until args.size) {
            val pair = args[i].split("=", limit = 2)
            if (pair.size == 2) filters.add(Filter(pair[0], "EQ", pair[1]))
        }

        return when (
            subscriptionService.subscribeChannelAndActivate(
                channelId = channelId,
                channelName = channelName,
                notificationTypeId = match.id!!,
                filters = filters,
            )
        ) {
            is ChannelSubscribeResult.Success ->
                ctx.ack("✅ <#$channelId|$channelName> is now subscribed to `$typeKey` notifications.")
            ChannelSubscribeResult.Failure ->
                ctx.ack("❌ Failed to subscribe <#$channelId|$channelName> to `$typeKey`. Please try again.")
        }
    }

    private fun handleUnsubscribeCommand(
        args: List<String>,
        req: SlashCommandRequest,
        ctx: SlashCommandContext,
    ): Response {
        if (args.isEmpty()) {
            return ctx.ack("Please specify a notification type. E.g., `/notifyme unsubscribe pr_created`")
        }

        val typeKey = args[0]
        val availableTypes = apiClient.getNotificationTypes()
        val match = availableTypes.find { it.key == typeKey }
            ?: run {
                val typeKeys = availableTypes.joinToString { it.key }
                return ctx.ack("Invalid notification type: `$typeKey`. Available types are: $typeKeys")
            }

        return when (subscriptionService.unsubscribe(req.payload.userId, match.id!!)) {
            UnsubscribeResult.Success ->
                ctx.ack("Successfully unsubscribed from `$typeKey`.")
            UnsubscribeResult.NotSubscribed ->
                ctx.ack("You have no active subscription for `$typeKey`.")
            UnsubscribeResult.Failure ->
                ctx.ack("❌ Failed to unsubscribe from `$typeKey`. Please try again later.")
        }
    }
}
