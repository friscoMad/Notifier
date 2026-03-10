package com.notifier.router.api.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class JwtService(
    @Value("\${app.jwt.secret:local-dev-secret-change-in-production}") private val secret: String,
    @Value("\${app.jwt.expiration:86400}") private val expirationSeconds: Long,
    private val objectMapper: ObjectMapper,
) {
    private val algorithm = "HmacSHA256"

    fun sign(slackId: String, name: String?): String {
        val header = base64("""{"alg":"HS256","typ":"JWT"}""")
        val now = System.currentTimeMillis() / MILLIS_PER_SECOND
        val payload = base64(
            objectMapper.writeValueAsString(
                mapOf("sub" to slackId, "name" to (name ?: slackId), "iat" to now, "exp" to now + expirationSeconds),
            ),
        )
        val signature = hmac("$header.$payload")
        return "$header.$payload.$signature"
    }

    fun verify(token: String): JwtClaims? {
        val parts = token.split(".")
        if (parts.size != JWT_PARTS) return null
        val expectedSig = hmac("${parts[0]}.${parts[1]}")
        if (expectedSig != parts[2]) return null
        val payload = String(Base64.getUrlDecoder().decode(parts[1]))
        val claims = objectMapper.readValue(payload, Map::class.java)
        val exp = (claims["exp"] as Number).toLong()
        if (System.currentTimeMillis() / MILLIS_PER_SECOND > exp) return null
        return JwtClaims(sub = claims["sub"] as String, name = claims["name"] as? String)
    }

    private fun base64(data: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(data.toByteArray())

    private fun hmac(data: String): String {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret.toByteArray(), algorithm))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.toByteArray()))
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
        private const val JWT_PARTS = 3
    }
}

data class JwtClaims(val sub: String, val name: String?)
