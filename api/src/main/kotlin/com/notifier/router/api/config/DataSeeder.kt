package com.notifier.router.api.config

import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.repository.ChannelSubscriptionRepository
import com.notifier.router.api.repository.FilterDefinitionRepository
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
@Profile("local")
class DataSeeder(
    private val notificationTypeRepository: NotificationTypeRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val channelSubscriptionRepository: ChannelSubscriptionRepository,
    private val userRepository: UserRepository,
    private val filterDefinitionRepository: FilterDefinitionRepository,
) : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(DataSeeder::class.java)

    @Transactional
    override fun run(vararg args: String) {
        logger.info("Resetting and seeding data for local profile...")

        try {
            // Deletion order respects foreign keys
            subscriptionRepository.deleteAll()
            channelSubscriptionRepository.deleteAll()
            filterDefinitionRepository.deleteAll()
            userRepository.deleteAll()
            notificationTypeRepository.deleteAll()

            seedTypes.forEach {
                notificationTypeRepository.save(it)
                logger.info("Seeded Notification Type: ${it.typeKey}")
            }

            logger.info("DataSeeder completed successfully.")
        } catch (e: Exception) {
            logger.error("Error during DataSeeding: ${e.message}", e)
        }
    }

    companion object {
        private val seedTypes =
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
                    description =
                        "Triggered when a Pull Request is successfully merged",
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
    }
}
