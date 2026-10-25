package dev.adriangrzebyk.trader.login.adapter.web

import dev.adriangrzebyk.trader.login.InvalidLoginRequestException
import dev.adriangrzebyk.trader.login.LoginCodeRequest
import dev.adriangrzebyk.trader.login.LoginCodeRequestService
import dev.adriangrzebyk.trader.login.OneTimeCodeGenerator
import dev.adriangrzebyk.trader.login.OneTimeCodeSender
import dev.adriangrzebyk.trader.login.OneTimeCodeStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api")
class LoginController(codeGenerator: OneTimeCodeGenerator, codeSender: OneTimeCodeSender, codeStore: OneTimeCodeStore) {

	private val loginCodeRequestService = LoginCodeRequestService(codeGenerator, codeSender, codeStore)

	@PostMapping("/login/request-code")
	fun requestCode(@RequestBody request: LoginCodeRequestDto): ResponseEntity<Void> {
		val playerName = request.playerName?.trim()
		if (playerName.isNullOrEmpty()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Player name is required")
		}
		val corporationName = request.corporationName?.trim()
		if (corporationName.isNullOrEmpty()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Corporation name is required")
		}
		val email = request.email?.trim()
		if (email.isNullOrEmpty()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required")
		}

		try {
			loginCodeRequestService.requestCode(LoginCodeRequest(playerName, corporationName, email))
		} catch (ex: InvalidLoginRequestException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		}

		return ResponseEntity.status(HttpStatus.ACCEPTED).build()
	}

	@ExceptionHandler(ResponseStatusException::class)
	fun handleInvalidRequest(ex: ResponseStatusException): ResponseEntity<String> = ResponseEntity.status(ex.statusCode).body(ex.reason)
}
