package com.notifier.router.api.controller

import com.notifier.router.api.dto.UserDto
import com.notifier.router.api.service.UserService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
class UserControllerControllerTest {
    @Mock private lateinit var userService: UserService

    @org.mockito.InjectMocks private lateinit var userController: UserController

    @Test
    fun `test createUser returns created user`() {
        val userDto =
            UserDto(
                id = null,
                slackId = "U12345",
                slackTeamId = "T12345",
                email = "test@example.com",
                name = "Test User",
            )

        val expectedDto = userDto.copy(id = "${java.util.UUID.randomUUID()}")

        whenever(userService.createUser(any())).thenReturn(expectedDto)

        val result = userController.createUser(userDto)

        assert(result.statusCode == HttpStatus.OK)
        assert(result.body != null)
        assert(result.body!!.slackId == userDto.slackId)
    }

    @Test
    fun `test getUserBySlackId returns user when found`() {
        val slackId = "U12345"
        val expectedDto =
            UserDto(
                id = "${java.util.UUID.randomUUID()}",
                slackId = slackId,
                slackTeamId = "T12345",
                email = "test@example.com",
                name = "Test User",
            )

        whenever(userService.getUserBySlackId(any())).thenReturn(expectedDto)

        val result = userController.getUserBySlackId(slackId)

        assert(result.statusCode == HttpStatus.OK)
        assert(result.body != null)
        assert(result.body!!.slackId == slackId)
    }

    @Test
    fun `test getUserBySlackId returns not found when user not exists`() {
        val slackId = "U54321"

        whenever(userService.getUserBySlackId(any())).thenReturn(null)

        val result = userController.getUserBySlackId(slackId)

        assert(result.statusCode == HttpStatus.NOT_FOUND)
        assert(result.body == null)
    }
}
