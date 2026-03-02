package com.notifier.router.api.repository

import com.notifier.router.api.domain.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.util.UUID

@DataJpaTest
class UserRepositoryTest(
    @Autowired private val userRepository: UserRepository,
) : BaseRepositoryTest() {
    @Test
    fun `test find by slackId`() {
        val user =
            User(
                id = UUID.randomUUID(),
                slackId = "U12345",
                slackTeamId = "T12345",
                email = "test@example.com",
                name = "Test User",
            )
        entityManager.persist(user)
        entityManager.flush()

        val found = userRepository.findBySlackId("U12345")
        assert(found != null)
        assert(found!!.slackId == "U12345")
    }

    @Test
    fun `test find by slackId not found`() {
        val found = userRepository.findBySlackId("U54321")
        assert(found == null)
    }
}
