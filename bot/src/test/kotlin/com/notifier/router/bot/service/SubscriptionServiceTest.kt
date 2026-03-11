package com.notifier.router.bot.service

import com.notifier.router.bot.client.RouterApiClient
import com.notifier.router.common.dto.SubscriptionDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class SubscriptionServiceTest {
    private lateinit var apiClient: RouterApiClient
    private lateinit var service: SubscriptionService

    @BeforeEach
    fun setup() {
        apiClient = mock()
        service = SubscriptionService(apiClient)
    }

    private fun stubSuccessfulSubscribe(userId: String = "U123", typeId: String = UUID.randomUUID().toString()) =
        SubscriptionDto(id = "sub-1", userId = userId, notificationTypeId = typeId, channels = listOf("slack_dm"))

    // ── subscribeAndActivate ──────────────────────────────────────────────────

    @Test
    fun `email is included in subscription dto when provided`() {
        whenever(apiClient.subscribe(any())).thenReturn(stubSuccessfulSubscribe())

        service.subscribeAndActivate(
            userId = "U123",
            email = "user@example.com",
            notificationTypeId = UUID.randomUUID().toString(),
            channels = listOf("slack_dm"),
        )

        verify(apiClient).subscribe(
            check { dto -> assertEquals("user@example.com", dto.email) },
        )
    }

    @Test
    fun `null email is forwarded when not provided`() {
        whenever(apiClient.subscribe(any())).thenReturn(stubSuccessfulSubscribe())

        service.subscribeAndActivate(
            userId = "U123",
            notificationTypeId = UUID.randomUUID().toString(),
            channels = listOf("slack_dm"),
        )

        verify(apiClient).subscribe(
            check { dto -> assertNull(dto.email) },
        )
    }

    @Test
    fun `returns Success when api call succeeds`() {
        val returned = stubSuccessfulSubscribe()
        whenever(apiClient.subscribe(any())).thenReturn(returned)

        val result = service.subscribeAndActivate(
            userId = "U123",
            notificationTypeId = returned.notificationTypeId,
            channels = listOf("slack_dm"),
        )

        assert(result is SubscribeResult.Success)
    }

    @Test
    fun `returns Failure when api throws`() {
        whenever(apiClient.subscribe(any())).thenThrow(org.springframework.web.client.RestClientException("boom"))

        val result = service.subscribeAndActivate(
            userId = "U123",
            notificationTypeId = UUID.randomUUID().toString(),
            channels = listOf("slack_dm"),
        )

        assert(result is SubscribeResult.Failure)
    }
}
