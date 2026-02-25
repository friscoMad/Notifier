package com.notifier.router.api.controller

import com.notifier.router.api.dto.FilterDefinitionDto
import com.notifier.router.api.dto.NotificationTypeDto
import com.notifier.router.api.service.NotificationTypeService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
class NotificationTypeControllerTest {
    @Mock private lateinit var notificationTypeService: NotificationTypeService

    @org.mockito.InjectMocks
    private lateinit var notificationTypeController: NotificationTypeController

    @Test
    fun `test getAllNotificationTypes returns all types`() {
        val expectedDto =
            NotificationTypeDto(
                id = "${java.util.UUID.randomUUID()}",
                typeKey = "pr_created",
                name = "Pull Request Created",
                description = "A pull request was created",
            )

        whenever(notificationTypeService.getAllNotificationTypes())
            .thenReturn(listOf(expectedDto))

        val result = notificationTypeController.getAllNotificationTypes()

        assert(result.statusCode == HttpStatus.OK)
        assert(result.body != null)
        assert(result.body!!.size == 1)
        assert(result.body!!.first().typeKey == "pr_created")
    }

    @Test
    fun `test getFiltersForType returns filters when type found`() {
        val typeKey = "pr_created"
        val expectedDto =
            FilterDefinitionDto(
                id = "${java.util.UUID.randomUUID()}",
                notificationTypeId = "${java.util.UUID.randomUUID()}",
                field = "repo",
                fieldType = "STRING",
                operators = listOf("EQ", "IN"),
            )

        whenever(notificationTypeService.getFiltersForType(any()))
            .thenReturn(listOf(expectedDto))

        val result = notificationTypeController.getFiltersForType(typeKey)

        assert(result.statusCode == HttpStatus.OK)
        assert(result.body != null)
        assert(result.body!!.size == 1)
        assert(result.body!!.first().field == "repo")
    }

    @Test
    fun `test getFiltersForType returns empty list when type not found`() {
        val typeKey = "unknown_type"

        whenever(notificationTypeService.getFiltersForType(any()))
            .thenReturn(emptyList())

        val result = notificationTypeController.getFiltersForType(typeKey)

        assert(result.statusCode == HttpStatus.OK)
        assert(result.body != null)
        assert(result.body!!.isEmpty())
    }
}
