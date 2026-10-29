package dev.adriangrzebyk.trader.login.adapter.web

import dev.adriangrzebyk.trader.login.Clock
import dev.adriangrzebyk.trader.login.CodeExchangeService
import dev.adriangrzebyk.trader.login.InvalidCodeExchangeException
import dev.adriangrzebyk.trader.login.InvalidLoginRequestException
import dev.adriangrzebyk.trader.login.LoginCodeRequest
import dev.adriangrzebyk.trader.login.LoginCodeRequestService
import dev.adriangrzebyk.trader.login.OneTimeCodeGenerator
import dev.adriangrzebyk.trader.login.OneTimeCodeSender
import dev.adriangrzebyk.trader.login.OneTimeCodeStore
import dev.adriangrzebyk.trader.login.SessionStore
import dev.adriangrzebyk.trader.login.SessionTokenGenerator
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api")
class LoginController(
	codeGenerator: OneTimeCodeGenerator,
	codeSender: OneTimeCodeSender,
	codeStore: OneTimeCodeStore,
	clock: Clock,
	tokenGenerator: SessionTokenGenerator,
	private val sessionStore: SessionStore,
) {

	private val loginCodeRequestService = LoginCodeRequestService(codeGenerator, codeSender, codeStore, clock)
	private val codeExchangeService = CodeExchangeService(codeStore, sessionStore, tokenGenerator, clock)

	@PostMapping("/login/request-code")
	fun requestCode(@RequestBody request: LoginCodeRequestDto): ResponseEntity<Void> {
		try {
			loginCodeRequestService.requestCode(
				LoginCodeRequest(request.playerName.orEmpty(), request.corporationName.orEmpty(), request.email.orEmpty()),
			)
		} catch (ex: InvalidLoginRequestException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		}

		return ResponseEntity.status(HttpStatus.ACCEPTED).build()
	}

	@PostMapping("/login/exchange-code")
	fun exchangeCode(@RequestBody request: ExchangeCodeRequestDto): SessionTokenResponseDto {
		if (request.email.isNullOrBlank() || request.code.isNullOrBlank()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and code are required")
		}
		val session = try {
			codeExchangeService.exchangeCode(request.email, request.code)
		} catch (ex: InvalidCodeExchangeException) {
			throw ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.message)
		}

		return SessionTokenResponseDto(session.token)
	}

	@GetMapping("/login/session")
	fun session(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?): SessionResponseDto {
		val token = authorization?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
			?.substring(BEARER_PREFIX.length)?.trim()
		val session = token?.takeIf { it.isNotEmpty() }?.let { sessionStore.find(it) }
			?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

		return SessionResponseDto(session.playerName, session.corporationName, session.email)
	}

	companion object {
		private const val BEARER_PREFIX = "Bearer "
	}
}
