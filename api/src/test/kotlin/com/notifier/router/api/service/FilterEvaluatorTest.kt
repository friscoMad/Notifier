package com.notifier.router.api.service

import com.notifier.router.api.domain.Event
import com.notifier.router.api.domain.Filter
import com.notifier.router.api.domain.Subscription
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterEvaluatorTest {
    private val evaluator = FilterEvaluator()

    @Test
    fun `test filter evaluation with EQ operator`() {
        val event =
            Event(
                typeKey = "pr_created",
                metadata = mapOf("repo" to "api", "author" to "john"),
            )

        val subscription =
            Subscription(
                id = java.util.UUID.randomUUID(),
                userId = java.util.UUID.randomUUID(),
                notificationTypeId = java.util.UUID.randomUUID(),
                channels = listOf("slack_dm"),
                filters = listOf(Filter("repo", "EQ", "api")),
            )

        val result = evaluator.evaluate(event, subscription)
        assertTrue(result)
    }

    @Test
    fun `test filter evaluation with IN operator`() {
        val event =
            Event(
                typeKey = "pr_created",
                metadata = mapOf("repo" to "api", "author" to "john"),
            )

        val subscription =
            Subscription(
                id = java.util.UUID.randomUUID(),
                userId = java.util.UUID.randomUUID(),
                notificationTypeId = java.util.UUID.randomUUID(),
                channels = listOf("slack_dm"),
                filters = listOf(Filter("repo", "IN", listOf("api", "web"))),
            )

        val result = evaluator.evaluate(event, subscription)
        assertTrue(result)
    }

    @Test
    fun `test filter evaluation with CONTAINS operator`() {
        val event =
            Event(
                typeKey = "pr_created",
                metadata = mapOf("repo" to "api", "author" to "john"),
            )

        val subscription =
            Subscription(
                id = java.util.UUID.randomUUID(),
                userId = java.util.UUID.randomUUID(),
                notificationTypeId = java.util.UUID.randomUUID(),
                channels = listOf("slack_dm"),
                filters = listOf(Filter("author", "CONTAINS", "jo")),
            )

        val result = evaluator.evaluate(event, subscription)
        assertTrue(result)
    }

    @Test
    fun `test filter evaluation with multiple filters`() {
        val event =
            Event(
                typeKey = "pr_created",
                metadata = mapOf("repo" to "api", "author" to "john"),
            )

        val subscription =
            Subscription(
                id = java.util.UUID.randomUUID(),
                userId = java.util.UUID.randomUUID(),
                notificationTypeId = java.util.UUID.randomUUID(),
                channels = listOf("slack_dm"),
                filters =
                    listOf(
                        Filter("repo", "EQ", "api"),
                        Filter("author", "CONTAINS", "jo"),
                    ),
            )

        val result = evaluator.evaluate(event, subscription)
        assertTrue(result)
    }

    @Test
    fun `test filter evaluation with failed filter`() {
        val event =
            Event(
                typeKey = "pr_created",
                metadata = mapOf("repo" to "api", "author" to "john"),
            )

        val subscription =
            Subscription(
                id = java.util.UUID.randomUUID(),
                userId = java.util.UUID.randomUUID(),
                notificationTypeId = java.util.UUID.randomUUID(),
                channels = listOf("slack_dm"),
                filters =
                    listOf(
                        Filter("repo", "EQ", "web"),
                        Filter("author", "CONTAINS", "jo"),
                    ),
            )

        val result = evaluator.evaluate(event, subscription)
        assertTrue(!result)
    }
}
