package dev.adriangrzebyk.trader.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class ResponseStatusExceptionAdvice {

	@ExceptionHandler(ResponseStatusException::class)
	fun handleResponseStatus(ex: ResponseStatusException): ResponseEntity<String> = ResponseEntity.status(ex.statusCode).body(ex.reason)
}
