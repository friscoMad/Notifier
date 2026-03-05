package com.notifier.router.api.controller

import com.notifier.router.api.service.NovuService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/subscribers")
class SubscriberController(
    private val novuService: NovuService,
) {
    @PostMapping("/{subscriberId}/slack/endpoint")
    fun createSlackEndpoint(
        @PathVariable subscriberId: String,
    ): ResponseEntity<Void> {
        novuService.createSlackEndpoint(subscriberId)
        return ResponseEntity.noContent().build()
    }
}
