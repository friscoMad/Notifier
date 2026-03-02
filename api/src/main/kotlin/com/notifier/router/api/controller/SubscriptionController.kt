package com.notifier.router.api.controller

import com.notifier.router.api.service.SubscriptionService
import com.notifier.router.common.dto.SubscriptionDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/subscriptions")
class SubscriptionController(
    private val subscriptionService: SubscriptionService,
) {
    @PostMapping
    fun createSubscription(
        @RequestBody dto: SubscriptionDto,
    ): ResponseEntity<SubscriptionDto> = ResponseEntity.ok(subscriptionService.createSubscription(dto))

    @GetMapping("/users/{userId}")
    fun getSubscriptionsByUserId(
        @PathVariable userId: String,
    ): ResponseEntity<List<SubscriptionDto>> = ResponseEntity.ok(subscriptionService.getSubscriptionsByUserId(userId))

    @PatchMapping("/{id}")
    fun updateSubscription(
        @PathVariable id: String,
        @RequestBody dto: SubscriptionDto,
    ): ResponseEntity<SubscriptionDto> = ResponseEntity.ok(subscriptionService.updateSubscription(id, dto))

    @DeleteMapping("/{id}")
    fun deleteSubscription(
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        subscriptionService.deleteSubscription(id)
        return ResponseEntity.noContent().build()
    }
}
