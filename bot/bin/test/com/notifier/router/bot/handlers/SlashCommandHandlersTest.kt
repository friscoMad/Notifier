package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.SlashCommandContext
import com.slack.api.bolt.request.builtin.SlashCommandRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any

@ExtendWith(MockitoExtension::class)
class SlashCommandHandlersTest {
    @Mock private lateinit var app: App

    @Mock private lateinit var apiClient: RouterApiClient

    @Mock private lateinit var req: SlashCommandRequest

    @Mock private lateinit var ctx: SlashCommandContext

    @Mock private lateinit var payload: SlashCommandPayload

    private lateinit var handlers: SlashCommandHandlers

    @BeforeEach
    fun setup() {
        handlers = SlashCommandHandlers(app, apiClient)
        // Handlers only register closures to app, so testing the handleCommand is via reflection if
        // it's private.
        // Or we can just call registerHandlers to ensure it doesn't crash.
    }

    @Test
    fun `registerHandlers attaches command to app`() {
        handlers.registerHandlers()
        verify(app).command(eq("/notifyme"), any())
    }
}
