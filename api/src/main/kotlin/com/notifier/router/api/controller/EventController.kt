package com.notifier.router.api.controller

import com.notifier.router.api.domain.Event
import com.notifier.router.api.dto.EventDto
import com.notifier.router.api.service.FilterEvaluator
import com.notifier.router.api.service.SubscriptionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/events")
class EventController(
    private val subscriptionService: SubscriptionService,
    private val filterEvaluator: FilterEvaluator,
) {
    @PostMapping
    fun processEvent(
        @RequestBody eventDto: EventDto,
    ): ResponseEntity<Void> {
        val event =
            Event(
                typeKey = eventDto.typeKey,
                metadata = eventDto.metadata,
                payload = eventDto.payload,
            )

        // In a real implementation, you would:
        // 1. Get all subscriptions for this event type
        // 2. Evaluate filters using FilterEvaluator
        // 3. Route to appropriate channels via Novu

        return ResponseEntity.accepted().build()
    }
}
