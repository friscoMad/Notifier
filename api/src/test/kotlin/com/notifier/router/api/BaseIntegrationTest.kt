package com.notifier.router.api

import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BaseIntegrationTest {
    @Autowired private lateinit var databaseCleanupService: DatabaseCleanupService

    @AfterEach
    fun truncateTables() {
        databaseCleanupService.truncate()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", TestContainers.postgres::getJdbcUrl)
            registry.add("spring.datasource.username", TestContainers.postgres::getUsername)
            registry.add("spring.datasource.password", TestContainers.postgres::getPassword)
        }
    }
}
