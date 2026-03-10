package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.notifier.router.bot.service.SubscribeResult
import com.notifier.router.bot.service.SubscriptionService
import com.notifier.router.bot.service.UnsubscribeResult
import com.notifier.router.common.domain.Filter
import com.notifier.router.common.dto.FilterDefinitionDto
import com.notifier.router.common.dto.NotificationTypeDto
import com.notifier.router.common.dto.SubscriptionDto
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.SlashCommandContext
import com.slack.api.bolt.request.builtin.SlashCommandRequest
import com.slack.api.bolt.response.Response
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.views.ViewsOpenRequest
import com.slack.api.methods.response.views.ViewsOpenResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.web.client.RestClientException
import java.util.regex.Pattern

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Suppress("LargeClass")
class SlashCommandHandlersTest {
    @Mock
    private lateinit var app: App

    @Mock
    private lateinit var apiClient: RouterApiClient

    @Mock
    private lateinit var subscriptionService: SubscriptionService

    @InjectMocks
    private lateinit var handlers: SlashCommandHandlers

    @Mock
    private lateinit var req: SlashCommandRequest

    @Mock
    private lateinit var ctx: SlashCommandContext

    @Mock
    private lateinit var payload:
        com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload

    @BeforeEach
    fun setup() {
        whenever(req.payload).thenReturn(payload)
    }

    @Test
    fun `test empty command opens modal list`() {
        // Arrange
        whenever(payload.text).thenReturn("")
        whenever(payload.triggerId).thenReturn("trigger_123")

        val mockTypes = listOf(NotificationTypeDto(name = "PR Created", key = "pr_created"))
        whenever(apiClient.getNotificationTypes()).thenReturn(mockTypes)

        val methodsClient = mock<MethodsClient>()
        whenever(ctx.client()).thenReturn(methodsClient)

        val viewsOpenResponse = ViewsOpenResponse().apply { isOk = true }
        org.mockito
            .kotlin
            .doReturn(viewsOpenResponse)
            .whenever(methodsClient)
            .viewsOpen(any<ViewsOpenRequest>())
        org.mockito.kotlin
            .doReturn(Response.ok())
            .whenever(ctx)
            .ack()

        // Act
        // Reflection is needed here to invoke the private handleCommand method since Bolt lambda
        // registration happens at startup.
        val method =
            SlashCommandHandlers::class.java.getDeclaredMethod(
                "handleCommand",
                SlashCommandRequest::class.java,
                SlashCommandContext::class.java,
            )
        method.isAccessible = true
        val response = method.invoke(handlers, req, ctx) as Response

        // Assert
        assertEquals(200, response.statusCode)
        verify(apiClient).getNotificationTypes()
        verify(methodsClient).viewsOpen(any<ViewsOpenRequest>())
        verify(ctx).ack()
    }

    @Test
    fun `registerHandlers registers slash command with a pattern not an exact string`() {
        handlers.registerHandlers()

        verify(app).command(
            org.mockito.kotlin.check<Pattern> { pattern ->
                assertTrue(pattern.matcher("/notifyme").matches(), "/notifyme should match")
                assertTrue(pattern.matcher("/notifymejuan").matches(), "/notifymejuan should match")
                assertTrue(pattern.matcher("/notifymeraul").matches(), "/notifymeraul should match")
                assertTrue(pattern.matcher("/notifyme123").matches(), "/notifyme123 should match")
                assertFalse(pattern.matcher("/subscribe").matches(), "/subscribe should not match")
                assertFalse(pattern.matcher("/notify").matches(), "/notify should not match")
            },
            any(),
        )
    }

    @Test
    fun `test help command returns instructions`() {
        // Arrange
        whenever(payload.text).thenReturn("help")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())

        // Act
        val method =
            SlashCommandHandlers::class.java.getDeclaredMethod(
                "handleCommand",
                SlashCommandRequest::class.java,
                SlashCommandContext::class.java,
            )
        method.isAccessible = true
        val response = method.invoke(handlers, req, ctx) as Response

