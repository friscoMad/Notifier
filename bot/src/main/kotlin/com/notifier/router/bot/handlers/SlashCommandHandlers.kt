package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.notifier.router.bot.service.ChannelSubscribeResult
import com.notifier.router.bot.service.SubscribeResult
import com.notifier.router.bot.service.SubscriptionService
import com.notifier.router.bot.service.UnsubscribeResult
import com.notifier.router.bot.view.ModalViewBuilder
import com.notifier.router.common.domain.Filter
import com.notifier.router.common.dto.ChannelSubscriptionDto
import com.notifier.router.common.dto.NotificationTypeDto
import com.notifier.router.common.dto.SubscriptionDto
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.context.builtin.SlashCommandContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import com.slack.api.bolt.request.builtin.SlashCommandRequest
import com.slack.api.bolt.response.Response
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.views.ViewsOpenRequest
import com.slack.api.model.kotlin_extension.block.withBlocks
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

private const val EMPTY_SUBSCRIPTIONS_MSG =
    "No active subscriptions. Use `/notifyme subscribe` to create one."

@Component
class SlashCommandHandlers(
    private val app: App,
    private val apiClient: RouterApiClient,
    private val subscriptionService: SubscriptionService,
) {
    private val logger = LoggerFactory.getLogger(SlashCommandHandlers::class.java)

    /**
     * Fetches the user's email from Slack. Returns null on any failure — email is best-effort
     * and must not block subscription creation if Slack is unavailable or the profile has no email.
     */
    private fun fetchUserEmail(client: MethodsClient, userId: String): String? =
        try {
            client.usersInfo { it.user(userId) }.takeIf { it.isOk }?.user?.profile?.email
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Slack SDK throws IOException + SlackApiException with no common parent narrower than Exception.
            logger.warn("Could not fetch email for Slack user $userId", e)
            null
        }

    @PostConstruct
    fun registerHandlers() {
        app.command("/notifyme.*".toPattern()) { req: SlashCommandRequest, ctx: SlashCommandContext ->
            handleCommand(req, ctx)
        }

        app.blockAction("delete_subscription") { req: BlockActionRequest, ctx: ActionContext ->
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
        val channelId = req.payload.channelId
        val isChannelContext = channelId != null && (channelId.startsWith("C") || channelId.startsWith("G"))

        return if (isChannelContext && channelId != null) {
            handleListInChannel(slackId, channelId, ctx)
        } else {
            handleListInDm(slackId, ctx)
        }
    }

    private fun handleListInChannel(
        slackId: String,
        channelId: String,
        ctx: SlashCommandContext,
    ): Response {
        val channelSubs =
            try {
                apiClient.getChannelSubscriptionsForChannel(channelId)
            } catch (e: RestClientException) {
                logger.error("Failed to fetch channel subscriptions for $channelId", e)
                return ctx.ack("❌ Failed to fetch subscriptions. Please try again.")
            }

        if (channelSubs.isEmpty()) {
            return ctx.ack(
                "No active subscriptions for this channel. Use `/notifyme channel #channel <type>` to create one."
            )
        }

        val availableTypes =
            try {
                apiClient.getNotificationTypes()
            } catch (e: RestClientException) {
                logger.error("Failed to fetch notification types", e)
                emptyList()
            }

        ctx.client().chatPostEphemeral { r ->
            r.channel(channelId).user(slackId)
                .blocks(buildChannelSubscriptionBlocks(channelSubs, availableTypes))
                .text("Channel subscriptions")
        }

        return ctx.ack()
    }

    private fun handleListInDm(
        slackId: String,
        ctx: SlashCommandContext,
    ): Response {
        val subs =
            try {
                apiClient.getSubscriptionsForUser(slackId)
            } catch (e: RestClientException) {
                logger.error("Failed to fetch subscriptions for $slackId", e)
                return ctx.ack("❌ Failed to fetch subscriptions. Please try again.")
            }

        if (subs.isEmpty()) {
            return ctx.ack(EMPTY_SUBSCRIPTIONS_MSG)
        }

        val availableTypes =
            try {
                apiClient.getNotificationTypes()
            } catch (e: RestClientException) {
                logger.error("Failed to fetch notification types for $slackId", e)
                emptyList()
            }

        ctx.client().chatPostMessage { r ->
            r.channel(slackId)
                .blocks(buildSubscriptionBlocks(subs, availableTypes))
                .text("Your subscriptions")
        }

        return ctx.ack()
    }

    private fun handleDeleteAction(
        req: BlockActionRequest,
        ctx: ActionContext,
    ): Response {
        val actionValue = req.payload.actions[0].value
        val userId = req.payload.user.id
        val channelId = req.payload.channel.id
        val isChannelSub = actionValue.startsWith("channel:")
        val subscriptionId = actionValue.removePrefix("user:").removePrefix("channel:")
        logger.info(
            "User requested deletion of ${if (isChannelSub) "channel" else "user"} subscription: $subscriptionId"
        )

        try {
            if (isChannelSub) apiClient.unsubscribeChannel(subscriptionId) else apiClient.unsubscribe(subscriptionId)
        } catch (e: RestClientException) {
            logger.error("Failed to delete subscription $subscriptionId", e)
            ctx.client().chatPostEphemeral { r ->
                r.channel(channelId).user(userId).text("❌ Failed to delete subscription. Please try again.")
            }
            return ctx.ack()
        }

        ctx.client().chatPostEphemeral { r ->
            r.channel(channelId).user(userId).text("✅ Subscription deleted.")
        }

        return ctx.ack()
    }

    private fun buildSubscriptionBlocks(
        subs: List<SubscriptionDto>,
        availableTypes: List<NotificationTypeDto>,
    ) = withBlocks {
        section { markdownText("*Your subscriptions:*") }
        divider()
        subs.forEach { sub ->
            val typeName = availableTypes.find { it.id == sub.notificationTypeId }?.name ?: "Unknown Type"
            val filtersSummary =
                if (sub.filters.isNotEmpty()) {
                    "\nFilters: " + sub.filters.joinToString(", ") { "${it.field} = ${it.value}" }
                } else {
                    ""
                }
            val channels = sub.channels.joinToString { ModalViewBuilder.channelDisplayName(it) }
            section {
                markdownText("*$typeName*${filtersSummary}\nChannels: $channels")
                accessory {
                    button {
                        text(text = "Delete", emoji = true)
                        actionId("delete_subscription")
                        value("user:${sub.id}")
                        style("danger")
                    }
                }
            }
            divider()
        }
    }

    private fun buildChannelSubscriptionBlocks(
        channelSubs: List<ChannelSubscriptionDto>,
        availableTypes: List<NotificationTypeDto>,
    ) = withBlocks {
        section { markdownText("*Subscriptions for this channel:*") }
        divider()
        channelSubs.forEach { sub ->
            val typeName = availableTypes.find { it.id == sub.notificationTypeId }?.name ?: "Unknown Type"
            val filtersSummary =
                if (sub.filters.isNotEmpty()) {
                    "\nFilters: " + sub.filters.joinToString(", ") { "${it.field} = ${it.value}" }
                } else {
                    ""
                }
            section {
                markdownText("*$typeName*$filtersSummary")
                accessory {
                    button {
                        text(text = "Delete", emoji = true)
                        actionId("delete_subscription")
                        value("channel:${sub.id}")
                        style("danger")
                    }
                }
            }
            divider()
        }
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

        // Parse filter arguments (e.g., repo=api)
        if (args.size > 1) {
            for (i in 1 until args.size) {
                val pair = args[i].split("=", limit = 2)
                if (pair.size == 2) {
                    filters.add(Filter(pair[0], "EQ", pair[1]))
                }
            }
        }

        val filtersSuffix = if (filters.isNotEmpty()) " with filters" else ""
        return when (
            subscriptionService.subscribeAndActivate(
                userId = req.payload.userId,
                email = fetchUserEmail(ctx.client(), req.payload.userId),
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
