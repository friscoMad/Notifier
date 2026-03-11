package com.notifier.router.api.controller

import com.notifier.router.api.service.JwtService
import com.notifier.router.api.service.NovuService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationFeedController(
    private val jwtService: JwtService,
    private val novuService: NovuService,
) {
    @GetMapping("/me")
    fun myNotifications(
        @CookieValue("nr_session", required = false) token: String?,
    ): ResponseEntity<List<Map<String, Any?>>> {
        val claims = token?.let { jwtService.verify(it) }
            ?: return ResponseEntity.status(UNAUTHORIZED).build()

        return ResponseEntity.ok(novuService.listInAppNotifications(claims.sub))
    }

    @GetMapping("/all")
    fun allNotifications(): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(novuService.listAllInAppNotifications())

    companion object {
        private const val UNAUTHORIZED = 401
    }
}