        // Assert
        assertEquals(200, response.statusCode)
        verify(ctx)
            .ack(org.mockito.kotlin.check<String> { assertTrue(it.contains("/notifyme help")) })
    }

    private fun invokeHandleCommand(): Response {
        val method =
            SlashCommandHandlers::class.java.getDeclaredMethod(
                "handleCommand",
                SlashCommandRequest::class.java,
                SlashCommandContext::class.java,
            )
        method.isAccessible = true
        return method.invoke(handlers, req, ctx) as Response
    }

    @Test
    fun `unsubscribe with no args returns usage hint`() {
        whenever(payload.text).thenReturn("unsubscribe")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(org.mockito.kotlin.check<String> { assertTrue(it.contains("pr_created")) })
    }

    @Test
    fun `unsubscribe with unknown type returns available types`() {
        whenever(payload.text).thenReturn("unsubscribe unknown_type")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(
            org.mockito.kotlin.check<String> {
                assertTrue(it.contains("unknown_type"))
                assertTrue(it.contains("pr_created"))
            },
        )
    }

    @Test
    fun `unsubscribe with valid type and active subscription succeeds`() {
        whenever(payload.text).thenReturn("unsubscribe pr_created")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(subscriptionService.unsubscribe("U03SRNCB1HS", "type-1"))
            .thenReturn(UnsubscribeResult.Success)

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(
            org.mockito.kotlin.check<String> {
                assertTrue(it.contains("pr_created"))
                assertTrue(it.lowercase().contains("unsubscribed"))
            },
        )
    }

    @Test
    fun `unsubscribe when not subscribed returns not-subscribed message`() {
        whenever(payload.text).thenReturn("unsubscribe pr_created")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(subscriptionService.unsubscribe("U03SRNCB1HS", "type-1"))
            .thenReturn(UnsubscribeResult.NotSubscribed)

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(
            org.mockito.kotlin.check<String> {
                assertTrue(it.contains("pr_created"))
                assertTrue(it.lowercase().contains("no active subscription"))
            },
        )
    }

    @Test
    fun `unsubscribe on api failure returns error message`() {
        whenever(payload.text).thenReturn("unsubscribe pr_created")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(subscriptionService.unsubscribe("U03SRNCB1HS", "type-1"))
            .thenReturn(UnsubscribeResult.Failure)

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(
            org.mockito.kotlin.check<String> { assertTrue(it.contains("❌")) },
        )
    }

    // ── subscribe tests ────────────────────────────────────────────────────────

    @Test
    fun `subscribe with no args returns usage hint`() {
        whenever(payload.text).thenReturn("subscribe")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(org.mockito.kotlin.check<String> { assertTrue(it.contains("pr_created")) })
    }

    @Test
    fun `subscribe with unknown type returns available types`() {
        whenever(payload.text).thenReturn("subscribe unknown_type")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(
            org.mockito.kotlin.check<String> {
                assertTrue(it.contains("unknown_type"))
                assertTrue(it.contains("pr_created"))
            },
        )
    }

    @Test
    fun `subscribe without filters succeeds`() {
        whenever(payload.text).thenReturn("subscribe pr_created")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(subscriptionService.subscribeAndActivate(any(), any(), any(), any(), any()))
            .thenReturn(
                SubscribeResult.Success(
                    SubscriptionDto(
                        id = "sub-1",
                        userId = "U03SRNCB1HS",
                        notificationTypeId = "type-1",
                        channels = listOf("slack_dm"),
                        channelConfig = emptyMap(),
                        filters = emptyList(),
                        enabled = true
                    )
                )
            )

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(org.mockito.kotlin.check<String> { assertTrue(it.contains("pr_created")) })
    }

    @Test
    fun `subscribe with single EQ filter succeeds`() {
        whenever(payload.text).thenReturn("subscribe pr_created repo=api")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(apiClient.getFiltersForType("pr_created")).thenReturn(
            listOf(
                FilterDefinitionDto(
                    id = "f-1",
                    notificationTypeId = "type-1",
                    field = "repo",
                    fieldType = "STRING",
                    operators = listOf("EQ", "IN")
                )
            ),
        )
        whenever(subscriptionService.subscribeAndActivate(any(), any(), any(), any(), any()))
            .thenReturn(
                SubscribeResult.Success(
                    SubscriptionDto(
                        id = "sub-1",
                        userId = "U03SRNCB1HS",
                        notificationTypeId = "type-1",
                        channels = listOf("slack_dm"),
                        channelConfig = emptyMap(),
                        filters = listOf(Filter("repo", "EQ", "api")),
                        enabled = true
                    )
                )
            )

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(subscriptionService).subscribeAndActivate(
            userId = any(),
            notificationTypeId = any(),
            channels = any(),
            filters = org.mockito.kotlin.check { filters ->
                assertEquals(1, filters.size)
                assertEquals("repo", filters[0].field)
                assertEquals("EQ", filters[0].operator)
                assertEquals("api", filters[0].value)
            },
            channelConfig = any(),
        )
    }

    @Test
    fun `subscribe with comma-separated value uses IN operator`() {
        whenever(payload.text).thenReturn("subscribe pr_created repo=api,web")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(apiClient.getFiltersForType("pr_created")).thenReturn(
            listOf(
                FilterDefinitionDto(
                    id = "f-1",
                    notificationTypeId = "type-1",
                    field = "repo",
                    fieldType = "STRING",
                    operators = listOf("EQ", "IN")
                )
            ),
        )
        whenever(subscriptionService.subscribeAndActivate(any(), any(), any(), any(), any()))
            .thenReturn(
                SubscribeResult.Success(
                    SubscriptionDto(
                        id = "sub-1",
                        userId = "U03SRNCB1HS",
                        notificationTypeId = "type-1",
                        channels = listOf("slack_dm"),
                        channelConfig = emptyMap(),
                        filters = emptyList(),
                        enabled = true
                    )
                )
            )

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(subscriptionService).subscribeAndActivate(
            userId = any(),
            notificationTypeId = any(),
            channels = any(),
            filters = org.mockito.kotlin.check { filters ->
                assertEquals(1, filters.size)
                assertEquals("repo", filters[0].field)
                assertEquals("IN", filters[0].operator)
                @Suppress("UNCHECKED_CAST")
                assertEquals(listOf("api", "web"), filters[0].value as List<String>)
            },
            channelConfig = any(),
        )
    }

    @Test
    fun `subscribe with malformed filter returns error`() {
        whenever(payload.text).thenReturn("subscribe pr_created invalid_filter")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(apiClient.getFiltersForType("pr_created")).thenReturn(emptyList())

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(
            org.mockito.kotlin.check<String> {
                assertTrue(it.contains("invalid_filter"))
                assertTrue(it.lowercase().contains("field=value"))
            },
        )
    }

    @Test
    fun `subscribe when already subscribed returns friendly message`() {
        whenever(payload.text).thenReturn("subscribe pr_created")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(subscriptionService.subscribeAndActivate(any(), any(), any(), any(), any()))
            .thenReturn(SubscribeResult.AlreadySubscribed)

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(
            org.mockito.kotlin.check<String> {
                assertTrue(it.contains("pr_created"))
                assertTrue(it.lowercase().contains("already subscribed"))
            }
        )
    }

    @Test
    fun `subscribe on service failure returns error message`() {
        whenever(payload.text).thenReturn("subscribe pr_created")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(subscriptionService.subscribeAndActivate(any(), any(), any(), any(), any()))
            .thenReturn(SubscribeResult.Failure)

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(org.mockito.kotlin.check<String> { assertTrue(it.contains("❌")) })
    }

    @Test
    fun `subscribe with multiple EQ filters sends all filters to service`() {
        whenever(payload.text).thenReturn("subscribe pr_created repo=api author=johndoe")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(apiClient.getFiltersForType("pr_created")).thenReturn(
            listOf(
                FilterDefinitionDto(
                    id = "f-1",
                    notificationTypeId = "type-1",
                    field = "repo",
                    fieldType = "STRING",
                    operators = listOf("EQ")
                ),
                FilterDefinitionDto(
                    id = "f-2",
                    notificationTypeId = "type-1",
                    field = "author",
                    fieldType = "STRING",
                    operators = listOf("EQ")
                ),
            ),
        )
        whenever(subscriptionService.subscribeAndActivate(any(), any(), any(), any(), any()))
            .thenReturn(
                SubscribeResult.Success(
                    SubscriptionDto(
                        id = "sub-1",
                        userId = "U03SRNCB1HS",
                        notificationTypeId = "type-1",
                        channels = listOf("slack_dm"),
                        channelConfig = emptyMap(),
                        filters = emptyList(),
                        enabled = true
                    )
                )
            )

        invokeHandleCommand()

        verify(subscriptionService).subscribeAndActivate(
            userId = any(),
            notificationTypeId = any(),
            channels = any(),
            filters = org.mockito.kotlin.check { filters ->
                assertEquals(2, filters.size)
                assertEquals(Filter("repo", "EQ", "api"), filters[0])
                assertEquals(Filter("author", "EQ", "johndoe"), filters[1])
            },
            channelConfig = any(),
        )
    }

    @Test
    fun `subscribe with mixed EQ and IN filters sends correct operators`() {
        whenever(payload.text).thenReturn("subscribe pr_created repo=api,web author=johndoe")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(apiClient.getFiltersForType("pr_created")).thenReturn(
            listOf(
                FilterDefinitionDto(
                    id = "f-1",
                    notificationTypeId = "type-1",
                    field = "repo",
                    fieldType = "STRING",
                    operators = listOf("EQ", "IN")
                ),
                FilterDefinitionDto(
                    id = "f-2",
                    notificationTypeId = "type-1",
                    field = "author",
                    fieldType = "STRING",
                    operators = listOf("EQ")
                ),
            ),
        )
        whenever(subscriptionService.subscribeAndActivate(any(), any(), any(), any(), any()))
            .thenReturn(
                SubscribeResult.Success(
                    SubscriptionDto(
                        id = "sub-1",
                        userId = "U03SRNCB1HS",
                        notificationTypeId = "type-1",
                        channels = listOf("slack_dm"),
                        channelConfig = emptyMap(),
                        filters = emptyList(),
                        enabled = true
                    )
                )
            )

        invokeHandleCommand()

        verify(subscriptionService).subscribeAndActivate(
            userId = any(),
            notificationTypeId = any(),
            channels = any(),
            filters = org.mockito.kotlin.check { filters ->
                assertEquals(2, filters.size)
                assertEquals("IN", filters[0].operator)
                @Suppress("UNCHECKED_CAST")
                assertEquals(listOf("api", "web"), filters[0].value as List<String>)
                assertEquals("EQ", filters[1].operator)
                assertEquals("johndoe", filters[1].value)
            },
            channelConfig = any(),
        )
    }

    @Test
    fun `subscribe when filter definition API throws proceeds without field validation`() {
        whenever(payload.text).thenReturn("subscribe pr_created any_field=any_value")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(apiClient.getFiltersForType("pr_created"))
            .thenThrow(RestClientException("connection refused"))
        whenever(subscriptionService.subscribeAndActivate(any(), any(), any(), any(), any()))
            .thenReturn(
                SubscribeResult.Success(
                    SubscriptionDto(
                        id = "sub-1",
                        userId = "U03SRNCB1HS",
                        notificationTypeId = "type-1",
                        channels = listOf("slack_dm"),
                        channelConfig = emptyMap(),
                        filters = emptyList(),
                        enabled = true
                    )
                )
            )

        invokeHandleCommand()

        // Field validation was skipped — filter still passed through to the service
        verify(subscriptionService).subscribeAndActivate(
            userId = any(),
            notificationTypeId = any(),
            channels = any(),
            filters = org.mockito.kotlin.check { filters ->
                assertEquals(1, filters.size)
                assertEquals("any_field", filters[0].field)
                assertEquals("any_value", filters[0].value)
            },
            channelConfig = any(),
        )
    }

    @Test
    fun `subscribe with empty filter definitions allows any field name`() {
        whenever(payload.text).thenReturn("subscribe pr_created custom_field=hello")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(apiClient.getFiltersForType("pr_created")).thenReturn(emptyList())
        whenever(subscriptionService.subscribeAndActivate(any(), any(), any(), any(), any()))
            .thenReturn(
                SubscribeResult.Success(
                    SubscriptionDto(
                        id = "sub-1",
                        userId = "U03SRNCB1HS",
                        notificationTypeId = "type-1",
                        channels = listOf("slack_dm"),
                        channelConfig = emptyMap(),
                        filters = emptyList(),
                        enabled = true
                    )
                )
            )

        invokeHandleCommand()

        verify(subscriptionService).subscribeAndActivate(
            userId = any(),
            notificationTypeId = any(),
            channels = any(),
            filters = org.mockito.kotlin.check { filters ->
                assertEquals(1, filters.size)
                assertEquals("custom_field", filters[0].field)
            },
            channelConfig = any(),
        )
    }

    @Test
    fun `subscribe stops at first malformed filter and does not call service`() {
        whenever(payload.text).thenReturn("subscribe pr_created repo=api bad_filter author=johndoe")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(apiClient.getFiltersForType("pr_created")).thenReturn(emptyList())

        invokeHandleCommand()

        verify(ctx).ack(
            org.mockito.kotlin.check<String> {
                assertTrue(it.contains("bad_filter"))
                assertTrue(it.lowercase().contains("field=value"))
            }
        )
        verify(subscriptionService, org.mockito.Mockito.never()).subscribeAndActivate(any(), any(), any(), any(), any())
    }

    @Test
    fun `subscribe with filter value containing equals sign splits on first equals only`() {
        whenever(payload.text).thenReturn("subscribe pr_created label=feat=ure")
        whenever(payload.userId).thenReturn("U03SRNCB1HS")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(apiClient.getFiltersForType("pr_created")).thenReturn(emptyList())
        whenever(subscriptionService.subscribeAndActivate(any(), any(), any(), any(), any()))
            .thenReturn(
                SubscribeResult.Success(
                    SubscriptionDto(
                        id = "sub-1",
                        userId = "U03SRNCB1HS",
                        notificationTypeId = "type-1",
                        channels = listOf("slack_dm"),
                        channelConfig = emptyMap(),
                        filters = emptyList(),
                        enabled = true
                    )
                )
            )

        invokeHandleCommand()

        // split("=", limit = 2) keeps everything after the first = as the value
        verify(subscriptionService).subscribeAndActivate(
            userId = any(),
            notificationTypeId = any(),
            channels = any(),
            filters = org.mockito.kotlin.check { filters ->
                assertEquals(1, filters.size)
                assertEquals("label", filters[0].field)
                assertEquals("feat=ure", filters[0].value)
            },
            channelConfig = any(),
        )
    }

    @Test
    fun `subscribe with unknown filter field returns error listing valid fields`() {
        whenever(payload.text).thenReturn("subscribe pr_created unknown_field=value")
        whenever(ctx.ack(any<String>())).thenReturn(Response.ok())
        whenever(apiClient.getNotificationTypes()).thenReturn(
            listOf(NotificationTypeDto(id = "type-1", key = "pr_created", name = "PR Created")),
        )
        whenever(apiClient.getFiltersForType("pr_created")).thenReturn(
            listOf(
                FilterDefinitionDto(
                    id = "f-1",
                    notificationTypeId = "type-1",
                    field = "repo",
                    fieldType = "STRING",
                    operators = listOf("EQ")
                )
            ),
        )

        val response = invokeHandleCommand()

        assertEquals(200, response.statusCode)
        verify(ctx).ack(
            org.mockito.kotlin.check<String> {
                assertTrue(it.contains("unknown_field"))
                assertTrue(it.contains("repo"))
            },
        )
    }
}
