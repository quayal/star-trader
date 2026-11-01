package dev.adriangrzebyk.trader.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class FunctionsInClassesRuleTest {

	private val assertThatCode = assertThatRule { FunctionsInClassesRule() }

	@Test
	fun `reports functions with a parameter on an object, private ones too`() {
		val code =
			"""
			internal object SDCalculator {
				fun getPriceModifier(indicator: Int): Int? = null
				private fun indicators(modifier: Int): List<Int> = emptyList()
			}
			""".trimIndent()
		assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
			LintViolation(2, 6, objectMessage("SDCalculator.getPriceModifier")),
			LintViolation(3, 14, objectMessage("SDCalculator.indicators"))
		)
	}

	@Test
	fun `reports a member extension on an object`() {
		val code =
			"""
			object Mappers {
				fun Game.toItem(): Item = TODO()
			}
			""".trimIndent()
		assertThatCode(code).hasLintViolationWithoutAutoCorrect(2, 11, objectMessage("Mappers.toItem"))
	}

	@Test
	fun `allows constants, zero-argument functions and overrides on an object`() {
		val code =
			"""
			object OneTimeCode {
				const val MAX_ATTEMPTS = 5
				fun ttl(): Duration = Duration.ofMinutes(10)
			}

			object SilentSender : CodeSender {
				override fun send(email: String, code: String) = Unit
			}
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	@Test
	fun `allows functions on object expressions`() {
		val code =
			"""
			class Ranking {
				val comparator = object : Comparator<Game> {
					fun weigh(game: Game): Int = game.turns
					override fun compare(a: Game, b: Game) = weigh(a) - weigh(b)
				}
			}
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	@Test
	fun `reports a companion conversion from another type`() {
		val code =
			"""
			data class TradeOfferView(val price: Int) {
				companion object {
					fun from(offer: TradeOffer?): TradeOfferView = TradeOfferView(offer!!.price)
				}
			}
			""".trimIndent()
		assertThatCode(code).hasLintViolationWithoutAutoCorrect(
			3,
			7,
			"from converts a TradeOffer, so make it a constructor: TradeOfferView(offer: TradeOffer) " +
				"(engineering standards §5)"
		)
	}

	@Test
	fun `allows companion factories of several values or of built-in types`() {
		val code =
			"""
			data class Game(val id: String) {
				companion object {
					const val MAX_PLAYERS = 6
					fun lobby(id: String, turns: Int, creator: Player) = Game(id)
					fun lowestFor(priceModifier: Int) = Game("")
					fun build(block: Builder.() -> Unit) = Game("")
					private fun timeout() = Duration.ofSeconds(5)
				}
			}
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	@Test
	fun `reports a top-level function whose first parameter is a project type`() {
		val code =
			"""
			package dev.adriangrzebyk.trader.game

			import dev.adriangrzebyk.trader.trading.TradeOffer

			fun describe(game: Game): String = game.id

			private fun priceOf(offer: TradeOffer?, roll: Int): Int = roll
			""".trimIndent()
		assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
			LintViolation(5, 5, topLevelMessage("describe", "Game")),
			LintViolation(7, 13, topLevelMessage("priceOf", "TradeOffer"))
		)
	}

	@Test
	fun `allows top-level functions no class owns`() {
		val code =
			"""
			package dev.adriangrzebyk.trader.game

			import dev.adriangrzebyk.trader.trading.TradeOffer

			fun main(args: Array<String>) = Unit

			fun parseTurns(text: String): Int? = text.toIntOrNull()

			fun newGameId(): String = "g-1"

			fun startingPositions(players: List<Player>, picker: () -> Int): String = ""

			// Known gap: a project type after the first parameter isn't caught; reviews catch it.
			fun price(roll: Int, offer: TradeOffer): Int = roll
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	@Test
	fun `reports extensions on project types, imported or from the same package`() {
		val code =
			"""
			package dev.adriangrzebyk.trader.game.adapter.web

			import dev.adriangrzebyk.trader.game.Game
			import dev.adriangrzebyk.trader.game.Ship as DomainShip

			fun Game.toResponseDto(viewer: String) = GameResponseDto(id)

			private fun DomainShip?.toResponseDto() = ShipResponseDto()

			fun GameResponseDto.withYou() = this

			val dev.adriangrzebyk.trader.game.Player.label: String get() = name

			class GameController {
				private fun Game.isOver() = false
			}
			""".trimIndent()
		assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
			LintViolation(6, 10, extensionMessage("toResponseDto", "Game")),
			LintViolation(8, 25, extensionMessage("toResponseDto", "DomainShip")),
			LintViolation(10, 21, extensionMessage("withYou", "GameResponseDto")),
			LintViolation(12, 42, extensionMessage("label", "dev.adriangrzebyk.trader.game.Player")),
			LintViolation(15, 19, extensionMessage("isOver", "Game"))
		)
	}

	@Test
	fun `allows extensions on library types and type parameters`() {
		val code =
			"""
			package dev.adriangrzebyk.trader.game.adapter.dynamodb

			import software.amazon.awssdk.services.dynamodb.model.AttributeValue

			internal fun Map<String, AttributeValue>.attribute(name: String): AttributeValue = getValue(name)

			fun AttributeValue.number(): Int = n().toInt()

			fun String.shout() = uppercase()

			fun <T> T.alsoLog(): T = this

			val java.time.Duration.label: String get() = toString()
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	@Test
	fun `allows methods, constructors and properties in classes`() {
		val code =
			"""
			data class TradeOfferView(val price: Int) {
				constructor(offer: TradeOffer) : this(offer.price)

				fun cheaperThan(other: TradeOfferView) = price < other.price

				val free: Boolean get() = price == 0
			}
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	private fun objectMessage(function: String) = "$function works on a value, so it belongs in that value's class (engineering standards §5)"

	private fun topLevelMessage(name: String, type: String) =
		"$name works on a $type, so make it a method of $type, or, when it maps to an adapter type, a constructor or " +
			"method of the adapter-side class (engineering standards §5)"

	private fun extensionMessage(name: String, type: String) =
		"$name extends $type, a class of this project: declare it in $type, or, when it maps to an adapter type, as a " +
			"constructor or method of the adapter-side class (engineering standards §5)"
}
