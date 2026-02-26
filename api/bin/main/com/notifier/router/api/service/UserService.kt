package com.notifier.router.api.service

import com.notifier.router.api.domain.User
import com.notifier.router.api.dto.UserDto
import com.notifier.router.api.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createUser(userDto: UserDto): UserDto {
        val existingUser = userRepository.findBySlackId(userDto.slackId)
        if (existingUser != null) {
            return mapToDto(existingUser)
        }
        val user =
            User(
                id = UUID.randomUUID(),
                slackId = userDto.slackId,
                slackTeamId = userDto.slackTeamId,
                email = userDto.email,
                name = userDto.name,
            )

        val savedUser = userRepository.save(user)
        return mapToDto(savedUser)
    }

    fun getUserBySlackId(slackId: String): UserDto? {
        val user = userRepository.findBySlackId(slackId)
        return user?.let { mapToDto(it) }
    }

    private fun mapToDto(user: User): UserDto =
        UserDto(
            id = user.id.toString(),
            slackId = user.slackId,
            slackTeamId = user.slackTeamId,
            email = user.email,
            name = user.name,
        )
}
