package com.notifier.router.api.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
class SlackOAuthService(
    @Value("\${novu.slack.client-id:}") private val clientId: String,
    @Value("\${novu.slack.client-secret:}") private val clientSecret: String,
    @Value("\${app.slack.oauth.redirect-uri:http://localhost:8082/auth/slack/callback}") private val redirectUri:
    String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    fun buildAuthorizeUrl(): String =
        "https://slack.com/oauth/v2/authorize?" +
            "client_id=$clientId" +
            "&user_scope=identity.basic,identity.email" +
            "&redirect_uri=${URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)}"

    @Suppress("UNCHECKED_CAST")
    fun exchangeCode(code: String): SlackUser? {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply {
            add("client_id", clientId)
            add("client_secret", clientSecret)
            add("code", code)
            add("redirect_uri", redirectUri)
        }

        val response = restTemplate.postForObject(
            "https://slack.com/api/oauth.v2.access",
            HttpEntity(body, headers),
            Map::class.java,
        ) ?: return null

        if (response["ok"] != true) {
            logger.error("Slack OAuth failed: ${response["error"]}")
            return null
        }

        val authedUser = response["authed_user"] as? Map<String, Any> ?: return null
        val userId = authedUser["id"] as? String ?: return null
        val accessToken = authedUser["access_token"] as? String

        val name = accessToken?.let { fetchIdentity(it) }

        return SlackUser(id = userId, name = name)
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchIdentity(userToken: String): String? {
        val headers = HttpHeaders().apply { setBearerAuth(userToken) }
        val response = restTemplate.exchange(
            "https://slack.com/api/users.identity",
            org.springframework.http.HttpMethod.GET,
            HttpEntity<Unit>(headers),
            Map::class.java,
        ).body ?: return null

        val user = response["user"] as? Map<String, Any> ?: return null
        return user["name"] as? String
    }
}

data class SlackUser(val id: String, val name: String?)
