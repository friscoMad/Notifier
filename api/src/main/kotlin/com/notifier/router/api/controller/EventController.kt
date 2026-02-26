package com.notifier.router.api.controller

import com.notifier.router.api.domain.Event
import com.notifier.router.api.dto.EventDto
import com.notifier.router.api.service.EventService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/events")
class EventController(
    private val eventService: EventService,
) {
    @PostMapping
    fun processEvent(
        @RequestBody dto: EventDto,
    ): ResponseEntity<Void> {
        eventService.processEventAsync(dto.toEvent())
        return ResponseEntity.accepted().build()
    }

    private fun EventDto.toEvent() =
        Event(
            typeKey = typeKey,
            metadata = metadata,
            payload = payload,
        )
}
