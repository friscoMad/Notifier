package com.notifier.router.api.repository

import com.notifier.router.api.domain.FilterDefinition
import com.notifier.router.api.domain.NotificationType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.util.UUID

@DataJpaTest
class FilterDefinitionRepositoryTest(
    @Autowired private val filterDefinitionRepository: FilterDefinitionRepository,
) : BaseRepositoryTest() {
    @Test
    fun `test find by notificationTypeId`() {
        val type =
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = "test-type",
                name = "Test Type",
            )
        entityManager.persist(type)

        val typeId = type.id
        val def =
            FilterDefinition(
                id = UUID.randomUUID(),
                notificationTypeId = typeId,
                field = "repo",
                fieldType = "string",
                operators = listOf("EQ", "IN"),
            )
        entityManager.persist(def)
        entityManager.flush()

        val found = filterDefinitionRepository.findByNotificationTypeId(typeId)
        assert(found.isNotEmpty())
        assert(found[0].notificationTypeId == typeId)
    }
}
