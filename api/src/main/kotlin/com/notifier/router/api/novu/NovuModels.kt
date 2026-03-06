package com.notifier.router.api.novu

// ── Integrations ──────────────────────────────────────────────────────────────

@Suppress("ConstructorParameterNaming")
data class NovuIntegration(
    val _id: String? = null,
    val identifier: String? = null,
    val providerId: String = "",
    val channel: String = "",
    val name: String? = null,
    val active: Boolean = true,
    val credentials: NovuSlackCredentials? = null,
)

data class NovuSlackCredentials(
    val clientId: String,
    val secretKey: String,
    val applicationId: String,
    val token: String,
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
    val identifier: String,
)

data class NovuWorkflowStep(
    val template: NovuStepTemplate,
)

data class NovuStepTemplate(
    val type: String,
    val content: String,
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
    val id: String,
    val name: String,
)

data class NovuConnectionAuth(
    val accessToken: String,
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

data class NovuSubscriber(
    val subscriberId: String,
    val firstName: String? = null,
    val lastName: String? = null,
)
