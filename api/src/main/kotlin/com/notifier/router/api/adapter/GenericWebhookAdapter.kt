package com.notifier.router.api.adapter

import com.notifier.router.api.domain.GenericEvent
import com.notifier.router.api.domain.NotificationEvent

object GenericWebhookAdapter {
    fun parse(
        payload: String,
        eventType: String,
    ): NotificationEvent =
        GenericEvent(
            typeKey = eventType,
            metadata = mapOf("event_type" to eventType),
            rawPayload = mapOf("event_data" to payload),
        )

    fun parseCustomEvent(
        payload: String,
        typeKey: String,
        metadata: Map<String, String>,
    ): NotificationEvent =
        GenericEvent(
            typeKey = typeKey,
            metadata = metadata,
            rawPayload = mapOf("event_data" to payload),
        )
}
