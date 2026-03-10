package com.notifier.router.api.controller

import com.notifier.router.api.exception.SubscriptionNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(SubscriptionNotFoundException::class)
    fun handleSubscriptionNotFound(ex: SubscriptionNotFoundException): ResponseEntity<Unit> {
        logger.warn("Subscription not found: ${ex.message}")
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<Unit> {
        logger.warn("Data integrity violation: ${ex.message}")
        return ResponseEntity.status(HttpStatus.CONFLICT).build()
    }


    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<Unit> {
        logger.error("Unexpected error occurred: ${ex.message}", ex)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .build()
    }
}
