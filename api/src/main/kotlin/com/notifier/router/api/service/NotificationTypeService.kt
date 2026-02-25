package com.notifier.router.api.service

import com.notifier.router.api.domain.FilterDefinition
import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.dto.FilterDefinitionDto
import com.notifier.router.api.dto.NotificationTypeDto
import com.notifier.router.api.repository.FilterDefinitionRepository
import com.notifier.router.api.repository.NotificationTypeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationTypeService(
    private val notificationTypeRepository: NotificationTypeRepository,
    private val filterDefinitionRepository: FilterDefinitionRepository,
) {
    @Transactional(readOnly = true)
    fun getAllNotificationTypes(): List<NotificationTypeDto> =
        notificationTypeRepository
            .findAll()
            .map { mapToDto(it) }

    @Transactional(readOnly = true)
    fun getNotificationTypeByKey(typeKey: String): NotificationTypeDto? {
        val notificationType = notificationTypeRepository.findByTypeKey(typeKey)
        return notificationType?.let { mapToDto(it) }
    }

    @Transactional(readOnly = true)
    fun getFiltersForType(typeKey: String): List<FilterDefinitionDto> {
        val notificationType = notificationTypeRepository.findByTypeKey(typeKey)
        return notificationType?.let {
            val filters = filterDefinitionRepository.findByNotificationTypeId(it.id)
            filters.map { mapToDto(it) }
        } ?: emptyList()
    }

    private fun mapToDto(notificationType: NotificationType): NotificationTypeDto =
        NotificationTypeDto(
            id = notificationType.id.toString(),
            typeKey = notificationType.typeKey,
            name = notificationType.name,
            description = notificationType.description,
        )

    private fun mapToDto(filterDefinition: FilterDefinition): FilterDefinitionDto =
        FilterDefinitionDto(
            id = filterDefinition.id.toString(),
            notificationTypeId = filterDefinition.notificationTypeId.toString(),
            field = filterDefinition.field,
            fieldType = filterDefinition.fieldType,
            operators = filterDefinition.operators,
        )
}
