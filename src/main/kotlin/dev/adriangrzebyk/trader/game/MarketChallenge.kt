package dev.adriangrzebyk.trader.game

/**
 * A challenge of a market position (R126): [kind] connections (political or business) of the challenger, [challengerLevel], against
 * the target's, [targetLevel]. A d6 below the [difference] lowers the target's position one tier; otherwise the challenger loses as many points of
 * reputation as the roll missed by ([reputationLost]).
 */
data class MarketChallenge(val kind: ConnectionKind, val challengerLevel: Int, val targetLevel: Int) {

	constructor(kind: ConnectionKind, challenger: Connections, target: Connections) : this(kind, challenger.of(kind), target.of(kind))

	/**
	 * Why this challenge can't be made against [targetName], or null: criminal connections don't fight challenges, and a
	 * difference under [MIN_DIFFERENCE] can't win, so it isn't worth the fee (R126).
	 */
	fun refusal(targetName: String): String? = when {
		kind == ConnectionKind.CRIMINAL -> "Challenge with political or business connections."
		!canSucceed() ->
			"Your ${kind.displayName} connections ($challengerLevel) must beat $targetName's ($targetLevel) by at least $MIN_DIFFERENCE " +
				"for a challenge to have a chance."
		else -> null
	}

	/** The challenger's level minus the target's. */
	fun difference(): Int = challengerLevel - targetLevel

	/** Whether any roll can win: a d6 is never below 1, so the difference must be at least [MIN_DIFFERENCE]. */
	fun canSucceed(): Boolean = difference() >= MIN_DIFFERENCE

	/** Whether [roll] (a d6) wins: below the difference. */
	fun succeeds(roll: Int): Boolean = roll < difference()

	/** What a failed [roll] costs the challenger in reputation: how far it missed the successful rolls (R126). */
	fun reputationLost(roll: Int): Int = roll - difference() + 1

	/**
	 * The public log line of the challenge by [challenger] of [target]'s [position] with [roll]: "Evil Corp challenged Star
	 * Dust's Dictator of CHIPS at Mu Herculis (political 8 vs 4), rolled 2: Star Dust is demoted to Wholesaler" (a Retailer "loses the
	 * position"), or "…: the
	 * challenge fails". The fee isn't said (R29).
	 */
	fun logText(challenger: String, target: String, position: MarketPosition, roll: Int): String {
		val lowered = position.tier.previous()?.let { "$target is demoted to ${it.displayName}" } ?: "$target loses the position"
		val result = if (succeeds(roll)) lowered else "the challenge fails"
		return "$challenger challenged $target's ${position.text()} (${kind.displayName} $challengerLevel vs $targetLevel), rolled $roll: $result"
	}

	companion object {
		/** R126: what a challenge costs, paid up front. */
		const val FEE = 20

		/** R126: the reputation a successful challenge takes from the target. */
		const val TARGET_REPUTATION_LOSS = 2

		/** R126: below this difference no d6 can win. */
		const val MIN_DIFFERENCE = 2
	}
}
