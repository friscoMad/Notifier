package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.notifier.router.common.dto.NotificationTypeDto
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.SlashCommandContext
import com.slack.api.bolt.request.builtin.SlashCommandRequest
import com.slack.api.bolt.response.Response
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.views.ViewsOpenRequest
import com.slack.api.methods.response.views.ViewsOpenResponse
import org.junit.jupiter.api.Assertions.assertEquals
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

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlashCommandHandlersTest {
    @Mock
    private lateinit var app: App

    @Mock
    private lateinit var apiClient: RouterApiClient

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
}
