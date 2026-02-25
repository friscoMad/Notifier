package com.notifier.router.api.service

import com.notifier.router.api.domain.Event
import com.notifier.router.api.domain.Filter
import com.notifier.router.api.domain.Subscription
import org.springframework.stereotype.Service

@Service
class FilterEvaluator {
    fun evaluate(
        event: Event,
        subscription: Subscription,
    ): Boolean = subscription.filters.all { filter -> evaluateFilter(event, filter) }

    private fun evaluateFilter(
        event: Event,
        filter: Filter,
    ): Boolean {
        val eventValue = getEventValue(event, filter.field)
        return when (filter.operator) {
            "EQ" -> evaluateEquals(eventValue, filter.value)
            "IN" -> evaluateIn(eventValue, filter.value)
            "CONTAINS" -> evaluateContains(eventValue, filter.value)
            else -> false
        }
    }

    private fun getEventValue(
        event: Event,
        field: String,
    ): Any? =
        when (field) {
            "repo" -> event.metadata["repo"]
            "author" -> event.metadata["author"]
            "base_branch" -> event.metadata["base_branch"]
            "reviewer" -> event.metadata["reviewer"]
            "affected_services" -> event.metadata["affected_services"]
            "check_name" -> event.metadata["check_name"]
            "service" -> event.metadata["service"]
            "environment" -> event.metadata["environment"]
            "status" -> event.metadata["status"]
            "test_name" -> event.metadata["test_name"]
            "team" -> event.metadata["team"]
            else -> null
        }

    private fun evaluateEquals(
        eventValue: Any?,
        filterValue: Any,
    ): Boolean = eventValue?.equals(filterValue) ?: false

    private fun evaluateIn(
        eventValue: Any?,
        filterValue: Any,
    ): Boolean {
        if (eventValue is String && filterValue is List<*>) {
            return filterValue.contains(eventValue)
        }
        return false
    }

    private fun evaluateContains(
        eventValue: Any?,
        filterValue: Any,
    ): Boolean {
        if (eventValue is String && filterValue is String) {
            return eventValue.contains(filterValue, ignoreCase = true)
        }
        return false
    }
}
