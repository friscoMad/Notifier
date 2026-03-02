package com.notifier.router.api.controller

import com.notifier.router.api.service.UserService
import com.notifier.router.common.dto.UserDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    @PostMapping
    fun createUser(
        @RequestBody userDto: UserDto,
    ): ResponseEntity<UserDto> = ResponseEntity.ok(userService.createUser(userDto))

    @GetMapping("/{slackId}")
    fun getUserBySlackId(
        @PathVariable slackId: String,
    ): ResponseEntity<UserDto> =
        userService.getUserBySlackId(slackId)?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
}
