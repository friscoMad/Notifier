package com.notifier.router.api.controller

import com.notifier.router.api.repository.UserRepository
import com.notifier.router.api.service.JwtClaims
import com.notifier.router.api.service.JwtService
import com.notifier.router.api.service.SlackOAuthService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val slackOAuthService: SlackOAuthService,
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
) {
    @GetMapping("/slack")
    fun redirectToSlack(response: HttpServletResponse) {
        response.sendRedirect(slackOAuthService.buildAuthorizeUrl())
    }

    @GetMapping("/slack/callback")
    fun slackCallback(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val code = request.getParameter("code")
        if (code.isNullOrBlank()) {
            response.sendRedirect("/dashboard.html?error=missing_code")
            return
        }

        val slackUser = slackOAuthService.exchangeCode(code)
        if (slackUser == null) {
            response.sendRedirect("/dashboard.html?error=auth_failed")
            return
        }

        val user = userRepository.findBySlackId(slackUser.id)
        val displayName = slackUser.name ?: user?.name ?: slackUser.id

        val token = jwtService.sign(slackUser.id, displayName)
        val cookie = Cookie(COOKIE_NAME, token).apply {
            path = "/"
            isHttpOnly = true
            secure = true
            maxAge = COOKIE_MAX_AGE
            setAttribute("SameSite", "Lax")
        }
        response.addCookie(cookie)
        response.sendRedirect("/dashboard.html")
    }

    @GetMapping("/me")
    fun me(
        @CookieValue(COOKIE_NAME, required = false) token: String?,
    ): ResponseEntity<Map<String, Any?>> {
        val claims = token?.let { jwtService.verify(it) }
            ?: return ResponseEntity.status(UNAUTHORIZED).build()

        val user = userRepository.findBySlackId(claims.sub)
        return ResponseEntity.ok(
            mapOf(
                "slackId" to claims.sub,
                "name" to (claims.name ?: user?.name ?: claims.sub),
                "email" to user?.email,
            ),
        )
    }

    @PostMapping("/logout")
    fun logout(response: HttpServletResponse): ResponseEntity<Void> {
        val cookie = Cookie(COOKIE_NAME, "").apply {
            path = "/"
            isHttpOnly = true
            maxAge = 0
        }
        response.addCookie(cookie)
        return ResponseEntity.noContent().build()
    }

    companion object {
        private const val COOKIE_NAME = "nr_session"
        private const val COOKIE_MAX_AGE = 86400
        private const val UNAUTHORIZED = 401
    }

    fun extractClaims(token: String?): JwtClaims? = token?.let { jwtService.verify(it) }
}
