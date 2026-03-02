package com.notifier.router.common.dto

import jakarta.validation.constraints.NotBlank

data class UserDto(
    val id: String? = null,
    @field:NotBlank val slackId: String,
    val slackTeamId: String? = null,
    val email: String? = null,
    val name: String? = null,
)
