package com.notifier.router.api.service

import co.novu.common.base.Novu
import com.fasterxml.jackson.databind.ObjectMapper
import com.notifier.router.api.novu.NovuApiClient
import com.notifier.router.api.novu.NovuChannelConnection
import com.notifier.router.api.novu.NovuChannelEndpoint
import com.notifier.router.api.novu.NovuIntegration
import com.notifier.router.api.novu.NovuSlackCredentials
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NovuServiceTest {
    private lateinit var apiClient: NovuApiClient
    private lateinit var service: NovuService

    @BeforeEach
    fun setup() {
        apiClient = mock()
        service =
            NovuService(
                apiKey = "test-key",
                apiUrl = "http://localhost:3000/v1",
                slackClientId = "client-id",
                slackClientSecret = "client-secret",
                slackApplicationId = "app-id",
                slackBotToken = "xoxb-token",
                slackWorkspaceId = "T123",
                slackWorkspaceName = "Test Workspace",
                objectMapper = ObjectMapper(),
            )
        val field = NovuService::class.java.getDeclaredField("novuApiClient")
        field.isAccessible = true
        field.set(service, apiClient)
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private fun markNovuClientInitialized() {
        val field = NovuService::class.java.getDeclaredField("novuClient")
        field.isAccessible = true
        field.set(service, mock<Novu>())
    }

    private fun setSlackIntegrationIdentifier(value: String) {
        markNovuClientInitialized()
        val field = NovuService::class.java.getDeclaredField("slackIntegrationIdentifier")
        field.isAccessible = true
        field.set(service, value)
    }

    private fun setSlackConnectionIdentifier(value: String) {
        val field = NovuService::class.java.getDeclaredField("slackConnectionIdentifier")
        field.isAccessible = true
        field.set(service, value)
    }

    // ── ensureSlackIntegrationExists ──────────────────────────────────────────

    @Test
    fun `creates integration when none exists`() {
        markNovuClientInitialized()
        whenever(apiClient.listIntegrations()).thenReturn(emptyList())
        whenever(apiClient.createIntegration(any())).thenReturn(
            NovuIntegration(_id = "int1", identifier = "slack-new", providerId = "slack", channel = "chat"),
        )

        service.ensureSlackIntegrationExists()

        verify(apiClient).createIntegration(any())
        verify(apiClient, never()).updateIntegration(any(), any(), any())
    }

    @Test
    fun `updates existing integration in place`() {
        markNovuClientInitialized()
        val existing = NovuIntegration(
            _id = "int-existing",
            identifier = "slack-abc",
            providerId = "slack",
            channel = "chat"
        )
        whenever(apiClient.listIntegrations()).thenReturn(listOf(existing))
        whenever(apiClient.updateIntegration(any(), any(), any())).thenReturn(existing)

        service.ensureSlackIntegrationExists()

        verify(apiClient).updateIntegration(
            check { id -> assertEquals("int-existing", id) },
            any<NovuSlackCredentials>(),
            any(),
        )
        verify(apiClient, never()).createIntegration(any())
        verify(apiClient, never()).deleteIntegration(any())
    }

    @Test
    fun `deletes duplicate integrations beyond the first`() {
        markNovuClientInitialized()
        val first = NovuIntegration(
            _id = "int-first",
            identifier = "slack-first",
            providerId = "slack",
            channel = "chat"
        )
        val second = NovuIntegration(
            _id = "int-second",
            identifier = "slack-second",
            providerId = "slack",
            channel = "chat"
        )
        whenever(apiClient.listIntegrations()).thenReturn(listOf(first, second))
        whenever(apiClient.updateIntegration(any(), any(), any())).thenReturn(first)

        service.ensureSlackIntegrationExists()

        verify(apiClient).updateIntegration(
            check { id -> assertEquals("int-first", id) },
            any<NovuSlackCredentials>(),
            any(),
        )
        verify(apiClient).deleteIntegration(
            check { id -> assertEquals("int-second", id) },
        )
    }

    // ── createSlackEndpoint ───────────────────────────────────────────────────

    @Test
    fun `skips creation when valid endpoint already exists`() {
        setSlackIntegrationIdentifier("slack-abc")
        setSlackConnectionIdentifier("chconn-1")
        whenever(apiClient.listChannelEndpoints("U123")).thenReturn(
            listOf(
                NovuChannelEndpoint(
                    identifier = "ep-1",
                    integrationIdentifier = "slack-abc",
                    connectionIdentifier = "chconn-1",
                    type = "slack_user",
                ),
            ),
        )

        service.createSlackEndpoint("U123")

        verify(apiClient, never()).createChannelEndpoint(any())
        verify(apiClient, never()).deleteChannelEndpoint(any())
    }

    @Test
    fun `creates slack_user endpoint for U-prefixed subscriber`() {
        setSlackIntegrationIdentifier("slack-abc")
        setSlackConnectionIdentifier("chconn-1")
        whenever(apiClient.listChannelEndpoints("U123")).thenReturn(emptyList())
        whenever(apiClient.createChannelEndpoint(any())).thenReturn(
            NovuChannelEndpoint(identifier = "ep-new", type = "slack_user"),
        )

        service.createSlackEndpoint("U123")

        verify(apiClient).createChannelEndpoint(
            check { ep ->
                assertEquals("slack_user", ep.type)
                assertEquals("U123", ep.endpoint?.get("userId"))
            },
        )
    }

    @Test
    fun `creates slack_channel endpoint for C-prefixed subscriber`() {
        setSlackIntegrationIdentifier("slack-abc")
        setSlackConnectionIdentifier("chconn-1")
        whenever(apiClient.listChannelEndpoints("C456")).thenReturn(emptyList())
        whenever(apiClient.createChannelEndpoint(any())).thenReturn(
            NovuChannelEndpoint(identifier = "ep-new", type = "slack_channel"),
        )

        service.createSlackEndpoint("C456")

        verify(apiClient).createChannelEndpoint(
            check { ep ->
                assertEquals("slack_channel", ep.type)
                assertEquals("C456", ep.endpoint?.get("channelId"))
            },
        )
    }

    @Test
    fun `deletes stale endpoints before creating new one`() {
        setSlackIntegrationIdentifier("slack-abc")
        setSlackConnectionIdentifier("chconn-1")
        whenever(apiClient.listChannelEndpoints("U123")).thenReturn(
            listOf(
                NovuChannelEndpoint(
                    identifier = "ep-stale",
                    integrationIdentifier = "slack-OLD",
                    connectionIdentifier = null,
                ),
            ),
        )
        whenever(apiClient.createChannelEndpoint(any())).thenReturn(
            NovuChannelEndpoint(identifier = "ep-new", type = "slack_user"),
        )

        service.createSlackEndpoint("U123")

        verify(apiClient).deleteChannelEndpoint("ep-stale")
        verify(apiClient).createChannelEndpoint(any())
    }

    // ── resolveSlackConnectionIdentifier (via createSlackEndpoint) ────────────

    @Test
    fun `reuses existing connection`() {
        setSlackIntegrationIdentifier("slack-abc")
        whenever(apiClient.listChannelEndpoints("U123")).thenReturn(emptyList())
        whenever(apiClient.listChannelConnections()).thenReturn(
            listOf(NovuChannelConnection(identifier = "chconn-existing", providerId = "slack")),
        )
        whenever(apiClient.createChannelEndpoint(any())).thenReturn(
            NovuChannelEndpoint(identifier = "ep-new", type = "slack_user"),
        )

        service.createSlackEndpoint("U123")

        verify(apiClient, never()).createChannelConnection(any())
    }

    @Test
    fun `creates connection when none exists`() {
        setSlackIntegrationIdentifier("slack-abc")
        whenever(apiClient.listChannelEndpoints("U123")).thenReturn(emptyList())
        whenever(apiClient.listChannelConnections()).thenReturn(emptyList())
        whenever(apiClient.createChannelConnection(any())).thenReturn(
            NovuChannelConnection(identifier = "chconn-new", providerId = "slack"),
        )
        whenever(apiClient.createChannelEndpoint(any())).thenReturn(
            NovuChannelEndpoint(identifier = "ep-new", type = "slack_user"),
        )

        service.createSlackEndpoint("U123")

        verify(apiClient).createChannelConnection(
            check { conn ->
                assertEquals("U123", conn.subscriberId)
                assertEquals("xoxb-token", conn.auth?.accessToken)
                assertEquals("T123", conn.workspace?.id)
            },
        )
    }
}
