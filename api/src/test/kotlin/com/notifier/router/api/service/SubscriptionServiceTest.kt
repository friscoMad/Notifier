package com.notifier.router.api.service

import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.domain.User
import com.notifier.router.api.exception.SubscriptionNotFoundException
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import com.notifier.router.common.domain.Filter
import com.notifier.router.common.dto.SubscriptionDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SubscriptionServiceTest {
    @Mock private lateinit var subscriptionRepository: SubscriptionRepository

    @Mock private lateinit var notificationTypeRepository: NotificationTypeRepository

    @Mock private lateinit var userRepository: UserRepository

    @Mock private lateinit var novuService: NovuService

    @InjectMocks private lateinit var subscriptionService: SubscriptionService

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
    fun `test deleteSubscription deletes subscription and cleans up Novu when last subscription`() {
        val id = UUID.randomUUID()
        val userUuid = UUID.randomUUID()
        val slackId = "U12345"
        val subscription = Subscription(
            id = id,
            userId = userUuid,
            notificationTypeId = UUID.randomUUID(),
            channels = listOf("slack_dm"),
            channelConfig = emptyMap(),
            filters = emptyList(),
            enabled = true,
        )
        val mockUser = User(id = userUuid, slackId = slackId)

        whenever(subscriptionRepository.findById(id)).thenReturn(Optional.of(subscription))
        whenever(subscriptionRepository.findByUserId(userUuid)).thenReturn(emptyList())
        whenever(userRepository.findById(userUuid)).thenReturn(Optional.of(mockUser))

        subscriptionService.deleteSubscription(id.toString())

        verify(subscriptionRepository).deleteById(id)
        verify(novuService).cleanupSubscriber(slackId)
    }

    @Test
    fun `test deleteSubscription does not clean up Novu when other subscriptions remain`() {
        val id = UUID.randomUUID()
        val userUuid = UUID.randomUUID()
        val subscription = Subscription(
            id = id,
            userId = userUuid,
            notificationTypeId = UUID.randomUUID(),
            channels = listOf("slack_dm"),
            channelConfig = emptyMap(),
            filters = emptyList(),
            enabled = true,
        )
        val remaining = Subscription(
            id = UUID.randomUUID(),
            userId = userUuid,
            notificationTypeId = UUID.randomUUID(),
            channels = listOf("slack_dm"),
            channelConfig = emptyMap(),
            filters = emptyList(),
            enabled = true,
        )

        whenever(subscriptionRepository.findById(id)).thenReturn(Optional.of(subscription))
        whenever(subscriptionRepository.findByUserId(userUuid)).thenReturn(listOf(remaining))

        subscriptionService.deleteSubscription(id.toString())

        verify(subscriptionRepository).deleteById(id)
        verify(novuService, org.mockito.Mockito.never()).cleanupSubscriber(any())
    }

    @Test
    fun `test deleteSubscription throws exception when subscription not found`() {
        val id = UUID.randomUUID().toString()
        whenever(subscriptionRepository.findById(UUID.fromString(id))).thenReturn(Optional.empty())

        assertThrows<SubscriptionNotFoundException> {
            subscriptionService.deleteSubscription(id)
        }
    }

    @Test
    fun `createSubscription stores email on new user`() {
        val slackId = "U99999"
        val email = "user@example.com"
        val userUuid = UUID.randomUUID()
        val dto =
            SubscriptionDto(
                userId = slackId,
                email = email,
                notificationTypeId = "${UUID.randomUUID()}",
                channels = listOf("slack_dm")
            )

        whenever(userRepository.findBySlackId(slackId)).thenReturn(null)
        whenever(userRepository.save(any<User>())).thenAnswer { it.getArgument(0) }
        whenever(subscriptionRepository.save(any<Subscription>())).thenAnswer {
            Subscription(
                id = UUID.randomUUID(),
                userId = userUuid,
                notificationTypeId = UUID.fromString(dto.notificationTypeId),
                channels = dto.channels,
                channelConfig = emptyMap(),
                filters = emptyList(),
                enabled = true,
            )
        }

        subscriptionService.createSubscription(dto)

        verify(userRepository).save(check<User> { assertEquals(email, it.email) })
    }

    @Test
    fun `createSubscription updates email on existing user when provided`() {
        val slackId = "U88888"
        val userUuid = UUID.randomUUID()
        val existingUser = User(id = userUuid, slackId = slackId, email = null)
        val dto =
            SubscriptionDto(
                userId = slackId,
                email = "new@example.com",
                notificationTypeId = "${UUID.randomUUID()}",
                channels = listOf("slack_dm")
            )

        whenever(userRepository.findBySlackId(slackId)).thenReturn(existingUser)
        whenever(userRepository.save(any<User>())).thenAnswer { it.getArgument(0) }
        whenever(subscriptionRepository.save(any<Subscription>())).thenAnswer {
            Subscription(
                id = UUID.randomUUID(),
                userId = userUuid,
                notificationTypeId = UUID.fromString(dto.notificationTypeId),
                channels = dto.channels,
                channelConfig = emptyMap(),
                filters = emptyList(),
                enabled = true
            )
        }

        subscriptionService.createSubscription(dto)

        verify(userRepository).save(check<User> { assertEquals("new@example.com", it.email) })
    }

    @Test
    fun `createSubscription keeps existing user when dto email is null`() {
        val slackId = "U77777"
        val userUuid = UUID.randomUUID()
        val existingUser = User(id = userUuid, slackId = slackId, email = "existing@example.com")
        val dto =
            SubscriptionDto(
                userId = slackId,
                email = null,
                notificationTypeId = "${UUID.randomUUID()}",
                channels = listOf("slack_dm")
            )

        whenever(userRepository.findBySlackId(slackId)).thenReturn(existingUser)
        whenever(subscriptionRepository.save(any<Subscription>())).thenAnswer {
            Subscription(
                id = UUID.randomUUID(),
                userId = userUuid,
                notificationTypeId = UUID.fromString(dto.notificationTypeId),
                channels = dto.channels,
                channelConfig = emptyMap(),
                filters = emptyList(),
                enabled = true
            )
        }

        subscriptionService.createSubscription(dto)

        verify(userRepository, never()).save(any<User>())
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
