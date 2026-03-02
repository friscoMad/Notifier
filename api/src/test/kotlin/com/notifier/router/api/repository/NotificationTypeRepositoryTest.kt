package com.notifier.router.api.repository

import com.notifier.router.api.domain.NotificationType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class NotificationTypeRepositoryTest(
    @Autowired private val notificationTypeRepository: NotificationTypeRepository,
) : BaseRepositoryTest() {
    @Test
    fun `test find by type key`() {
        val type =
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = "pr_created",
                name = "PR Created",
                description = "When a PR is created",
                defaultChannels = listOf("slack_dm"),
            )
        entityManager.persist(type)
        entityManager.flush()

        val found = notificationTypeRepository.findByTypeKey("pr_created")
        assert(found != null)
        assert(found!!.typeKey == "pr_created")
    }
}
