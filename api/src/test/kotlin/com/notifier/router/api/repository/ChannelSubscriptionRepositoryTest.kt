package com.notifier.router.api.repository

import com.notifier.router.api.domain.ChannelSubscription
import com.notifier.router.api.domain.NotificationType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.util.UUID

@DataJpaTest
class ChannelSubscriptionRepositoryTest(
    @Autowired private val channelSubscriptionRepository: ChannelSubscriptionRepository,
) : BaseRepositoryTest() {
    @Test
    fun `test find by slackChannelId`() {
        val type =
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = "test-type",
                name = "Test Type",
            )
        entityManager.persist(type)

        val typeId = type.id
        val sub =
            ChannelSubscription(
                id = UUID.randomUUID(),
                slackChannelId = "C12345",
                slackChannelName = "general",
                notificationTypeId = typeId,
            )
        entityManager.persist(sub)
        entityManager.flush()

        val found = channelSubscriptionRepository.findBySlackChannelId("C12345")
        assert(found.isNotEmpty())
        assert(found[0].slackChannelId == "C12345")
    }
}
