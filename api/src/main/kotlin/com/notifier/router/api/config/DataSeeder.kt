package com.notifier.router.api.config

import com.notifier.router.api.domain.FilterDefinition
import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.repository.ChannelSubscriptionRepository
import com.notifier.router.api.repository.FilterDefinitionRepository
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import com.notifier.router.api.service.NovuService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
@Profile("local")
@Suppress("LongParameterList")
class DataSeeder(
    private val notificationTypeRepository: NotificationTypeRepository,
    private val filterDefinitionRepository: FilterDefinitionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val channelSubscriptionRepository: ChannelSubscriptionRepository,
    private val userRepository: UserRepository,
    private val novuService: NovuService,
    @Value("\${app.seeder.full-reset:false}") private val fullReset: Boolean,
) : CommandLineRunner,
    Ordered {
    private val logger = LoggerFactory.getLogger(DataSeeder::class.java)

    @Transactional
    override fun run(vararg args: String) {
        if (fullReset) {
            logger.info("Full reset enabled — wiping all data including subscriptions and Novu state...")
            userRepository.findAll().forEach { novuService.cleanupSubscriber(it.slackId) }
            subscriptionRepository.deleteAllInBatch()
            channelSubscriptionRepository.deleteAllInBatch()
            userRepository.deleteAllInBatch()
        } else {
            logger.info("Seeding notification types and filter definitions (subscriptions preserved)...")
        }

        // Refresh filter definitions (no FK dependencies from subscriptions)
        filterDefinitionRepository.deleteAllInBatch()
        // Notification types are upserted via save() — deleteAllInBatch() would violate the FK
        // constraint from `subscriptions.notification_type_id` when full-reset is disabled.

        seedData.forEach { (type, filters) ->
            notificationTypeRepository.save(type)
            logger.info("Seeded Notification Type: ${type.typeKey}")

            filters.forEach { filter ->
                filterDefinitionRepository.save(filter)
                logger.info("  Seeded Filter: ${filter.field} for ${type.typeKey}")
            }
        }

        logger.info("DataSeeder completed successfully.")
    }

    override fun getOrder(): Int = 10

    companion object {
        private val seedData: List<Pair<NotificationType, List<FilterDefinition>>> =
            run {
                val commonOperators = listOf("=", "!=", "contains", "regex")

                fun createType(
                    id: String,
                    key: String,
                    name: String,
                    desc: String,
                ) = NotificationType(
                    id = UUID.fromString(id),
                    typeKey = key,
                    name = name,
                    description = desc,
                )

                fun createFilter(
                    typeId: String,
                    field: String,
                    type: String = "string",
                    ops: List<String> = commonOperators,
                ) = FilterDefinition(
                    id = UUID.randomUUID(),
                    notificationTypeId = UUID.fromString(typeId),
                    field = field,
                    fieldType = type,
                    operators = ops,
                )

                listOf(
                    // PR Created
                    createType(
                        "00000000-0000-0000-0000-000000000001",
                        "pr_created",
                        "PR Created",
                        "Triggered when a new PR is opened on GitHub",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000001", "author"),
                            createFilter("00000000-0000-0000-0000-000000000001", "repo"),
                            createFilter(
                                "00000000-0000-0000-0000-000000000001",
                                "base_branch",
                            ),
                        ),
                    // PR Review Requested
                    createType(
                        "00000000-0000-0000-0000-000000000002",
                        "pr_review_requested",
                        "PR Review Requested",
                        "Triggered when someone is requested to review a PR",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000002", "author"),
                            createFilter("00000000-0000-0000-0000-000000000002", "repo"),
                            createFilter("00000000-0000-0000-0000-000000000002", "reviewer"),
                        ),
                    // PR Merged Service
                    createType(
                        "00000000-0000-0000-0000-000000000003",
                        "pr_merged_service",
                        "PR Merged (Service)",
                        "PR merged affecting specific services",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000003", "author"),
                            createFilter("00000000-0000-0000-0000-000000000003", "repo"),
                            createFilter(
                                "00000000-0000-0000-0000-000000000003",
                                "affected_services",
                                "list",
                            ),
                        ),
                    // PR Checks Passed
                    createType(
                        "00000000-0000-0000-0000-000000000004",
                        "pr_checks_passed",
                        "PR Checks Passed",
                        "Triggered when PR checks pass successfully",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000004", "author"),
                            createFilter("00000000-0000-0000-0000-000000000004", "repo"),
                            createFilter(
                                "00000000-0000-0000-0000-000000000004",
                                "check_name",
                            ),
                        ),
                    // PR Checks Failed
                    createType(
                        "00000000-0000-0000-0000-000000000005",
                        "pr_checks_failed",
                        "PR Checks Failed",
                        "Triggered when PR checks fail",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000005", "author"),
                            createFilter("00000000-0000-0000-0000-000000000005", "repo"),
                            createFilter(
                                "00000000-0000-0000-0000-000000000005",
                                "check_name",
                            ),
                        ),
                    // Deploy Started
                    createType(
                        "00000000-0000-0000-0000-000000000006",
                        "deploy_started",
                        "Deployment Started",
                        "Triggered when a new deployment begins",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000006", "service"),
                            createFilter(
                                "00000000-0000-0000-0000-000000000006",
                                "environment",
                            ),
                        ),
                    // Deploy Completed
                    createType(
                        "00000000-0000-0000-0000-000000000007",
                        "deploy_completed",
                        "Deployment Completed",
                        "Triggered when a deployment finishes",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000007", "service"),
                            createFilter(
                                "00000000-0000-0000-0000-000000000007",
                                "environment",
                            ),
                            createFilter("00000000-0000-0000-0000-000000000007", "status"),
                        ),
                    // Flaky Test Detected
                    createType(
                        "00000000-0000-0000-0000-000000000008",
                        "flaky_test_detected",
                        "Flaky Test Detected",
                        "Triggered when a flaky test is identified",
                    ) to
                        listOf(
                            createFilter(
                                "00000000-0000-0000-0000-000000000008",
                                "test_name",
                            ),
                            createFilter("00000000-0000-0000-0000-000000000008", "service"),
                            createFilter("00000000-0000-0000-0000-000000000008", "team"),
                        ),
                    // PR Merged Master Success
                    createType(
                        "00000000-0000-0000-0000-000000000009",
                        "pr_merged_master_success",
                        "PR Merged to Master (Success)",
                        "PR successfully merged to master branch",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000009", "author"),
                            createFilter("00000000-0000-0000-0000-000000000009", "repo"),
                        ),
                    // PR Merged Master Error
                    createType(
                        "00000000-0000-0000-0000-000000000010",
                        "pr_merged_master_error",
                        "PR Merged to Master (Error)",
                        "Error merging PR to master branch",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000010", "author"),
                            createFilter("00000000-0000-0000-0000-000000000010", "repo"),
                        ),
                    // Buildkite Ping
                    createType(
                        "00000000-0000-0000-0000-000000000011",
                        "buildkite_ping",
                        "Buildkite Ping",
                        "Triggered when Buildkite sends a webhook ping (connection confirmed)",
                    ) to emptyList(),
                    // Buildkite Agent Connected
                    createType(
                        "00000000-0000-0000-0000-000000000012",
                        "buildkite_agent_connected",
                        "Buildkite Agent Connected",
                        "Triggered when a Buildkite agent connects to the queue",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000012", "agent_name"),
                            createFilter("00000000-0000-0000-0000-000000000012", "hostname"),
                        ),
                    // Buildkite Agent Disconnected
                    createType(
                        "00000000-0000-0000-0000-000000000013",
                        "buildkite_agent_disconnected",
                        "Buildkite Agent Disconnected",
                        "Triggered when a Buildkite agent disconnects from the queue",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000013", "agent_name"),
                            createFilter("00000000-0000-0000-0000-000000000013", "hostname"),
                        ),
                    // Buildkite Build Scheduled
                    createType(
                        "00000000-0000-0000-0000-000000000014",
                        "buildkite_build_scheduled",
                        "Buildkite Build Scheduled",
                        "Triggered when a Buildkite build is scheduled",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000014", "pipeline"),
                            createFilter("00000000-0000-0000-0000-000000000014", "branch"),
                            createFilter("00000000-0000-0000-0000-000000000014", "creator"),
                        ),
                    // Buildkite Build Running
                    createType(
                        "00000000-0000-0000-0000-000000000015",
                        "buildkite_build_running",
                        "Buildkite Build Running",
                        "Triggered when a Buildkite build starts running",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000015", "pipeline"),
                            createFilter("00000000-0000-0000-0000-000000000015", "branch"),
                            createFilter("00000000-0000-0000-0000-000000000015", "creator"),
                        ),
                    // Buildkite Build Finished
                    createType(
                        "00000000-0000-0000-0000-000000000016",
                        "buildkite_build_finished",
                        "Buildkite Build Finished",
                        "Triggered when a Buildkite build finishes (passed or failed)",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000016", "pipeline"),
                            createFilter("00000000-0000-0000-0000-000000000016", "branch"),
                            createFilter("00000000-0000-0000-0000-000000000016", "status"),
                        ),
                    // Buildkite Job Scheduled
                    createType(
                        "00000000-0000-0000-0000-000000000017",
                        "buildkite_job_scheduled",
                        "Buildkite Job Scheduled",
                        "Triggered when a Buildkite job is scheduled",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000017", "pipeline"),
                            createFilter("00000000-0000-0000-0000-000000000017", "job_name"),
                            createFilter("00000000-0000-0000-0000-000000000017", "branch"),
                        ),
                    // Buildkite Job Started
                    createType(
                        "00000000-0000-0000-0000-000000000018",
                        "buildkite_job_started",
                        "Buildkite Job Started",
                        "Triggered when a Buildkite job starts running on an agent",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000018", "pipeline"),
                            createFilter("00000000-0000-0000-0000-000000000018", "job_name"),
                            createFilter("00000000-0000-0000-0000-000000000018", "branch"),
                        ),
                    // Buildkite Job Finished
                    createType(
                        "00000000-0000-0000-0000-000000000019",
                        "buildkite_job_finished",
                        "Buildkite Job Finished",
                        "Triggered when a Buildkite job finishes (passed or failed)",
                    ) to
                        listOf(
                            createFilter("00000000-0000-0000-0000-000000000019", "pipeline"),
                            createFilter("00000000-0000-0000-0000-000000000019", "job_name"),
                            createFilter("00000000-0000-0000-0000-000000000019", "branch"),
                            createFilter("00000000-0000-0000-0000-000000000019", "status"),
                        ),
                )
            }
    }
}
