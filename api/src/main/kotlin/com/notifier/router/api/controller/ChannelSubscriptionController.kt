package com.notifier.router.api.controller

import com.notifier.router.api.dto.ChannelSubscriptionDto
import com.notifier.router.api.service.ChannelSubscriptionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/channel-subscriptions")
class ChannelSubscriptionController(
    private val channelSubscriptionService: ChannelSubscriptionService,
) {
    @PostMapping
    fun createChannelSubscription(
        @RequestBody channelSubscriptionDto: ChannelSubscriptionDto,
    ): ResponseEntity<ChannelSubscriptionDto> {
        val createdSubscription = channelSubscriptionService.createChannelSubscription(channelSubscriptionDto)
        return ResponseEntity.ok(createdSubscription)
    }

    @GetMapping("/channels/{slackChannelId}")
    fun getChannelSubscriptionsByChannelId(
        @PathVariable slackChannelId: String,
    ): ResponseEntity<List<ChannelSubscriptionDto>> {
        val subscriptions = channelSubscriptionService.getChannelSubscriptionsByChannelId(slackChannelId)
        return ResponseEntity.ok(subscriptions)
    }

    @DeleteMapping("/{id}")
    fun deleteChannelSubscription(
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        channelSubscriptionService.deleteChannelSubscription(id)
        return ResponseEntity.noContent().build()
    }
}
