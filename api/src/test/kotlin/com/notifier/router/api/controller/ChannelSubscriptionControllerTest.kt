package com.notifier.router.api.controller

import com.notifier.router.api.service.ChannelSubscriptionService
import com.notifier.router.common.dto.ChannelSubscriptionDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
class ChannelSubscriptionControllerTest {
    @Mock private lateinit var channelSubscriptionService: ChannelSubscriptionService

    @InjectMocks private lateinit var channelSubscriptionController: ChannelSubscriptionController

    @Test
    fun `test createChannelSubscription returns created subscription`() {
        val channelSubscriptionDto =
            ChannelSubscriptionDto(
                id = null,
                slackChannelId = "C12345",
                slackChannelName = "general",
                notificationTypeId = "${java.util.UUID.randomUUID()}",
                filters = emptyList(),
                digestEnabled = false,
                digestInterval = "24h",
                enabled = true,
            )

        val expectedDto = channelSubscriptionDto.copy(id = "${java.util.UUID.randomUUID()}")

        whenever(
            channelSubscriptionService.createChannelSubscription(
                any(),
            ),
        ).thenReturn(expectedDto)

        val result =
            channelSubscriptionController.createChannelSubscription(
                channelSubscriptionDto,
            )

        assert(result.statusCode == HttpStatus.OK)
        assert(result.body != null)
        assert(result.body!!.slackChannelId == channelSubscriptionDto.slackChannelId)
    }

    @Test
    fun `test getChannelSubscriptionsByChannelId returns subscriptions`() {
        val slackChannelId = "C12345"
        val expectedDto =
            ChannelSubscriptionDto(
                id = "${java.util.UUID.randomUUID()}",
                slackChannelId = slackChannelId,
                slackChannelName = "general",
                notificationTypeId = "${java.util.UUID.randomUUID()}",
                filters = emptyList(),
                digestEnabled = false,
                digestInterval = "24h",
                enabled = true,
            )

        whenever(
            channelSubscriptionService.getChannelSubscriptionsByChannelId(
                any(),
            ),
        ).thenReturn(listOf(expectedDto))

        val result =
            channelSubscriptionController.getChannelSubscriptionsByChannelId(
                slackChannelId,
            )

        assert(result.statusCode == HttpStatus.OK)
        assert(result.body != null)
        assert(result.body!!.size == 1)
        assert(result.body!!.first().slackChannelId == slackChannelId)
    }

    @Test
    fun `test deleteChannelSubscription returns no content`() {
        val id = "${java.util.UUID.randomUUID()}"

        val result = channelSubscriptionController.deleteChannelSubscription(id)

        assert(result.statusCode == HttpStatus.NO_CONTENT)
        assert(result.body == null)
    }
}
