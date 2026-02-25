package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.ViewSubmissionContext
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest
import com.slack.api.bolt.response.Response
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
        app.viewSubmission("create_subscription_modal") {
            req: ViewSubmissionRequest,
            ctx: ViewSubmissionContext,
            ->
            handleSubscriptionSubmission(req, ctx)
        }
    }

    private fun handleSubscriptionSubmission(
        req: ViewSubmissionRequest,
        ctx: ViewSubmissionContext,
    ): Response {
        val stateValues = req.payload.view.state.values

        // Example parsing logic depending on block IDs defined when opening modal:
        // val typeId = stateValues["type_block"]?.get("type_select")?.selectedOption?.value

        logger.info("Received view submission for create_subscription_modal")

        return ctx.ack()
    }
}
