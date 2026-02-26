package com.notifier.router.api.service

import com.notifier.router.api.domain.Event
import com.notifier.router.api.domain.Filter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterEvaluatorTest {
    private val evaluator = FilterEvaluator()

    private fun event(vararg metadata: Pair<String, Any>) = Event(typeKey = "pr_created", metadata = mapOf(*metadata))

    @Test
    fun `EQ operator matches exact value`() {
        assertTrue(evaluator.evaluate(event("repo" to "api"), listOf(Filter("repo", "EQ", "api"))))
    }

    @Test
    fun `EQ operator is case-insensitive on operator name`() {
        assertTrue(evaluator.evaluate(event("repo" to "api"), listOf(Filter("repo", "eq", "api"))))
    }

    @Test
    fun `IN operator matches value in list`() {
        assertTrue(
            evaluator.evaluate(
                event("repo" to "api"),
                listOf(Filter("repo", "IN", listOf("api", "web"))),
            ),
        )
    }

    @Test
    fun `CONTAINS operator matches substring`() {
        assertTrue(
            evaluator.evaluate(
                event("author" to "john"),
                listOf(Filter("author", "CONTAINS", "jo")),
            ),
        )
    }

    @Test
    fun `multiple filters all must match`() {
        assertTrue(
            evaluator.evaluate(
                event("repo" to "api", "author" to "john"),
                listOf(Filter("repo", "EQ", "api"), Filter("author", "CONTAINS", "jo")),
            ),
        )
    }

    @Test
    fun `returns false when any filter fails`() {
        assertFalse(
            evaluator.evaluate(
                event("repo" to "api", "author" to "john"),
                listOf(Filter("repo", "EQ", "web"), Filter("author", "CONTAINS", "jo")),
            ),
        )
    }

    @Test
    fun `empty filters always match`() {
        assertTrue(evaluator.evaluate(event("repo" to "api"), emptyList()))
    }
}
