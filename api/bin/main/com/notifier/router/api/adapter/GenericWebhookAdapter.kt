package com.notifier.router.api.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.notifier.router.api.domain.Event

object GenericWebhookAdapter {
    private val mapper = jacksonObjectMapper()

    fun parse(
        payload: String,
        eventType: String,
    ): Event {
        val jsonNode = mapper.readTree(payload)

        return Event(
            typeKey = eventType,
            metadata =
                mapOf(
                    "event_type" to eventType,
                ),
            payload =
                mapOf(
                    "event_data" to jsonNode.toString(),
                ),
        )
    }

    fun parseCustomEvent(
        payload: String,
        typeKey: String,
        metadata: Map<String, String>,
    ): Event =
        Event(
            typeKey = typeKey,
            metadata = metadata,
            payload =
                mapOf(
                    "event_data" to payload,
                ),
        )
}
