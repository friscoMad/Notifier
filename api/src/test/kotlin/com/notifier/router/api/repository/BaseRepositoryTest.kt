package com.notifier.router.api.repository

import com.notifier.router.api.DatabaseCleanupService
import com.notifier.router.api.TestContainers
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

@DataJpaTest
@Import(DatabaseCleanupService::class)
@TestPropertySource(
    properties = ["spring.jpa.hibernate.ddl-auto=validate"],
)
abstract class BaseRepositoryTest {
    @Autowired protected lateinit var entityManager: TestEntityManager

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
