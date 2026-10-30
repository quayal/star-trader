package dev.adriangrzebyk.trader.login.adapter.web

import dev.adriangrzebyk.trader.login.InvalidLoginRequestException
import dev.adriangrzebyk.trader.login.LoginCodeRequest
import dev.adriangrzebyk.trader.login.LoginCodeRequestService
import dev.adriangrzebyk.trader.login.OneTimeCodeDeliveryException
import dev.adriangrzebyk.trader.login.OneTimeCodeGenerator
import dev.adriangrzebyk.trader.login.OneTimeCodeSender
import dev.adriangrzebyk.trader.login.OneTimeCodeStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
		try {
			loginCodeRequestService.requestCode(
				LoginCodeRequest(request.playerName.orEmpty(), request.corporationName.orEmpty(), request.email.orEmpty()),
			)
		} catch (ex: InvalidLoginRequestException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		} catch (ex: OneTimeCodeDeliveryException) {
			throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "We couldn't send the code. Try again.")
		}

		return ResponseEntity.status(HttpStatus.ACCEPTED).build()
	}
}
