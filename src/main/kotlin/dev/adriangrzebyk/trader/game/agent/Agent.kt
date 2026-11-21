package dev.adriangrzebyk.trader.game.agent

/**
 * The creator's ten special operatives (R103–R108, D25, vault note "Operatives and sabotage"), each one copy per game.
 * [effect] is what its owner is told; [recruitable] is false for Badger and Frank, whose features (planet landing, the
 * black market) don't exist yet, so nobody can recruit an agent that does nothing (R108).
 */
enum class Agent(val displayName: String, val effect: String, val recruitable: Boolean) {
	NOVAK("Novak", "+4 to the jump result of the ship he travels on", true),
	BADGER("Badger", "−3 to the security of a planet a ship carrying him tries to land on", true),
	WOLF("Wolf", "+4 on your initiative roll, wherever he is", true),
	SHORTY("Shorty", "+4 on both sabotage rolls if present at the target's system", true),
	JB("JB", "while in any Port, every reputation loss of yours is 1 smaller", true),
	SILENT("Silent", "while in Port, all your assets at that system are immune to sabotage", true),
	TRIGGER("Trigger", "+1 to intercept for the ship carrying him", true),
	FRANK("Frank", "+2 to Black Market rolls on the planet he's on", true),
	X("X", "+5 weapons for the ship carrying him", true),
	LIMPY("Limpy", "while in Port, you may act as Dictator for one good a turn traded there, if nobody else is its Dictator", true);

	/** The public line when this agent acts, owned by [corporation] (R105): "Novak (Vega Lines): +4 to Starfarer's jump". */
	fun actedLog(corporation: String, what: String): String = "$displayName ($corporation)$what"

	companion object {
		/** R104: 2d6 × this at or below what was paid recruits an agent. */
		const val RECRUIT_ROLL_MULTIPLIER = 5

		/** R104: what a recruiter may pay, from 10 to 60 HT; 60 always succeeds. */
		const val MIN_RECRUIT_PAYMENT = 10
		const val MAX_RECRUIT_PAYMENT = 60

		const val NOVAK_JUMP_BONUS = 4
		const val BADGER_SECURITY_REDUCTION = 3
		const val FRANK_BLACK_MARKET_BONUS = 2
		const val WOLF_INITIATIVE_BONUS = 4
		const val SHORTY_SABOTAGE_BONUS = 4
		const val TRIGGER_INTERCEPT_BONUS = 1
		const val X_WEAPONS_BONUS = 5
		const val JB_REPUTATION_REDUCTION = 1
	}
}
