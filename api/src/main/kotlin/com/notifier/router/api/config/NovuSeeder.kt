package com.notifier.router.api.config

import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.service.NovuService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.stereotype.Component

/**
 * Automatically provisions matching workflows in Novu for all notification types. This runs on the
 * 'local' profile after DataSeeder to ensure that the trigger identifiers exist in Novu before any
 * events are processed.
 */
@Component
@Profile("local")
class NovuSeeder(
    private val notificationTypeRepository: NotificationTypeRepository,
    private val novuService: NovuService,
) : CommandLineRunner,
    Ordered {
    private val logger = LoggerFactory.getLogger(NovuSeeder::class.java)

    override fun run(vararg args: String) {
        logger.info("Starting Novu workflow provisioning...")

        // 1. Ensure the Slack and SES integrations exist first
        novuService.ensureSlackIntegrationExists()
        novuService.ensureResendIntegrationExists()

        val types = notificationTypeRepository.findAll()
        if (types.isEmpty()) {
            logger.warn("No notification types found in DB. Skipping Novu seeding.")
            return
        }

        val existingWorkflows = novuService.listExistingWorkflowNames()
        types.forEach { type ->
            WORKFLOW_CHANNELS.forEach { channel ->
                try {
                    novuService.ensureChannelWorkflowExists(
                        typeKey = type.typeKey,
                        name = type.name,
                        channel = channel,
                        existingNames = existingWorkflows,
                    )
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // Novu plan limits (e.g. workflow cap) must not prevent startup.
                    logger.warn("Could not provision Novu workflow for ${type.typeKey}/$channel: ${e.message}")
                }
            }
        }

        DIGEST_TYPE_KEYS.forEach { typeKey ->
            val typeName = types.find { it.typeKey == typeKey }?.name ?: typeKey
            DIGEST_CHANNELS.forEach { channel ->
                DIGEST_INTERVALS.forEach { intervalKey ->
                    novuService.ensureDigestChannelWorkflowExists(
                        typeKey = typeKey,
                        name = typeName,
                        channel = channel,
                        intervalKey = intervalKey,
                        existingNames = existingWorkflows,
                    )
                }
            }
        }

        logger.info("Novu workflow provisioning completed.")
    }

    override fun getOrder(): Int = 20 // Runs after DataSeeder (which is default 0)

    companion object {
        /** Each notification type gets one Novu workflow per delivery channel. */
        private val WORKFLOW_CHANNELS = listOf("in_app", "chat", "email")

        /** Notification types that support digest delivery. */
        private val DIGEST_TYPE_KEYS = listOf("pr_created")

        /** Channels for which digest variants are provisioned. */
        private val DIGEST_CHANNELS = listOf("chat", "email")

        /** Digest interval keys; each produces a separate Novu workflow with a distinct window. */
        private val DIGEST_INTERVALS = listOf("1d", "1w")
    }
}
