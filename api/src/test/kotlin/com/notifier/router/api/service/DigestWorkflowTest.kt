package com.notifier.router.api.service

import co.novu.common.base.Novu
import com.fasterxml.jackson.databind.ObjectMapper
import com.notifier.router.api.config.NovuApiProperties
import com.notifier.router.api.config.NovuResendProperties
import com.notifier.router.api.config.NovuSesProperties
import com.notifier.router.api.config.NovuSlackProperties
import com.notifier.router.api.novu.NovuApiClient
import com.notifier.router.api.novu.NovuDigestMetadata
import com.notifier.router.api.novu.NovuWorkflow
import com.notifier.router.api.novu.NovuWorkflowTrigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DigestWorkflowTest {
    private lateinit var apiClient: NovuApiClient
    private lateinit var service: NovuService

    @BeforeEach
    fun setup() {
        apiClient = mock()
        service = NovuService(
            novuApiProps = NovuApiProperties(key = "test-key", url = "http://localhost:3000/v1"),
            slackProps = NovuSlackProperties(
                clientId = "client-id",
                clientSecret = "client-secret",
                applicationId = "app-id",
                botToken = "xoxb-token",
                workspaceId = "T123",
                workspaceName = "Test Workspace",
            ),
            sesProps = NovuSesProperties(
                accessKeyId = "AKID",
                secretAccessKey = "secret",
                region = "us-east-1",
                from = "notifier@example.com",
                senderName = "Notifier",
            ),
            resendProps = NovuResendProperties(),
            objectMapper = ObjectMapper(),
        )
        NovuService::class.java.getDeclaredField("novuApiClient").also {
            it.isAccessible = true
            it.set(service, apiClient)
        }
        markNovuClientInitialized()
    }

    private fun markNovuClientInitialized() {
        NovuService::class.java.getDeclaredField("novuClient").also {
            it.isAccessible = true
            it.set(service, mock<Novu>())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readWorkflowRegistry(): Map<String, String> {
        val field = NovuService::class.java.getDeclaredField("workflowRegistry")
        field.isAccessible = true
        return field.get(service) as Map<String, String>
    }

    @Test
    fun `ensureDigestChannelWorkflowExists creates workflow with digest and chat steps when not exists`() {
        whenever(apiClient.listNotificationGroups()).thenReturn(listOf(mapOf("_id" to "g1")))
        whenever(apiClient.createWorkflow(any())).thenReturn(
            NovuWorkflow(
                _id = "wf1",
                name = "pr_created_chat_digest",
                triggers = listOf(NovuWorkflowTrigger(identifier = "pr-created-chat-digest-abc")),
            ),
        )

        service.ensureDigestChannelWorkflowExists(
            typeKey = "pr_created",
            name = "PR Created",
            channel = "chat",
            intervalKey = "1d",
            existingNames = emptyMap(),
        )

        verify(apiClient).createWorkflow(
            check { workflow ->
                assertEquals("pr_created_chat_digest_1d", workflow.name)
                val steps = workflow.steps!!
                assertEquals(2, steps.size)
                // First step: digest
                assertEquals("digest", steps[0].template.type)
                assertEquals(
                    NovuDigestMetadata(type = "regular", amount = 1, unit = "minutes"),
                    steps[0].metadata,
                )
                // Second step: chat delivery — digest header + each event's content
                assertEquals("chat", steps[1].template.type)
                assert(steps[1].template.content.contains("step.total_count"))
                assert(steps[1].template.content.contains("step.events"))
                assert(steps[1].template.content.contains("{{{content}}}"))

            },
        )
        assertEquals("pr-created-chat-digest-abc", readWorkflowRegistry()["pr_created_chat_digest_1d"])
    }

    @Test
    fun `ensureDigestChannelWorkflowExists creates workflow with digest and email steps when not exists`() {
        whenever(apiClient.listNotificationGroups()).thenReturn(listOf(mapOf("_id" to "g1")))
        whenever(apiClient.createWorkflow(any())).thenReturn(
            NovuWorkflow(
                _id = "wf2",
                name = "pr_created_email_digest",
                triggers = listOf(NovuWorkflowTrigger(identifier = "pr-created-email-digest-abc")),
            ),
        )

        service.ensureDigestChannelWorkflowExists(
            typeKey = "pr_created",
            name = "PR Created",
            channel = "email",
            intervalKey = "1d",
            existingNames = emptyMap(),
        )

        verify(apiClient).createWorkflow(
            check { workflow ->
                val steps = workflow.steps!!
                assertEquals(2, steps.size)
                assertEquals("digest", steps[0].template.type)
                assertEquals("email", steps[1].template.type)
                assertEquals("customHtml", steps[1].template.contentType)
            },
        )
        assertEquals("pr-created-email-digest-abc", readWorkflowRegistry()["pr_created_email_digest_1d"])
    }

    @Test
    fun `ensureDigestChannelWorkflowExists updates existing workflow without creating`() {
        val existing = NovuWorkflow(
            _id = "wf-existing",
            name = "pr_created_chat_digest_1d",
            triggers = listOf(NovuWorkflowTrigger(identifier = "pr-created-chat-digest-xyz")),
        )
        whenever(apiClient.updateWorkflow(any(), any())).thenReturn(existing)

        service.ensureDigestChannelWorkflowExists(
            typeKey = "pr_created",
            name = "PR Created",
            channel = "chat",
            intervalKey = "1d",
            existingNames = mapOf("pr_created_chat_digest_1d" to existing),
        )

        verify(apiClient, never()).createWorkflow(any())
        verify(apiClient).updateWorkflow(
            eq("wf-existing"),
            check { workflow ->
                val steps = workflow.steps!!
                assertEquals(2, steps.size)
                assertEquals("digest", steps[0].template.type)
                assertEquals("chat", steps[1].template.type)
                assert(steps[1].template.content.contains("step.events"))
            },
        )
        assertEquals("pr-created-chat-digest-xyz", readWorkflowRegistry()["pr_created_chat_digest_1d"])
    }
}
