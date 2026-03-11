package com.notifier.router.api.config

import com.notifier.router.api.BaseIntegrationTest
import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.domain.User
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import com.notifier.router.api.service.NovuService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

/**
 * Verifies that DataSeeder.run() with fullReset=true does not throw a
 * DataIntegrityViolationException when subscriptions exist.
 *
 * Regression: with deleteAll(), Hibernate may flush notification_types deletes before
 * subscriptions deletes, violating the FK constraint. deleteAllInBatch() issues immediate
 * SQL in declaration order, so subscriptions are removed before notification_types.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test", "local")
@TestPropertySource(properties = ["app.seeder.full-reset=true"])
class DataSeederIntegrationTest : BaseIntegrationTest() {

    @Autowired private lateinit var dataSeeder: DataSeeder

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var notificationTypeRepository: NotificationTypeRepository

    @Autowired private lateinit var subscriptionRepository: SubscriptionRepository

    @MockitoBean private lateinit var novuService: NovuService

    @Test
    fun `run() with fullReset does not throw when subscriptions exist`() {
        val type = notificationTypeRepository.save(
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = "pr_created_seed_test",
                name = "PR Created (Seed Test)",
            ),
        )
        val user = userRepository.save(
            User(id = UUID.randomUUID(), slackId = "USEEDER1", name = "Seeder Test User"),
        )
        subscriptionRepository.save(
            Subscription(
                id = UUID.randomUUID(),
                userId = user.id,
                notificationTypeId = type.id,
                channels = listOf("slack_dm"),
            ),
        )

        // Pre-condition: subscription referencing the notification type exists
        assert(subscriptionRepository.count() > 0) { "Expected at least one subscription before run()" }

        // With deleteAll(), Hibernate may flush notification_types before subscriptions → FK violation.
        // With deleteAllInBatch(), SQL order is deterministic: subscriptions deleted first.
        assertDoesNotThrow { dataSeeder.run() }

        // DataSeeder re-seeded all static notification types
        assert(notificationTypeRepository.count() > 0) { "Expected notification types to be re-seeded" }
    }
}
