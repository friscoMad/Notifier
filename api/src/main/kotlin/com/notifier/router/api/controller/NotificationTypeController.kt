package com.notifier.router.api.controller

import com.notifier.router.api.dto.FilterDefinitionDto
import com.notifier.router.api.dto.NotificationTypeDto
import com.notifier.router.api.service.NotificationTypeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notification-types")
class NotificationTypeController(
    private val notificationTypeService: NotificationTypeService,
) {
    @GetMapping
    fun getAllNotificationTypes(): ResponseEntity<List<NotificationTypeDto>> =
        ResponseEntity.ok(notificationTypeService.getAllNotificationTypes())

    @GetMapping("/{key}/filters")
    fun getFiltersForType(
        @PathVariable key: String,
    ): ResponseEntity<List<FilterDefinitionDto>> = ResponseEntity.ok(notificationTypeService.getFiltersForType(key))
}
