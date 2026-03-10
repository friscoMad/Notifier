package com.notifier.router.api.service

import co.novu.common.base.Novu
import com.fasterxml.jackson.databind.ObjectMapper
import com.notifier.router.api.novu.NovuApiClient
import com.notifier.router.api.novu.NovuChannelConnection
import com.notifier.router.api.novu.NovuChannelEndpoint
import com.notifier.router.api.novu.NovuIntegration
import com.notifier.router.api.novu.NovuSesCredentials
import com.notifier.router.api.novu.NovuSlackCredentials
import com.notifier.router.api.novu.NovuSubscriber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        service = buildService()

        val field = NovuService::class.java.getDeclaredField("novuApiClient")
        field.isAccessible = true
        field.set(service, apiClient)
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private fun buildService(
        sesAccessKeyId: String = "AKID",
        sesSecretAccessKey: String = "secret",
        sesRegion: String = "us-east-1",
        sesFrom: String = "notifier@example.com",
        sesSenderName: String = "Notifier",
        sesSessionToken: String = "",
    ): NovuService {
        val svc = NovuService(
            apiKey = "test-key",
            apiUrl = "http://localhost:3000/v1",
            slackClientId = "client-id",
            slackClientSecret = "client-secret",
            slackApplicationId = "app-id",
            slackBotToken = "xoxb-token",
            slackWorkspaceId = "T123",
            slackWorkspaceName = "Test Workspace",
            sesAccessKeyId = sesAccessKeyId,
            sesSecretAccessKey = sesSecretAccessKey,
            sesRegion = sesRegion,
            sesFrom = sesFrom,
            sesSenderName = sesSenderName,
            sesSessionToken = sesSessionToken,
            objectMapper = ObjectMapper(),
        )
        NovuService::class.java.getDeclaredField("novuApiClient").also {
            it.isAccessible = true
            it.set(svc, apiClient)
        }
        return svc
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

    private fun markNovuClientInitializedOn(svc: NovuService) {
        NovuService::class.java.getDeclaredField("novuClient").also {
            it.isAccessible = true
            it.set(svc, mock<Novu>())
        }
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

    // ── createSlackEndpoint — email forwarding ────────────────────────────────

    @Test
    fun `email is forwarded to upsertSubscriber`() {
        setSlackIntegrationIdentifier("slack-abc")
        setSlackConnectionIdentifier("chconn-1")
        whenever(apiClient.listChannelEndpoints("U123")).thenReturn(emptyList())
        whenever(apiClient.createChannelEndpoint(any())).thenReturn(
            NovuChannelEndpoint(identifier = "ep-new", type = "slack_user"),
        )

        service.createSlackEndpoint("U123", "user@example.com")

        verify(apiClient).upsertSubscriber(
            check { subscriber: NovuSubscriber ->
                assertEquals("U123", subscriber.subscriberId)
                assertEquals("user@example.com", subscriber.email)
            },
        )
    }

    @Test
    fun `null email is forwarded to upsertSubscriber`() {
        setSlackIntegrationIdentifier("slack-abc")
        setSlackConnectionIdentifier("chconn-1")
        whenever(apiClient.listChannelEndpoints("U123")).thenReturn(emptyList())
        whenever(apiClient.createChannelEndpoint(any())).thenReturn(
            NovuChannelEndpoint(identifier = "ep-new", type = "slack_user"),
        )

        service.createSlackEndpoint("U123", null)

        verify(apiClient).upsertSubscriber(
            check { subscriber: NovuSubscriber -> assertNull(subscriber.email) },
        )
    }

    // ── ensureSesIntegrationExists ────────────────────────────────────────────

    @Test
    fun `ses - skips when credentials not configured`() {
        val svc = buildService(sesAccessKeyId = "", sesFrom = "")
        markNovuClientInitializedOn(svc)

        svc.ensureSesIntegrationExists()

        verify(apiClient, never()).listIntegrations()
    }

    @Test
    fun `ses - creates integration when none exists`() {
        val svc = buildService()
        markNovuClientInitializedOn(svc)
        whenever(apiClient.listIntegrations()).thenReturn(emptyList())
        whenever(apiClient.createIntegration(any())).thenReturn(
            NovuIntegration(_id = "int1", identifier = "ses-new", providerId = "ses", channel = "email"),
        )

        svc.ensureSesIntegrationExists()

        verify(apiClient).createIntegration(
            check { integration ->
                assertEquals("ses", integration.providerId)
                assertEquals("email", integration.channel)
                val creds = integration.credentials as NovuSesCredentials
                assertEquals("AKID", creds.accessKeyId)
                assertEquals("secret", creds.secretAccessKey)
                assertEquals("us-east-1", creds.region)
                assertEquals("notifier@example.com", creds.from)
            },
        )
        verify(apiClient, never()).updateIntegration(any(), any(), any())
    }

    @Test
    fun `ses - updates existing integration in place`() {
        val svc = buildService()
        markNovuClientInitializedOn(svc)
        val existing = NovuIntegration(_id = "int-ses", identifier = "ses-abc", providerId = "ses", channel = "email")
        whenever(apiClient.listIntegrations()).thenReturn(listOf(existing))
        whenever(apiClient.updateIntegration(any(), any(), any())).thenReturn(existing)

        svc.ensureSesIntegrationExists()

        verify(apiClient).updateIntegration(
            check { id -> assertEquals("int-ses", id) },
            any<NovuSesCredentials>(),
            any(),
        )
        verify(apiClient, never()).createIntegration(any())
        verify(apiClient, never()).deleteIntegration(any())
    }

    @Test
    fun `ses - deletes duplicate integrations beyond the first`() {
        val svc = buildService()
        markNovuClientInitializedOn(svc)
        val first = NovuIntegration(
            _id = "int-first",
            identifier = "ses-first",
            providerId = "ses",
            channel = "email",
        )
        val second = NovuIntegration(
            _id = "int-second",
            identifier = "ses-second",
            providerId = "ses",
            channel = "email"
        )
        whenever(apiClient.listIntegrations()).thenReturn(listOf(first, second))
        whenever(apiClient.updateIntegration(any(), any(), any())).thenReturn(first)

        svc.ensureSesIntegrationExists()

        verify(apiClient).updateIntegration(
            check { assertEquals("int-first", it) },
            any(),
            any(),
        )
        verify(apiClient).deleteIntegration(check { assertEquals("int-second", it) })
    }

    @Test
    fun `ses - session token is null when blank`() {
        val svc = buildService(sesSessionToken = "")
        markNovuClientInitializedOn(svc)
        whenever(apiClient.listIntegrations()).thenReturn(emptyList())
        whenever(apiClient.createIntegration(any())).thenReturn(
            NovuIntegration(_id = "int1", identifier = "ses-new", providerId = "ses", channel = "email"),
        )

        svc.ensureSesIntegrationExists()

        verify(apiClient).createIntegration(
            check { assertNull((it.credentials as NovuSesCredentials).sessionToken) },
        )
    }

    @Test
    fun `ses - session token is included when provided`() {
        val svc = buildService(sesSessionToken = "tok-xyz")
        markNovuClientInitializedOn(svc)
        whenever(apiClient.listIntegrations()).thenReturn(emptyList())
        whenever(apiClient.createIntegration(any())).thenReturn(
            NovuIntegration(_id = "int1", identifier = "ses-new", providerId = "ses", channel = "email"),
        )

        svc.ensureSesIntegrationExists()

        verify(apiClient).createIntegration(
            check { assertEquals("tok-xyz", (it.credentials as NovuSesCredentials).sessionToken) },
        )
    }
}
