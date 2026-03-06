package com.notifier.router.common.domain

import java.io.Serializable

data class Filter(
    val field: String,
    val operator: String,
    val value: Any,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
