package com.notifier.router.api

import org.testcontainers.postgresql.PostgreSQLContainer

object TestContainers {
    val postgres =
        PostgreSQLContainer("postgres:16-alpine").apply {
            start()
        }
}
