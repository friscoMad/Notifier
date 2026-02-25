package com.notifier.router.api.controller

import com.notifier.router.api.dto.SubscriptionDto
import com.notifier.router.api.service.SubscriptionService
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
        @RequestBody subscriptionDto: SubscriptionDto,
    ): ResponseEntity<SubscriptionDto> {
        val createdSubscription = subscriptionService.createSubscription(subscriptionDto)
        return ResponseEntity.ok(createdSubscription)
    }

    @GetMapping("/users/{userId}")
    fun getSubscriptionsByUserId(
        @PathVariable userId: String,
    ): ResponseEntity<List<SubscriptionDto>> {
        val subscriptions = subscriptionService.getSubscriptionsByUserId(userId)
        return ResponseEntity.ok(subscriptions)
    }

    @PatchMapping("/{id}")
    fun updateSubscription(
        @PathVariable id: String,
        @RequestBody subscriptionDto: SubscriptionDto,
    ): ResponseEntity<SubscriptionDto> {
        val updatedSubscription = subscriptionService.updateSubscription(id, subscriptionDto)
        return ResponseEntity.ok(updatedSubscription)
    }

    @DeleteMapping("/{id}")
    fun deleteSubscription(
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        subscriptionService.deleteSubscription(id)
        return ResponseEntity.noContent().build()
    }
}
