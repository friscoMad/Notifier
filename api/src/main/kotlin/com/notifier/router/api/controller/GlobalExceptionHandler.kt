package com.notifier.router.api.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred: ${ex.message}", ex)

        val errorTrace = ex.stackTraceToString()
        // We log the trace, but arguably we might not want to return the full trace in the body for
        // security
        // However, the user explicitly asked to log it.

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    timestamp = LocalDateTime.now(),
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    error = "Internal Server Error",
                    message = ex.message ?: "An unexpected error occurred",
                    trace = errorTrace,
                ),
            )
    }

    data class ErrorResponse(
        val timestamp: LocalDateTime,
        val status: Int,
        val error: String,
        val message: String,
        val trace: String? = null,
    )
}
