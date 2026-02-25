package com.notifier.router.api.domain

data class Filter(
    val field: String,
    val operator: String,
    val value: Any,
)
