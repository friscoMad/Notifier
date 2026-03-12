package com.notifier.router.api.service

import com.notifier.router.api.domain.FilterDefinition
import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.repository.FilterDefinitionRepository
import com.notifier.router.api.repository.NotificationTypeRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NotificationTypeServiceTest {
    @Mock private lateinit var notificationTypeRepository: NotificationTypeRepository

    @Mock private lateinit var filterDefinitionRepository: FilterDefinitionRepository

    private val notificationTypeService: NotificationTypeService by lazy {
        NotificationTypeService(notificationTypeRepository, filterDefinitionRepository)
    }

    @Test
    fun `test getAllNotificationTypes returns all types`() {
        val type1 =
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = "pr_created",
                name = "Pull Request Created",
                description = "A pull request was created",
            )
        val type2 =
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = "deploy_completed",
                name = "Deploy Completed",
                description = "A deployment was completed",
            )

        whenever(notificationTypeRepository.findAllByOrderByNameAsc()).thenReturn(listOf(type1, type2))

        val result = notificationTypeService.getAllNotificationTypes()

        verify(notificationTypeRepository).findAllByOrderByNameAsc()
        assert(result.size == 2)
        assert(result.any { it.key == "pr_created" })
        assert(result.any { it.key == "deploy_completed" })
    }

    @Test
    fun `test getNotificationTypeByKey returns type when found`() {
        val typeKey = "pr_created"
        val notificationType =
            NotificationType(
                id = UUID.randomUUID(),
                typeKey = typeKey,
                name = "Pull Request Created",
                description = "A pull request was created",
            )

        whenever(notificationTypeRepository.findByTypeKey(any()))
            .thenReturn(notificationType)

        val result = notificationTypeService.getNotificationTypeByKey(typeKey)

        verify(notificationTypeRepository).findByTypeKey(typeKey)
        assert(result != null)
        assert(result!!.key == typeKey)
    }

    @Test
    fun `test getNotificationTypeByKey returns null when not found`() {
        val typeKey = "unknown_type"

        whenever(notificationTypeRepository.findByTypeKey(any())).thenReturn(null)

        val result = notificationTypeService.getNotificationTypeByKey(typeKey)

        verify(notificationTypeRepository).findByTypeKey(typeKey)
        assert(result == null)
    }

    @Test
    fun `test getFiltersForType returns filters when type found`() {
        val typeKey = "pr_created"
        val notificationTypeId = UUID.randomUUID()
        val notificationType =
            NotificationType(
                id = notificationTypeId,
                typeKey = typeKey,
                name = "Pull Request Created",
                description = "A pull request was created",
            )

        val filter1 =
            FilterDefinition(
                id = UUID.randomUUID(),
                notificationTypeId = notificationTypeId,
                field = "repo",
                fieldType = "STRING",
                operators = listOf("EQ", "IN", "CONTAINS"),
            )
        val filter2 =
            FilterDefinition(
                id = UUID.randomUUID(),
                notificationTypeId = notificationTypeId,
                field = "author",
                fieldType = "STRING",
                operators = listOf("EQ", "CONTAINS"),
            )

        whenever(notificationTypeRepository.findByTypeKey(any()))
            .thenReturn(notificationType)
        whenever(filterDefinitionRepository.findByNotificationTypeId(any()))
            .thenReturn(listOf(filter1, filter2))

        val result = notificationTypeService.getFiltersForType(typeKey)

        verify(notificationTypeRepository).findByTypeKey(typeKey)
        verify(filterDefinitionRepository).findByNotificationTypeId(notificationTypeId)
        assert(result.size == 2)
        assert(result.any { it.field == "repo" })
        assert(result.any { it.field == "author" })
    }

    @Test
    fun `test getFiltersForType returns empty list when type not found`() {
        val typeKey = "unknown_type"

        whenever(notificationTypeRepository.findByTypeKey(any())).thenReturn(null)

        val result = notificationTypeService.getFiltersForType(typeKey)

        verify(notificationTypeRepository).findByTypeKey(typeKey)
        assert(result.isEmpty())
    }
}
