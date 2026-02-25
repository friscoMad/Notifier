package com.notifier.router.api.service

import com.notifier.router.api.domain.User
import com.notifier.router.api.dto.UserDto
import com.notifier.router.api.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UserServiceTest {
    @Mock private lateinit var userRepository: UserRepository

    @org.mockito.InjectMocks private lateinit var userService: UserService

    @Test
    fun `test createUser creates new user when not exists`() {
        val userDto =
            UserDto(
                id = null,
                slackId = "U12345",
                slackTeamId = "T12345",
                email = "test@example.com",
                name = "Test User",
            )

        whenever(userRepository.findBySlackId(any())).thenReturn(null)

        val savedUser =
            User(
                id = UUID.randomUUID(),
                slackId = userDto.slackId,
                slackTeamId = userDto.slackTeamId,
                email = userDto.email,
                name = userDto.name,
            )

        whenever(userRepository.save(any())).thenReturn(savedUser)

        val result = userService.createUser(userDto)

        verify(userRepository).findBySlackId(userDto.slackId)
        verify(userRepository).save(any())
        assert(result.slackId == userDto.slackId)
        assert(result.email == userDto.email)
    }

    @Test
    fun `test createUser returns existing user when found`() {
        val existingUser =
            User(
                id = UUID.randomUUID(),
                slackId = "U12345",
                slackTeamId = "T12345",
                email = "test@example.com",
                name = "Test User",
            )

        val userDto =
            UserDto(
                id = null,
                slackId = existingUser.slackId,
                slackTeamId = existingUser.slackTeamId,
                email = "newemail@example.com",
                name = "New Name",
            )

        whenever(userRepository.findBySlackId(any())).thenReturn(existingUser)

        val result = userService.createUser(userDto)

        verify(userRepository, never()).save(any())
        assert(result.slackId == existingUser.slackId)
        assert(result.email == existingUser.email)
    }

    @Test
    fun `test getUserBySlackId returns user when found`() {
        val user =
            User(
                id = UUID.randomUUID(),
                slackId = "U12345",
                slackTeamId = "T12345",
                email = "test@example.com",
                name = "Test User",
            )

        whenever(userRepository.findBySlackId(any())).thenReturn(user)

        val result = userService.getUserBySlackId(user.slackId)

        verify(userRepository).findBySlackId(user.slackId)
        assert(result != null)
        assert(result!!.slackId == user.slackId)
    }

    @Test
    fun `test getUserBySlackId returns null when not found`() {
        val slackId = "U54321"

        whenever(userRepository.findBySlackId(any())).thenReturn(null)

        val result = userService.getUserBySlackId(slackId)

        verify(userRepository).findBySlackId(slackId)
        assert(result == null)
    }
}
