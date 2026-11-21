package dev.adriangrzebyk.trader.game.smuggling

/**
 * A ship's landing roll on a Planet (R116): a 2d6 [roll] plus its [bonus] (Aerodynamic hull, working Battle comm, Badger)
 * against the Planet's [security] this turn. Public, like the ship's position (R118).
 */
data class LandingRoll(val roll: Int, val bonus: Int, val security: Int) {

	fun total(): Int = roll + bonus

	/** R117: above security the ship lands; equal, it's forced into the Port; below, it's seized. */
	fun outcome(): LandingOutcome = when {
		total() > security -> LandingOutcome.LANDED
		total() == security -> LandingOutcome.FORCED_INTO_PORT
		else -> LandingOutcome.SEIZED
	}

	/** For the log: "9 vs security 7", or "6 + 3 = 9 vs security 10" with a bonus. */
	fun text(): String = (if (bonus > 0) "$roll + $bonus = ${total()}" else "$roll") + " vs security $security"
}

/** What a landing roll did (R117). */
enum class LandingOutcome { LANDED, FORCED_INTO_PORT, SEIZED }

/**
 * A ship's chances on a Planet before it tries (R118): of the 36 2d6 outcomes with its [bonus] against [security], how many
 * land, force it into the Port, or have it seized, as whole percentages that the landing choice shows.
 */
data class LandingOdds(val bonus: Int, val security: Int) {

	private fun count(outcome: LandingOutcome): Int =
		(1..6).sumOf { a -> (1..6).count { b -> LandingRoll(a + b, bonus, security).outcome() == outcome } }

	fun landsPercent(): Int = percent(LandingOutcome.LANDED)

	fun forcedPercent(): Int = percent(LandingOutcome.FORCED_INTO_PORT)

	fun seizedPercent(): Int = percent(LandingOutcome.SEIZED)

	private fun percent(outcome: LandingOutcome): Int = Math.round(count(outcome) * 100.0 / OUTCOMES).toInt()

	private companion object {
		const val OUTCOMES = 36
	}
}
