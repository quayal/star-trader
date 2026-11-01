package dev.adriangrzebyk.trader.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class NoFunctionsOnObjectsRuleTest {

	private val assertThatCode = assertThatRule { NoFunctionsOnObjectsRule() }

	@Test
	fun `reports a function with a parameter on an object`() {
		val code =
			"""
			object GameItemMapper {
				fun toItem(game: Game, version: Long): Item = TODO()
			}
			""".trimIndent()
		assertThatCode(code).hasLintViolationWithoutAutoCorrect(
			2,
			6,
			"GameItemMapper.toItem works on a value, so it belongs outside the object: an extension on that value's " +
				"type, or a top-level function when the value is a built-in type (engineering standards §5)"
		)
	}

	@Test
	fun `reports a function over a built-in type and a private helper on an object`() {
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
			val comparator = object : Comparator<Game> {
				fun weigh(game: Game): Int = game.turns
				override fun compare(a: Game, b: Game) = weigh(a) - weigh(b)
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
			"from converts a TradeOffer, so make it an extension on TradeOffer (e.g. TradeOffer.toTradeOfferView()), " +
				"not a companion function (engineering standards §5)"
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
					fun of(id: String) = Game(id)
					fun build(block: Builder.() -> Unit) = Game("")
					private fun timeout() = Duration.ofSeconds(5)
				}
			}
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	@Test
	fun `allows extensions and functions outside objects`() {
		val code =
			"""
			fun TradeOffer.toView(): TradeOfferView = TradeOfferView(price)

			internal fun getPriceModifier(indicator: Int): Int? = null

			class TradeDesk {
				fun place(offer: TradeOffer) = Unit
			}
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	private fun objectMessage(function: String) =
		"$function works on a value, so it belongs outside the object: an extension on that value's type, or a " +
			"top-level function when the value is a built-in type (engineering standards §5)"
}
