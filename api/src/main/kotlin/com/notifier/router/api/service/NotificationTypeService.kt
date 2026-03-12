package com.notifier.router.api.service

import com.notifier.router.api.domain.FilterDefinition
import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.repository.FilterDefinitionRepository
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.common.dto.FilterDefinitionDto
import com.notifier.router.common.dto.NotificationTypeDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationTypeService(
    private val notificationTypeRepository: NotificationTypeRepository,
    private val filterDefinitionRepository: FilterDefinitionRepository,
) {
    @Transactional(readOnly = true)
    fun getAllNotificationTypes(): List<NotificationTypeDto> = notificationTypeRepository.findAllByOrderByNameAsc().map {
        it.toDto()
    }

    @Transactional(readOnly = true)
    fun getNotificationTypeByKey(typeKey: String): NotificationTypeDto? = notificationTypeRepository.findByTypeKey(
        typeKey
    )?.toDto()

    @Transactional(readOnly = true)
    fun getFiltersForType(typeKey: String): List<FilterDefinitionDto> =
        notificationTypeRepository.findByTypeKey(typeKey)?.let {
            filterDefinitionRepository.findByNotificationTypeId(it.id).map { f ->
                f.toDto()
            }
        }
            ?: emptyList()

    private fun NotificationType.toDto() =
        NotificationTypeDto(
            id = id.toString(),
            key = typeKey,
            name = name,
            description = description,
        )

    private fun FilterDefinition.toDto() =
        FilterDefinitionDto(
            id = id.toString(),
            notificationTypeId = notificationTypeId.toString(),
            field = field,
            fieldType = fieldType,
            operators = operators,
        )
}
