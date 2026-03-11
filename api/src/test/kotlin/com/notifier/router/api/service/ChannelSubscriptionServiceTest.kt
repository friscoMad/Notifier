package com.notifier.router.api.service

import com.notifier.router.api.domain.ChannelSubscription
import com.notifier.router.api.repository.ChannelSubscriptionRepository
import com.notifier.router.common.dto.ChannelSubscriptionDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ChannelSubscriptionServiceTest {
    @Mock private lateinit var channelSubscriptionRepository: ChannelSubscriptionRepository

    @org.mockito.InjectMocks
    private lateinit var channelSubscriptionService: ChannelSubscriptionService

    @Test
    fun `test createChannelSubscription saves new subscription`() {
        val channelSubscriptionDto =
            ChannelSubscriptionDto(
                id = null,
                slackChannelId = "C12345",
                slackChannelName = "general",
                notificationTypeId = "${UUID.randomUUID()}",
                filters = emptyList(),
                digestEnabled = false,
                digestInterval = "1w",
                enabled = true,
            )

        val savedDomain =
            ChannelSubscription(
                id = UUID.randomUUID(),
                slackChannelId = channelSubscriptionDto.slackChannelId,
                slackChannelName = channelSubscriptionDto.slackChannelName,
                notificationTypeId =
                UUID.fromString(channelSubscriptionDto.notificationTypeId),
                filters = emptyList(),
                digestEnabled = channelSubscriptionDto.digestEnabled,
                digestInterval = channelSubscriptionDto.digestInterval,
                enabled = channelSubscriptionDto.enabled,
            )

        whenever(channelSubscriptionRepository.save(any<ChannelSubscription>()))
            .thenReturn(savedDomain)

        val result = channelSubscriptionService.createChannelSubscription(channelSubscriptionDto)

        verify(channelSubscriptionRepository).save(any())
        assert(result.slackChannelId == channelSubscriptionDto.slackChannelId)
        assert(result.enabled == channelSubscriptionDto.enabled)
    }

    @Test
    fun `test deleteChannelSubscription deletes subscription`() {
        val id = UUID.randomUUID().toString()

        channelSubscriptionService.deleteChannelSubscription(id)

        verify(channelSubscriptionRepository).deleteById(UUID.fromString(id))
    }

    @Test
    fun `test getChannelSubscriptionsByChannelId returns subscriptions`() {
        val slackChannelId = "C12345"
        val subscription1 =
            ChannelSubscription(
                id = UUID.randomUUID(),
                slackChannelId = slackChannelId,
                slackChannelName = "general",
                notificationTypeId = UUID.randomUUID(),
                filters = emptyList(),
                digestEnabled = false,
                digestInterval = "1w",
                enabled = true,
            )
        val subscription2 =
            ChannelSubscription(
                id = UUID.randomUUID(),
                slackChannelId = slackChannelId,
                slackChannelName = "general",
                notificationTypeId = UUID.randomUUID(),
                filters = emptyList(),
                digestEnabled = false,
                digestInterval = "1w",
                enabled = true,
            )

        whenever(channelSubscriptionRepository.findBySlackChannelId(any()))
            .thenReturn(listOf(subscription1, subscription2))

        val result = channelSubscriptionService.getChannelSubscriptionsByChannelId(slackChannelId)

        verify(channelSubscriptionRepository).findBySlackChannelId(slackChannelId)
        assert(result.size == 2)
    }
}
