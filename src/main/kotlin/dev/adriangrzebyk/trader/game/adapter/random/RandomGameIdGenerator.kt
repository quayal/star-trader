package dev.adriangrzebyk.trader.game.adapter.random

import dev.adriangrzebyk.trader.game.GameIdGenerator
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RandomGameIdGenerator : GameIdGenerator {

	override fun generate(): String = UUID.randomUUID().toString()
}
