package com.notifier.router.api.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class LoggingInterceptor : ClientHttpRequestInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        val reqBody = body.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8) ?: "<empty>"
        log.debug("→ {} {} | body={}", request.method, request.uri, reqBody)

        val response = execution.execute(request, body)
        val resBody =
            response.body.readBytes().also {
                log.debug("← {} {} | body={}", response.statusCode, request.uri, it.toString(Charsets.UTF_8).ifEmpty { "<empty>" })
            }
        return object : ClientHttpResponse by response {
            override fun getBody() = resBody.inputStream()
        }
    }
}
