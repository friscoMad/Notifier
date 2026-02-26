package com.notifier.router.api.config

import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.repository.NotificationTypeRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Seeds the database with essential data when running locally. This ensures the bot and API have
 * notification types to work with without needing manual database inserts.
 */
@Component
@Profile("local")
class DataSeeder(
    private val notificationTypeRepository: NotificationTypeRepository,
) : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(DataSeeder::class.java)

    override fun run(vararg args: String?) {
        logger.info("Running DataSeeder for local profile...")

        val types =
            listOf(
                NotificationType(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    typeKey = "pr_created",
                    name = "Pull Request Created",
                    description =
                        "Triggered when a new Pull Request is opened on GitHub",
                ),
                NotificationType(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    typeKey = "pr_merged",
                    name = "Pull Request Merged",
                    description = "Triggered when a Pull Request is successfully merged",
                ),
                NotificationType(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    typeKey = "deploy_started",
                    name = "Deployment Started",
                    description = "Triggered when a new deployment begins",
                ),
                NotificationType(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000004"),
                    typeKey = "flaky_test_detected",
                    name = "Flaky Test Detected",
                    description = "Triggered when a flaky test runs into issues on CI",
                ),
            )

        for (type in types) {
            if (notificationTypeRepository.findByTypeKey(type.typeKey) == null) {
                notificationTypeRepository.save(type)
                logger.info("Seeded Notification Type: ${type.typeKey}")
            }
        }

        logger.info("DataSeeder completed.")
    }
}
