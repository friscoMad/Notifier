package com.notifier.router.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper

@Component
class RequestLoggingFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val wrappedReq = ContentCachingRequestWrapper(request, MAX_BODY_BYTES)
        val wrappedRes = ContentCachingResponseWrapper(response)
        chain.doFilter(wrappedReq, wrappedRes)

        val reqBody = wrappedReq.contentAsByteArray.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8) ?: ""
        val resBody = wrappedRes.contentAsByteArray.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8) ?: ""

        log.info(
            "{} {} → {} | req={} | res={}",
            request.method,
            request.requestURI,
            response.status,
            reqBody.ifEmpty { "<empty>" },
            resBody.ifEmpty { "<empty>" },
        )

        wrappedRes.copyBodyToResponse()
    }

    companion object {
        private const val MAX_BODY_BYTES = 65536
    }
}
