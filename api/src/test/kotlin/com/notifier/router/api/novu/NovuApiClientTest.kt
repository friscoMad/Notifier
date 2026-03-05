package com.notifier.router.api.novu

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.client.RestTemplate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NovuApiClientTest {
    private val wm = WireMockServer(wireMockConfig().dynamicPort())
    private lateinit var client: NovuApiClient
    private lateinit var wmClient: WireMock

    @BeforeAll
    fun startWireMock() {
        wm.start()
        wmClient = WireMock(wm.port())
        val restTemplate =
            RestTemplate().apply {
                messageConverters.add(0, MappingJackson2HttpMessageConverter())
            }
        client = NovuApiClient(restTemplate, "${wm.baseUrl()}/v1", "test-key")
    }

    @AfterAll
    fun stopWireMock() {
        wm.stop()
    }

    @BeforeEach
    fun resetMappings() {
        wm.resetAll()
    }

    // ── listIntegrations ──────────────────────────────────────────────────────

    @Test
    fun `listIntegrations returns parsed list`() {
        wm.stubFor(
            get(urlEqualTo("/v1/integrations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":[{"_id":"int1","identifier":"slack-abc","providerId":"slack","channel":"chat"}]}""",
                        ),
                ),
        )

        val result = client.listIntegrations()

        assertThat(result).hasSize(1)
        assertThat(result[0]._id).isEqualTo("int1")
        assertThat(result[0].identifier).isEqualTo("slack-abc")
        assertThat(result[0].providerId).isEqualTo("slack")
        assertThat(result[0].channel).isEqualTo("chat")
    }

    @Test
    fun `listIntegrations sends ApiKey authorization header`() {
        wm.stubFor(
            get(urlEqualTo("/v1/integrations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"data":[]}"""),
                ),
        )

        client.listIntegrations()

        wmClient.verifyThat(
            getRequestedFor(urlEqualTo("/v1/integrations"))
                .withHeader("Authorization", equalTo("ApiKey test-key")),
        )
    }

    @Test
    fun `listIntegrations returns empty list when data is empty`() {
        wm.stubFor(
            get(urlEqualTo("/v1/integrations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"data":[]}"""),
                ),
        )

        val result = client.listIntegrations()

        assertThat(result).isEmpty()
    }

    // ── createIntegration ─────────────────────────────────────────────────────

    @Test
    fun `createIntegration sends POST with body and returns parsed integration`() {
        val requestBody =
            """
            {
                "providerId":"slack",
                "channel":"chat",
                "active":true
            }
            """.trimIndent()

        wm.stubFor(
            post(urlEqualTo("/v1/integrations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":{"_id":"int2","identifier":"slack-new","providerId":"slack","channel":"chat","active":true}}""",
                        ),
                ),
        )

        val integration = NovuIntegration(providerId = "slack", channel = "chat")
        val result = client.createIntegration(integration)

        assertThat(result.identifier).isEqualTo("slack-new")
        assertThat(result._id).isEqualTo("int2")

        wmClient.verifyThat(
            postRequestedFor(urlEqualTo("/v1/integrations"))
                .withHeader("Authorization", equalTo("ApiKey test-key"))
                .withRequestBody(
                    equalToJson(requestBody, true, true),
                ),
        )
    }

    @Test
    fun `createIntegration request body contains credentials clientId when provided`() {
        wm.stubFor(
            post(urlEqualTo("/v1/integrations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":{"_id":"int3","identifier":"slack-creds","providerId":"slack","channel":"chat","active":true}}""",
                        ),
                ),
        )

        val credentials =
            NovuSlackCredentials(
                clientId = "client-123",
                secretKey = "secret",
                applicationId = "app-id",
                token = "token",
            )
        val integration = NovuIntegration(providerId = "slack", channel = "chat", credentials = credentials)
        val result = client.createIntegration(integration)

        assertThat(result.identifier).isEqualTo("slack-creds")

        wmClient.verifyThat(
            postRequestedFor(urlEqualTo("/v1/integrations"))
                .withRequestBody(
                    equalToJson(
                        """{"providerId":"slack","channel":"chat","active":true,"credentials":{"clientId":"client-123","secretKey":"secret","applicationId":"app-id","token":"token"}}""",
                        true,
                        true,
                    ),
                ),
        )
    }

    // ── updateIntegration ─────────────────────────────────────────────────────

    @Test
    fun `updateIntegration sends PUT to correct URL with credentials and active flag`() {
        wm.stubFor(
            put(urlEqualTo("/v1/integrations/int1"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":{"_id":"int1","identifier":"slack-updated","providerId":"slack","channel":"chat","active":true}}""",
                        ),
                ),
        )

        val credentials =
            NovuSlackCredentials(
                clientId = "client-456",
                secretKey = "secret",
                applicationId = "app-id",
                token = "token",
            )
        val result = client.updateIntegration("int1", credentials, active = true)

        assertThat(result.identifier).isEqualTo("slack-updated")
        assertThat(result._id).isEqualTo("int1")

        wmClient.verifyThat(
            putRequestedFor(urlEqualTo("/v1/integrations/int1"))
                .withHeader("Authorization", equalTo("ApiKey test-key"))
                .withRequestBody(
                    equalToJson(
                        """{"credentials":{"clientId":"client-456","secretKey":"secret","applicationId":"app-id","token":"token"},"active":true}""",
                        true,
                        true,
                    ),
                ),
        )
    }

    // ── deleteIntegration ─────────────────────────────────────────────────────

    @Test
    fun `deleteIntegration sends DELETE to correct URL`() {
        wm.stubFor(
            delete(urlEqualTo("/v1/integrations/int1"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"data":{}}"""),
                ),
        )

        client.deleteIntegration("int1")

        wmClient.verifyThat(
            deleteRequestedFor(urlEqualTo("/v1/integrations/int1"))
                .withHeader("Authorization", equalTo("ApiKey test-key")),
        )
    }

    // ── listChannelConnections ─────────────────────────────────────────────────

    @Test
    fun `listChannelConnections returns typed list with identifier and providerId`() {
        wm.stubFor(
            get(urlEqualTo("/v1/channel-connections"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":[{"identifier":"conn-abc","providerId":"slack","subscriberId":"U123"}]}""",
                        ),
                ),
        )

        val result = client.listChannelConnections()

        assertThat(result).hasSize(1)
        assertThat(result[0].identifier).isEqualTo("conn-abc")
        assertThat(result[0].providerId).isEqualTo("slack")
        assertThat(result[0].subscriberId).isEqualTo("U123")
    }

    // ── createChannelConnection ────────────────────────────────────────────────

    @Test
    fun `createChannelConnection sends POST with subscriberId, workspace id, and auth accessToken`() {
        wm.stubFor(
            post(urlEqualTo("/v1/channel-connections"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":{"identifier":"conn-new","providerId":"slack","subscriberId":"U789"}}""",
                        ),
                ),
        )

        val connection =
            NovuChannelConnection(
                subscriberId = "U789",
                workspace = NovuWorkspace(id = "W001", name = "My Workspace"),
                auth = NovuConnectionAuth(accessToken = "xoxp-token"),
            )
        val result = client.createChannelConnection(connection)

        assertThat(result.identifier).isEqualTo("conn-new")

        wmClient.verifyThat(
            postRequestedFor(urlEqualTo("/v1/channel-connections"))
                .withHeader("Authorization", equalTo("ApiKey test-key"))
                .withRequestBody(
                    equalToJson(
                        """{"subscriberId":"U789","workspace":{"id":"W001","name":"My Workspace"},"auth":{"accessToken":"xoxp-token"}}""",
                        true,
                        true,
                    ),
                ),
        )
    }

    // ── listChannelEndpoints ──────────────────────────────────────────────────

    @Test
    fun `listChannelEndpoints passes subscriberId as query param and returns list`() {
        wm.stubFor(
            get(urlPathEqualTo("/v1/channel-endpoints"))
                .withQueryParam("subscriberId", equalTo("U456"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":[{"identifier":"ep-1","type":"slack_user","connectionIdentifier":"conn-abc"}]}""",
                        ),
                ),
        )

        val result = client.listChannelEndpoints("U456")

        assertThat(result).hasSize(1)
        assertThat(result[0].identifier).isEqualTo("ep-1")
        assertThat(result[0].type).isEqualTo("slack_user")
        assertThat(result[0].connectionIdentifier).isNotNull().isEqualTo("conn-abc")
    }

    @Test
    fun `listChannelEndpoints returns empty list when data is empty`() {
        wm.stubFor(
            get(urlPathEqualTo("/v1/channel-endpoints"))
                .withQueryParam("subscriberId", equalTo("U000"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"data":[]}"""),
                ),
        )

        val result = client.listChannelEndpoints("U000")

        assertThat(result).isEmpty()
    }

    // ── createChannelEndpoint (slack_user) ────────────────────────────────────

    @Test
    fun `createChannelEndpoint for slack_user sends correct body with userId`() {
        wm.stubFor(
            post(urlEqualTo("/v1/channel-endpoints"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":{"identifier":"ep-user-1","type":"slack_user","connectionIdentifier":"conn-abc"}}""",
                        ),
                ),
        )

        val endpoint =
            NovuChannelEndpoint(
                type = "slack_user",
                connectionIdentifier = "conn-abc",
                endpoint = mapOf("userId" to "USLACK123"),
            )
        val result = client.createChannelEndpoint(endpoint)

        assertThat(result.identifier).isEqualTo("ep-user-1")
        assertThat(result.type).isEqualTo("slack_user")

        wmClient.verifyThat(
            postRequestedFor(urlEqualTo("/v1/channel-endpoints"))
                .withHeader("Authorization", equalTo("ApiKey test-key"))
                .withRequestBody(
                    equalToJson(
                        """{"type":"slack_user","connectionIdentifier":"conn-abc","endpoint":{"userId":"USLACK123"}}""",
                        true,
                        true,
                    ),
                ),
        )
    }

    // ── createChannelEndpoint (slack_channel) ─────────────────────────────────

    @Test
    fun `createChannelEndpoint for slack_channel sends correct body with channelId`() {
        wm.stubFor(
            post(urlEqualTo("/v1/channel-endpoints"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":{"identifier":"ep-chan-1","type":"slack_channel","connectionIdentifier":"conn-abc"}}""",
                        ),
                ),
        )

        val endpoint =
            NovuChannelEndpoint(
                type = "slack_channel",
                connectionIdentifier = "conn-abc",
                endpoint = mapOf("channelId" to "CCHANNEL456"),
            )
        val result = client.createChannelEndpoint(endpoint)

        assertThat(result.identifier).isEqualTo("ep-chan-1")
        assertThat(result.type).isEqualTo("slack_channel")

        wmClient.verifyThat(
            postRequestedFor(urlEqualTo("/v1/channel-endpoints"))
                .withRequestBody(
                    equalToJson(
                        """{"type":"slack_channel","connectionIdentifier":"conn-abc","endpoint":{"channelId":"CCHANNEL456"}}""",
                        true,
                        true,
                    ),
                ),
        )
    }

    // ── deleteChannelEndpoint ─────────────────────────────────────────────────

    @Test
    fun `deleteChannelEndpoint sends DELETE to correct URL`() {
        wm.stubFor(
            delete(urlEqualTo("/v1/channel-endpoints/ep-1"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"data":{}}"""),
                ),
        )

        client.deleteChannelEndpoint("ep-1")

        wmClient.verifyThat(
            deleteRequestedFor(urlEqualTo("/v1/channel-endpoints/ep-1"))
                .withHeader("Authorization", equalTo("ApiKey test-key")),
        )
    }

    // ── upsertSubscriber ──────────────────────────────────────────────────────

    @Test
    fun `upsertSubscriber sends POST to subscribers with subscriberId`() {
        wm.stubFor(
            post(urlEqualTo("/v1/subscribers"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"data":{"subscriberId":"U123"}}"""),
                ),
        )

        client.upsertSubscriber(NovuSubscriber(subscriberId = "U123"))

        wmClient.verifyThat(
            postRequestedFor(urlEqualTo("/v1/subscribers"))
                .withHeader("Authorization", equalTo("ApiKey test-key"))
                .withRequestBody(
                    equalToJson("""{"subscriberId":"U123"}""", true, true),
                ),
        )
    }
}
