package com.notifier.router.api.domain

import java.io.Serializable

data class Filter(
        val field: String,
        val operator: String,
        val value: Any,
) : Serializable
