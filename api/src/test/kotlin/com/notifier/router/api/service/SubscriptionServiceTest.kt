package com.notifier.router.api.service

import com.notifier.router.api.domain.Filter
import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.domain.User
import com.notifier.router.api.dto.SubscriptionDto
import com.notifier.router.api.exception.SubscriptionNotFoundException
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SubscriptionServiceTest {
    @Mock private lateinit var subscriptionRepository: SubscriptionRepository

    @Mock private lateinit var notificationTypeRepository: NotificationTypeRepository

    @Mock private lateinit var userRepository: UserRepository

    @Mock private lateinit var novuService: NovuService

    @org.mockito.InjectMocks private lateinit var subscriptionService: SubscriptionService

    @Test
    fun `test createSubscription saves new subscription with Slack ID`() {
        val slackId = "U12345"
        val subscriptionDto =
            SubscriptionDto(
                id = null,
                userId = slackId,
                notificationTypeId = "${UUID.randomUUID()}",
                channels = listOf("slack_dm"),
                channelConfig = emptyMap(),
                filters = emptyList(),
                enabled = true,
            )

        val userUuid = UUID.randomUUID()
        val mockUser = User(id = userUuid, slackId = slackId)

        val savedDomain =
            Subscription(
                id = UUID.randomUUID(),
                userId = userUuid,
                notificationTypeId = UUID.fromString(subscriptionDto.notificationTypeId),
                channels = subscriptionDto.channels,
                channelConfig = subscriptionDto.channelConfig,
                filters = emptyList(),
                enabled = subscriptionDto.enabled,
            )

        whenever(userRepository.findBySlackId(slackId)).thenReturn(mockUser)
        whenever(subscriptionRepository.save(any<Subscription>())).thenReturn(savedDomain)

        val result = subscriptionService.createSubscription(subscriptionDto)

        verify(userRepository).findBySlackId(slackId)
        verify(subscriptionRepository).save(any())
        assert(result.userId == slackId)
        assert(result.channels == subscriptionDto.channels)
    }

    @Test
    fun `test updateSubscription updates existing subscription`() {
        val id = UUID.randomUUID().toString()
        val existingDomain =
            Subscription(
                id = UUID.fromString(id),
                userId = UUID.randomUUID(),
                notificationTypeId = UUID.randomUUID(),
                channels = listOf("slack_dm"),
                channelConfig = emptyMap(),
                filters = emptyList(),
                enabled = true,
            )

        val subscriptionDto =
            SubscriptionDto(
                id = id,
                userId = existingDomain.userId.toString(),
                notificationTypeId = existingDomain.notificationTypeId.toString(),
                channels = listOf("email"),
                channelConfig = emptyMap(),
                filters = listOf(Filter("repo", "EQ", "api")),
                enabled = false,
            )

        val userUuid = existingDomain.userId
        val mockUser = User(id = userUuid, slackId = "U12345")

        whenever(userRepository.findById(userUuid)).thenReturn(Optional.of(mockUser))
        whenever(subscriptionRepository.findById(any())).thenReturn(Optional.of(existingDomain))
        whenever(subscriptionRepository.save(any<Subscription>())).thenAnswer { it.getArgument(0) }

        val result = subscriptionService.updateSubscription(id, subscriptionDto)

        verify(subscriptionRepository).save(any())
        assert(result.channels == subscriptionDto.channels)
        assert(result.filters.size == 1)
        assert(result.enabled == subscriptionDto.enabled)
    }

    @Test
    fun `test updateSubscription throws exception when subscription not found`() {
        val id = UUID.randomUUID().toString()
        val subscriptionDto =
            SubscriptionDto(
                id = id,
                userId = "${UUID.randomUUID()}",
                notificationTypeId = "${UUID.randomUUID()}",
                channels = listOf("slack_dm"),
                channelConfig = emptyMap(),
                filters = emptyList(),
                enabled = true,
            )

        whenever(subscriptionRepository.findById(any())).thenReturn(Optional.empty())

        assertThrows<SubscriptionNotFoundException> {
            subscriptionService.updateSubscription(id, subscriptionDto)
        }
    }

    @Test
    fun `test deleteSubscription deletes subscription`() {
        val id = UUID.randomUUID().toString()

        subscriptionService.deleteSubscription(id)

        verify(subscriptionRepository).deleteById(UUID.fromString(id))
    }

    @Test
    fun `test getSubscriptionsByUserId returns subscriptions for Slack ID`() {
        val slackId = "U12345"
        val userUuid = UUID.randomUUID()
        val mockUser = User(id = userUuid, slackId = slackId)

        val subscription1 =
            Subscription(
                id = UUID.randomUUID(),
                userId = userUuid,
                notificationTypeId = UUID.randomUUID(),
                channels = listOf("slack_dm"),
                channelConfig = emptyMap(),
                filters = emptyList(),
                enabled = true,
            )

        whenever(userRepository.findBySlackId(slackId)).thenReturn(mockUser)
        whenever(subscriptionRepository.findByUserId(userUuid)).thenReturn(listOf(subscription1))

        val result = subscriptionService.getSubscriptionsByUserId(slackId)

        verify(userRepository).findBySlackId(slackId)
        verify(subscriptionRepository).findByUserId(userUuid)
        assert(result.size == 1)
        assert(result[0].userId == slackId)
    }
}
