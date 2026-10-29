package dev.adriangrzebyk.trader.login.adapter.random

import dev.adriangrzebyk.trader.login.SessionTokenGenerator
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RandomSessionTokenGenerator : SessionTokenGenerator {

	override fun generate(): String = UUID.randomUUID().toString()
}
