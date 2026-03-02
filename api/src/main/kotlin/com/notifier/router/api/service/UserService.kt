package com.notifier.router.api.service

import com.notifier.router.api.domain.User
import com.notifier.router.api.repository.UserRepository
import com.notifier.router.common.dto.UserDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createUser(dto: UserDto): UserDto =
        userRepository.findBySlackId(dto.slackId)?.toDto()
            ?: userRepository.save(dto.toDomain()).toDto()

    fun getUserBySlackId(slackId: String): UserDto? = userRepository.findBySlackId(slackId)?.toDto()

    private fun UserDto.toDomain() =
        User(
            id = UUID.randomUUID(),
            slackId = slackId,
            slackTeamId = slackTeamId,
            email = email,
            name = name,
        )

    private fun User.toDto() =
        UserDto(
            id = id.toString(),
            slackId = slackId,
            slackTeamId = slackTeamId,
            email = email,
            name = name,
        )
}
