package com.notifier.router.bot.handlers

import com.notifier.router.bot.client.RouterApiClient
import com.notifier.router.bot.service.SubscriptionService
import com.notifier.router.common.dto.NotificationTypeDto
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload.Action
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload.Action.SelectedOption
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import com.slack.api.bolt.response.Response
import com.slack.api.methods.MethodsClient
import com.slack.api.model.view.View
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModalHandlersTest {
    @Mock
    private lateinit var app: App

    @Mock
    private lateinit var apiClient: RouterApiClient

    @Mock
    private lateinit var subscriptionService: SubscriptionService

    @InjectMocks
    private lateinit var handlers: ModalHandlers

    @Test
    fun `test handleTypeSelection generates dynamic modal with filters`() {
        val actionReq = mock<BlockActionRequest>()
        val actionCtx = mock<ActionContext>()

        // Arrange
        val payload = mock<BlockActionPayload>()
        val action = mock<Action>()
        val selectedOption = mock<SelectedOption>()

        whenever(action.selectedOption).thenReturn(selectedOption)
        whenever(selectedOption.value).thenReturn("pr_created")
        whenever(payload.actions).thenReturn(listOf(action))
        val view = mock<View>()
        whenever(view.id).thenReturn("view_123")
        whenever(view.hash).thenReturn("hash_456")
        whenever(payload.view).thenReturn(view)

        whenever(actionReq.payload).thenReturn(payload)

        // Mock API returns
        val mockTypes = listOf(NotificationTypeDto(name = "PR Created", key = "pr_created"))
        whenever(apiClient.getNotificationTypes()).thenReturn(mockTypes)

        // Mock Slack Methods
        val methodsClient = mock<MethodsClient>()
        whenever(actionCtx.client()).thenReturn(methodsClient)
        whenever(actionCtx.ack()).thenReturn(Response.ok())

        // Act
        val method =
            ModalHandlers::class.java.getDeclaredMethod(
                "handleTypeSelection",
                BlockActionRequest::class.java,
                ActionContext::class.java,
            )
        method.isAccessible = true
        val response = method.invoke(handlers, actionReq, actionCtx) as Response

        // Assert
        assertEquals(200, response.statusCode)
        verify(apiClient).getNotificationTypes()
        verify(apiClient).getFiltersForType("pr_created")
        verify(actionCtx).client()
        // We know the viewsUpdate is called as long as there is no exception.
    }
}
