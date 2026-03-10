package com.notifier.router.api.controller

import com.notifier.router.api.exception.SubscriptionNotFoundException
import com.notifier.router.api.service.SubscriptionService
import com.notifier.router.common.dto.SubscriptionDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
class SubscriptionControllerTest {
    @Mock private lateinit var subscriptionService: SubscriptionService

    @org.mockito.InjectMocks private lateinit var subscriptionController: SubscriptionController

    @Test
    fun `test createSubscription returns created subscription`() {
        val subscriptionDto =
            SubscriptionDto(
                id = null,
                userId = "${java.util.UUID.randomUUID()}",
                notificationTypeId = "${java.util.UUID.randomUUID()}",
                channels = listOf("slack_dm"),
                channelConfig = emptyMap(),
                filters = emptyList(),
                enabled = true,
            )

        val expectedDto = subscriptionDto.copy(id = "${java.util.UUID.randomUUID()}")

        whenever(subscriptionService.createSubscription(any())).thenReturn(expectedDto)

        val result = subscriptionController.createSubscription(subscriptionDto)

        assert(result.statusCode == HttpStatus.OK)
        assert(result.body != null)
        assert(result.body!!.channels == subscriptionDto.channels)
    }

    @Test
    fun `test getSubscriptionsByUserId returns user subscriptions`() {
        val userId = "${java.util.UUID.randomUUID()}"
        val expectedDto =
            SubscriptionDto(
                id = "${java.util.UUID.randomUUID()}",
                userId = userId,
                notificationTypeId = "${java.util.UUID.randomUUID()}",
                channels = listOf("slack_dm"),
                channelConfig = emptyMap(),
                filters = emptyList(),
                enabled = true,
            )

        whenever(subscriptionService.getSubscriptionsByUserId(any()))
            .thenReturn(listOf(expectedDto))

        val result = subscriptionController.getSubscriptionsByUserId(userId)

        assert(result.statusCode == HttpStatus.OK)
        assert(result.body != null)
        assert(result.body!!.size == 1)
    }

    @Test
    fun `test updateSubscription returns updated subscription`() {
        val id = "${java.util.UUID.randomUUID()}"
        val subscriptionDto =
            SubscriptionDto(
                id = id,
                userId = "${java.util.UUID.randomUUID()}",
                notificationTypeId = "${java.util.UUID.randomUUID()}",
                channels = listOf("email"),
                channelConfig = emptyMap(),
                filters = emptyList(),
                enabled = true,
            )

        whenever(
            subscriptionService.updateSubscription(
                any(),
                any(),
            ),
        ).thenReturn(subscriptionDto)

        val result = subscriptionController.updateSubscription(id, subscriptionDto)

        assert(result.statusCode == HttpStatus.OK)
        assert(result.body != null)
        assert(result.body!!.id == id)
    }

    @Test
    fun `test deleteSubscription returns no content`() {
        val id = "${java.util.UUID.randomUUID()}"

        val result = subscriptionController.deleteSubscription(id)

        assert(result.statusCode == HttpStatus.NO_CONTENT)
        assert(result.body == null)
    }

    @Test
    fun `test deleteSubscription propagates SubscriptionNotFoundException`() {
        val id = "${java.util.UUID.randomUUID()}"
        doThrow(SubscriptionNotFoundException("Subscription not found: $id"))
            .whenever(subscriptionService).deleteSubscription(any())

        assertThrows<SubscriptionNotFoundException> {
            subscriptionController.deleteSubscription(id)
        }
    }
}
