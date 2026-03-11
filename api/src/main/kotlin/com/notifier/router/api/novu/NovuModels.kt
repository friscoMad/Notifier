package com.notifier.router.api.novu

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

// ── Integrations ──────────────────────────────────────────────────────────────

sealed class NovuCredentials

data class NovuSlackCredentials(
    val clientId: String = "",
    val secretKey: String = "",
    val applicationId: String = "",
    val token: String = "",
) : NovuCredentials()

data class NovuSesCredentials(
    val apiKey: String = "",
    val secretKey: String = "",
    val region: String = "",
    val from: String = "",
    val senderName: String = "",
) : NovuCredentials()

data class NovuResendCredentials(
    val apiKey: String = "",
    val from: String = "",
    val senderName: String = "",
) : NovuCredentials()

@Suppress("ConstructorParameterNaming")
@JsonIgnoreProperties(value = ["credentials"], allowGetters = true)
data class NovuIntegration(
    val _id: String? = null,
    val identifier: String? = null,
    val providerId: String = "",
    val channel: String = "",
    val name: String? = null,
    val active: Boolean = true,
    val credentials: NovuCredentials? = null,
)

// ── Workflows ─────────────────────────────────────────────────────────────────

@Suppress("ConstructorParameterNaming")
data class NovuWorkflow(
    val _id: String? = null,
    val name: String? = null,
    val notificationGroupId: String? = null,
    val triggers: List<NovuWorkflowTrigger>? = null,
    val steps: List<NovuWorkflowStep>? = null,
    val active: Boolean = true,
    val draft: Boolean = false,
    val critical: Boolean = false,
)

data class NovuWorkflowTrigger(
    val identifier: String = "",
)

data class NovuWorkflowStep(
    val template: NovuStepTemplate = NovuStepTemplate(),
)

data class NovuStepTemplate(
    val type: String = "",
    val content: String = "",
    val contentType: String? = null,
    val subject: String? = null,
)

// ── Channel Connections ───────────────────────────────────────────────────────

data class NovuChannelConnection(
    val identifier: String? = null,
    val providerId: String? = null,
    val subscriberId: String? = null,
    val integrationIdentifier: String? = null,
    val workspace: NovuWorkspace? = null,
    val auth: NovuConnectionAuth? = null,
)

data class NovuWorkspace(
    val id: String = "",
    val name: String = "",
)

data class NovuConnectionAuth(
    val accessToken: String = "",
)

// ── Channel Endpoints ─────────────────────────────────────────────────────────

data class NovuChannelEndpoint(
    val identifier: String? = null,
    val subscriberId: String? = null,
    val integrationIdentifier: String? = null,
    val connectionIdentifier: String? = null,
    val type: String? = null,
    val endpoint: Map<String, String>? = null,
)

// ── Subscribers ───────────────────────────────────────────────────────────────

@JsonInclude(JsonInclude.Include.NON_NULL)
data class NovuSubscriber(
    val subscriberId: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
)
