package com.notifier.router.api.repository

import com.notifier.router.api.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findBySlackId(slackId: String): User?

    fun existsBySlackId(slackId: String): Boolean
}
