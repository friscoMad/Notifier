package com.notifier.router.api.service

import com.notifier.router.api.domain.GenericEvent
import com.notifier.router.api.domain.NotificationType
import com.notifier.router.api.domain.Subscription
import com.notifier.router.api.domain.User
import com.notifier.router.api.repository.ChannelSubscriptionRepository
import com.notifier.router.api.repository.NotificationTypeRepository
import com.notifier.router.api.repository.SubscriptionRepository
import com.notifier.router.api.repository.UserRepository
import com.notifier.router.common.domain.Filter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class EventServiceTest {
    @Mock private lateinit var notificationTypeRepository: NotificationTypeRepository

    @Mock private lateinit var subscriptionRepository: SubscriptionRepository

    @Mock private lateinit var channelSubscriptionRepository: ChannelSubscriptionRepository

    @Mock private lateinit var userRepository: UserRepository

    @Mock private lateinit var filterEvaluator: FilterEvaluator

    @Mock private lateinit var novuService: NovuService

    @Mock private lateinit var slackNotificationService: SlackNotificationService

    @InjectMocks private lateinit var eventService: EventService

    @Test
    fun `processEventAsync triggers Novu for matched subscribers`() {
        val typeId = UUID.randomUUID()
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()

        val event =
            GenericEvent(
                typeKey = "pr_created",
                metadata = mapOf("repo" to "api"),
                rawPayload = mapOf("pr_url" to "https://github.com/pull/1"),
            )

        val notifType =
            NotificationType(
                id = typeId,
                typeKey = "pr_created",
                name = "PR Created",
                description = "Opened PR",
            )

        val sub1 =
            Subscription(
                id = UUID.randomUUID(),
                userId = userId1,
                notificationTypeId = typeId,
                channels = listOf("slack_dm"),
                channelConfig = emptyMap(),
                filters = listOf(Filter(field = "repo", operator = "EQ", value = "api")),
            )

        val sub2 =
            Subscription(
                id = UUID.randomUUID(),
                userId = userId2,
                notificationTypeId = typeId,
                channels = listOf("slack_dm"),
                channelConfig = emptyMap(),
                filters = listOf(Filter(field = "repo", operator = "EQ", value = "web")),
            )

        val user1 =
            User(
                id = userId1,
                slackId = "U123",
                slackTeamId = "T1",
                email = "test1@x.com",
                name = "User 1",
            )

        whenever(notificationTypeRepository.findByTypeKey("pr_created")).thenReturn(notifType)
        whenever(subscriptionRepository.findByNotificationTypeId(typeId))
            .thenReturn(listOf(sub1, sub2))
        whenever(filterEvaluator.evaluate(event, sub1.filters)).thenReturn(true)
        whenever(filterEvaluator.evaluate(event, sub2.filters)).thenReturn(false)
        whenever(userRepository.findAllById(listOf(userId1))).thenReturn(listOf(user1))
        whenever(channelSubscriptionRepository.findByNotificationTypeId(typeId))
            .thenReturn(emptyList())

        eventService.processEventAsync(event)

        verify(novuService).triggerChannelWorkflow(
            eq("pr_created"),
            eq("in_app"),
            eq(listOf("U123")),
            eq(event.payload),
        )
        verify(novuService, never()).triggerChannelWorkflow(any(), eq("chat"), any(), any())
        verify(slackNotificationService).sendMessage(eq("U123"), eq(event.payload["content"] as String))
    }

    @Test
    fun `processEventAsync triggers digest workflow when subscription has digest enabled`() {
        val typeId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val event =
            GenericEvent(
                typeKey = "pr_created",
                metadata = mapOf("repo" to "api"),
                rawPayload = mapOf("pr_url" to "https://github.com/pull/1"),
            )

        val notifType =
            NotificationType(
                id = typeId,
                typeKey = "pr_created",
                name = "PR Created",
                description = "Opened PR",
            )

        val digestSub =
            Subscription(
                id = UUID.randomUUID(),
                userId = userId,
                notificationTypeId = typeId,
                channels = listOf("slack_dm"),
                channelConfig = mapOf("digest" to true, "digestInterval" to "1d"),
                filters = emptyList(),
            )

        val user =
            User(
                id = userId,
                slackId = "U123",
                slackTeamId = "T1",
                email = "test@x.com",
                name = "User",
            )

        whenever(notificationTypeRepository.findByTypeKey("pr_created")).thenReturn(notifType)
        whenever(subscriptionRepository.findByNotificationTypeId(typeId)).thenReturn(listOf(digestSub))
        whenever(filterEvaluator.evaluate(event, digestSub.filters)).thenReturn(true)
        whenever(userRepository.findAllById(listOf(userId))).thenReturn(listOf(user))
        whenever(channelSubscriptionRepository.findByNotificationTypeId(typeId)).thenReturn(emptyList())

        eventService.processEventAsync(event)

        // in_app is always immediate
        verify(novuService).triggerChannelWorkflow("pr_created", "in_app", listOf("U123"), event.payload)
        // chat is routed to digest workflow (12h interval → pr_created_chat_digest_12h)
        verify(novuService).triggerWorkflow("pr_created_chat_digest_1d", listOf("U123"), event.payload)
        // immediate chat workflow must NOT be triggered
        verify(novuService, never()).triggerChannelWorkflow(eq("pr_created"), eq("chat"), any(), any())
    }

    @Test
    fun `processEventAsync skips Novu trigger when no subscribers match`() {
        val typeId = UUID.randomUUID()

        val event =
            GenericEvent(typeKey = "pr_created", metadata = emptyMap())
        val notifType =
            NotificationType(
                id = typeId,
                typeKey = "pr_created",
                name = "PR",
                description = "PR",
            )

        whenever(notificationTypeRepository.findByTypeKey("pr_created")).thenReturn(notifType)
        whenever(subscriptionRepository.findByNotificationTypeId(typeId)).thenReturn(emptyList())
        whenever(channelSubscriptionRepository.findByNotificationTypeId(typeId))
            .thenReturn(emptyList())

        eventService.processEventAsync(event)

        verify(novuService, never()).triggerChannelWorkflow(any(), any(), any(), any())
        verify(slackNotificationService, never()).sendMessage(any(), any())
    }
}
