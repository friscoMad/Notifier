package com.notifier.router.api.repository

import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.domain.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.util.UUID

@DataJpaTest
class SubscriptionRepositoryTest(
    @Autowired private val subscriptionRepository: SubscriptionRepository,
) : BaseRepositoryTest() {
    @Test
    fun `test find by userId`() {
        val user =
            User(
                id = UUID.randomUUID(),
                slackId = "U123",
                name = "Test User",
            )
        entityManager.persist(user)

        val type =
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = "test-type",
                name = "Test Type",
            )
        entityManager.persist(type)

        val userId = user.id
        val typeId = type.id
        val sub =
            Subscription(
                id = UUID.randomUUID(),
                userId = userId,
                notificationTypeId = typeId,
                channels = listOf("slack_dm"),
            )
        entityManager.persist(sub)
        entityManager.flush()

        val found = subscriptionRepository.findByUserId(userId)
        assert(found.isNotEmpty())
        assert(found[0].userId == userId)
    }
}
