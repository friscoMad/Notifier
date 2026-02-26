package com.notifier.router.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class UserDto(
    val id: String? = null,
    @field:NotBlank val slackId: String,
    val slackTeamId: String? = null,
    @field:Email val email: String? = null,
    val name: String? = null,
)
