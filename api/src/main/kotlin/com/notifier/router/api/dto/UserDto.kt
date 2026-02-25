package com.notifier.router.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class UserDto(
    @field:JsonProperty("id")
    val id: String? = null,
    @field:JsonProperty("slackId")
    @field:NotBlank
    val slackId: String,
    @field:JsonProperty("slackTeamId")
    val slackTeamId: String? = null,
    @field:JsonProperty("email")
    @field:Email
    val email: String? = null,
    @field:JsonProperty("name")
    val name: String? = null,
)
