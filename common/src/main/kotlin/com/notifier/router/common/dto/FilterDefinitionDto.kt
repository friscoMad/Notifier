package com.notifier.router.common.dto

data class FilterDefinitionDto(
    val id: String,
    val notificationTypeId: String,
    val field: String,
    val fieldType: String,
    val operators: List<String>,
)
