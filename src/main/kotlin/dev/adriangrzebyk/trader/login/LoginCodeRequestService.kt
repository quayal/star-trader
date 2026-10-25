package dev.adriangrzebyk.trader.login

class LoginCodeRequestService(
	private val codeGenerator: OneTimeCodeGenerator,
	private val codeSender: OneTimeCodeSender,
	private val codeStore: OneTimeCodeStore,
) {

	fun requestCode(request: LoginCodeRequest) {
		if (!isValidEmail(request.email)) {
			throw InvalidLoginRequestException("Email address is invalid")
		}

		val code = codeGenerator.generate()
		codeStore.store(request.email, code)
		codeSender.send(request.email, request.playerName, code)
	}

	private fun isValidEmail(email: String): Boolean {
		val atIndex = email.indexOf('@')
		if (atIndex <= 0 || atIndex == email.length - 1) {
			return false
		}
		return email.indexOf('@', atIndex + 1) == -1
	}
}
