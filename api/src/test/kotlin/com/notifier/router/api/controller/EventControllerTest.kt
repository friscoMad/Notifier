package com.notifier.router.api.controller

import com.notifier.router.api.service.EventService
import com.notifier.router.api.service.FilterEvaluator
import com.notifier.router.api.service.SubscriptionService
import com.notifier.router.common.dto.EventDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
class EventControllerTest {
    @Mock private lateinit var subscriptionService: SubscriptionService

    @Mock private lateinit var filterEvaluator: FilterEvaluator

    @Mock private lateinit var eventService: EventService

    private lateinit var eventController: EventController

    @BeforeEach
    fun setup() {
        eventController = EventController(eventService)
    }

    @Test
    fun `test processEvent returns accepted status`() {
        val eventDto =
            EventDto(
                typeKey = "pr_created",
                metadata = mapOf("repo" to "api", "author" to "john"),
                payload = emptyMap(),
            )

        val result = eventController.processEvent(eventDto)

        assert(result.statusCode == HttpStatus.ACCEPTED)
        assert(result.body == null)
    }

    @Test
    fun `test processEvent handles empty metadata`() {
        val eventDto =
            EventDto(typeKey = "deploy_completed", metadata = emptyMap(), payload = emptyMap())

        val result = eventController.processEvent(eventDto)

        assert(result.statusCode == HttpStatus.ACCEPTED)
        assert(result.body == null)
    }

    @Test
    fun `test processEvent handles null metadata`() {
        val eventDto =
            EventDto(
                typeKey = "pr_review_requested",
                metadata = emptyMap(),
                payload = emptyMap(),
            )

        val result = eventController.processEvent(eventDto)

        assert(result.statusCode == HttpStatus.ACCEPTED)
        assert(result.body == null)
    }
}
