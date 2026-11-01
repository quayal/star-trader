package dev.adriangrzebyk.trader.login.adapter.web

data class ExchangeCodeRequestDto(val email: String? = null, val code: String? = null)

data class SessionTokenResponseDto(val sessionToken: String)

data class SessionResponseDto(val playerName: String, val corporationName: String, val email: String)
