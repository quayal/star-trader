package dev.adriangrzebyk.trader.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class CopyOnlyInOwnClassRuleTest {

	private val assertThatCode = assertThatRule { CopyOnlyInOwnClassRule() }

	@Test
	fun `allows a class to copy itself`() {
		val code =
			"""
			data class Ship(val name: String, val system: String) {
				fun jumpTo(destination: String): Ship = copy(system = destination)
				fun renamed(name: String): Ship = this.copy(name = name)
			}
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	@Test
	fun `reports a copy of another object, in a lambda too`() {
		val code =
			"""
			class Game(val players: List<Player>) {
				fun move(player: Player, ship: Ship) = player.copy(ships = player.ships.map { it.copy(system = "Tau Ceti") })
			}
			""".trimIndent()
		assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
			LintViolation(2, 48, message("player")),
			LintViolation(2, 83, message("it"))
		)
	}

	@Test
	fun `reports a safe-call copy`() {
		val code =
			"""
			fun f(ship: Ship?) = ship?.copy(name = "x")
			""".trimIndent()
		assertThatCode(code).hasLintViolationWithoutAutoCorrect(1, 28, message("ship"))
	}

	@Test
	fun `allows a method that happens to be called copy on a non-data receiver only through this`() {
		val code =
			"""
			fun f(files: Files) = files.copyTo("x")
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	@Test
	fun `leaves test sources alone, which build fixtures with copy`() {
		val code =
			"""
			val turn = Turn.bidding(1).copy(phase = TurnPhase.TRADING)
			""".trimIndent()
		assertThatCode(code).asFileWithPath("/repo/src/test/kotlin/TurnTest.kt").hasNoLintViolations()
		assertThatCode(code).asFileWithPath("/repo/src/main/kotlin/Turn.kt").hasLintViolationWithoutAutoCorrect(1, 28, message("Turn.bidding(1)"))
	}

	@Test
	fun `reports a bare copy inside another object's run, apply or with`() {
		val code =
			"""
			class Game {
				fun a(ship: Ship) = ship.run { copy(name = "x") }
				fun b(ship: Ship) = ship.apply { listOf(1).map { copy(name = "y") } }
				fun c(ship: Ship) = with(ship) { copy(name = "z") }
			}
			""".trimIndent()
		assertThatCode(code).hasLintViolationsWithoutAutoCorrect(
			LintViolation(2, 33, message("ship")),
			LintViolation(3, 51, message("ship")),
			LintViolation(4, 35, message("ship"))
		)
	}

	@Test
	fun `allows a bare copy inside a lambda that keeps this class as receiver`() {
		val code =
			"""
			data class Turn(val picks: List<Int>) {
				fun a() = run { copy(picks = emptyList()) }
				fun b() = picks.map { copy(picks = listOf(it)) }
				fun c() = this.run { copy(picks = emptyList()) }
			}
			""".trimIndent()
		assertThatCode(code).hasNoLintViolations()
	}

	@Test
	fun `leaves adapters alone, which may call a library's copy`() {
		val code =
			"""
			fun backup(files: Files) = files.copy("a", "b")
			""".trimIndent()
		assertThatCode(code).asFileWithPath("/repo/src/main/kotlin/game/adapter/dynamodb/Backup.kt").hasNoLintViolations()
	}

	private fun message(receiver: String) =
		"$receiver.copy(…) changes another object's data: give its class a method that makes the change and call that " +
			"(engineering standards §8)"
}
