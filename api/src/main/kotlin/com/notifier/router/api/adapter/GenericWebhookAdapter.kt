package com.notifier.router.api.adapter

import com.notifier.router.api.domain.Event

object GenericWebhookAdapter {
    fun parse(
        payload: String,
        eventType: String,
    ) = Event(
        typeKey = eventType,
        metadata = mapOf("event_type" to eventType),
        payload = mapOf("event_data" to payload),
    )

    fun parseCustomEvent(
        payload: String,
        typeKey: String,
        metadata: Map<String, String>,
    ) = Event(
        typeKey = typeKey,
        metadata = metadata,
        payload = mapOf("event_data" to payload),
    )
}
