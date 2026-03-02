package com.notifier.router.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.test.context.ActiveProfiles

@Service
@ActiveProfiles("test")
class DatabaseCleanupService
    @Autowired
    constructor(
        private val jdbcTemplate: JdbcTemplate,
    ) {
        fun truncate() {
            val tables =
                jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name NOT LIKE 'flyway_%'",
                    String::class.java,
                )
            if (tables.isNotEmpty()) {
                jdbcTemplate.execute("TRUNCATE TABLE ${tables.joinToString(", ")} CASCADE")
            }
        }
    }
