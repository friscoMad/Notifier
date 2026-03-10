package com.notifier.router.api.controller

import com.notifier.router.api.repository.UserRepository
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
    private val userRepository: UserRepository,
) {
    @PostMapping("/{subscriberId}/slack/endpoint")
    fun createSlackEndpoint(
        @PathVariable subscriberId: String,
    ): ResponseEntity<Void> {
        val email = userRepository.findBySlackId(subscriberId)?.email
        novuService.createSlackEndpoint(subscriberId, email)
        return ResponseEntity.noContent().build()
    }
}
