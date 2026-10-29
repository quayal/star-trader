package dev.adriangrzebyk.trader.login

class LoginCodeRequestService(
	private val codeGenerator: OneTimeCodeGenerator,
	private val codeSender: OneTimeCodeSender,
	private val codeStore: OneTimeCodeStore,
	private val clock: Clock,
) {

	fun requestCode(request: LoginCodeRequest) {
		val playerName = request.playerName.trim()
		if (playerName.isEmpty()) {
			throw InvalidLoginRequestException("Player name is required")
		}
		if (request.corporationName.isBlank()) {
			throw InvalidLoginRequestException("Corporation name is required")
		}
		val email = request.email.trim().lowercase()
		if (!isValidEmail(email)) {
			throw InvalidLoginRequestException("Email address is invalid")
		}

		val code = codeGenerator.generate()
		// Send first: a failed send must not invalidate the player's previous code.
		codeSender.send(email, playerName, code)
		codeStore.store(email, code, clock.now())
	}

	private fun isValidEmail(email: String): Boolean {
		if (email.any { it.isWhitespace() || it.isISOControl() }) {
			return false
		}
		val atIndex = email.indexOf('@')
		if (atIndex <= 0 || atIndex == email.length - 1) {
			return false
		}
		return email.indexOf('@', atIndex + 1) == -1
	}
}
