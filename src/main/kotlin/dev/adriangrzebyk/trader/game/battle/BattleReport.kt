package dev.adriangrzebyk.trader.game.battle

import dev.adriangrzebyk.trader.trading.StarSystem

/**
 * What the salvos of round [round] of the battle at [system] did (R75–R78), so each player can be told what their ships
 * hit and what hit them. Kept on the turn until the next round fires, so it outlives the battle.
 */
data class BattleReport(val system: StarSystem, val round: Int, val results: List<SalvoResult>) {

	/** This report with only the salvos [email]'s ships fired or took, or null when there were none. */
	fun reportFor(email: String): BattleReport? = copy(results = results.filter { it.concerns(email) }).takeIf { it.results.isNotEmpty() }

	/**
	 * This report once [ships]' owner placed their hits (R76), which destroyed those named in [destroyed]: their salvos
	 * waiting for it are settled.
	 */
	fun hitsPlaced(ships: Set<String>, destroyed: Set<String>): BattleReport = copy(results = results.map { it.placed(ships, destroyed) })
}

/**
 * One salvo of a round as the players it concerns are told (R75, R76): [firer] (of [firerOwnerEmail]) at [target] (of
 * [targetOwnerEmail]), with [rockets] or not, for [hits], and what came of it.
 */
data class SalvoResult(
	val firer: String,
	val firerOwnerEmail: String,
	val target: String,
	val targetOwnerEmail: String,
	val rockets: Boolean,
	val hits: Int,
	val outcome: SalvoOutcome
) {

	/** Whether [email] fired this salvo or took it. */
	fun concerns(email: String): Boolean = email == firerOwnerEmail || email == targetOwnerEmail

	/** This result once the hits on [ships] were placed, destroying those in [destroyed] (R76). */
	fun placed(ships: Set<String>, destroyed: Set<String>): SalvoResult = when {
		outcome != SalvoOutcome.AWAITING_PLACEMENT || target !in ships -> this
		target in destroyed -> copy(outcome = SalvoOutcome.DESTROYED_WHEN_PLACED)
		else -> copy(outcome = SalvoOutcome.PLACED)
	}

	companion object {
		/**
		 * What [salvo] came to once the round's salvos landed (R74–R76): the target broke away, took nothing, was
		 * [destroyed] outright, or waits for its owner to place the hits.
		 */
		fun of(salvo: Salvo, firerOwnerEmail: String, targetOwnerEmail: String, destroyed: Set<String>): SalvoResult {
			val outcome = when {
				salvo.missed -> SalvoOutcome.BROKE_AWAY
				salvo.hits == 0 -> SalvoOutcome.NO_DAMAGE
				salvo.target in destroyed -> SalvoOutcome.DESTROYED
				else -> SalvoOutcome.AWAITING_PLACEMENT
			}
			return SalvoResult(salvo.firer, firerOwnerEmail, salvo.target, targetOwnerEmail, salvo.rockets, salvo.hits, outcome)
		}
	}
}

/** What came of a salvo (R74–R76). */
enum class SalvoOutcome { BROKE_AWAY, NO_DAMAGE, DESTROYED, AWAITING_PLACEMENT, PLACED, DESTROYED_WHEN_PLACED }
