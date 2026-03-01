package com.notifier.router.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication @EnableAsync @EnableJpaAuditing
class NotificationRouterApiApplication

fun main(args: Array<String>) {
    runApplication<NotificationRouterApiApplication>(*args)
}
