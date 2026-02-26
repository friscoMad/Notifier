package com.notifier.router.api.service

import com.notifier.router.api.domain.Filter
import com.notifier.router.api.domain.NotificationEvent
import org.springframework.stereotype.Service

@Service
class FilterEvaluator {
    fun evaluate(
        event: NotificationEvent,
        filters: List<Filter>,
    ): Boolean = filters.all { evaluateFilter(event, it) }

    private fun evaluateFilter(
        event: NotificationEvent,
        filter: Filter,
    ): Boolean {
        val eventValue = event.metadata[filter.field]
        return when (filter.operator.uppercase()) {
            "EQ" -> {
                eventValue == filter.value
            }

            "IN" -> {
                (filter.value as? List<*>)?.contains(eventValue) == true
            }

            "CONTAINS" -> {
                (eventValue as? String)?.contains(
                    filter.value as? String ?: "",
                    ignoreCase = true,
                ) == true
            }

            else -> {
                false
            }
        }
    }
}
