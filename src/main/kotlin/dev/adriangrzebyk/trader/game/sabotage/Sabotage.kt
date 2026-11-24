package dev.adriangrzebyk.trader.game.sabotage

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.game.CorporationNames
import dev.adriangrzebyk.trader.game.Dice
import dev.adriangrzebyk.trader.game.NoticeKind

/**
 * One sabotage ordered in the Opportunity phase (R97–R101, D23): [saboteurEmail] paid [cost] to hit [target], naming
 * [blamedEmail] to take the blame on a 21+ reputation roll. [effectRoll] and [reputationRoll] are the two 2d6 rolls,
 * with [effectModifier] (criminal connections, Shorty, minus a target factory's units) and [reputationModifier]
 * (criminal connections, Shorty); both null when [foiledBy] an agent (Silent) stopped it before any roll. [lost] is what
 * the target lost: the units the saboteur chose from a warehouse, or the cargo a damaged ship couldn't keep. [settled]
 * says the choice it waits for has been made: the saboteur's from a warehouse, or the ship owner's hits (R99).
 */
data class Sabotage(
	val saboteurEmail: String,
	val target: SabotageTarget,
	val blamedEmail: String,
	val cost: Int,
	val effectRoll: Int?,
	val effectModifier: Int,
	val reputationRoll: Int?,
	val reputationModifier: Int,
	val foiledBy: String?,
	val lost: Map<Goods, Int>,
	val settled: Boolean
) {

	/** The effect (R99): hits, units missing or turns disabled; 0 when foiled. */
	fun effect(): Int = effectRoll?.let { TABLE.effect(it + effectModifier) } ?: 0

	/** The effect roll's total, or null when foiled. */
	fun effectTotal(): Int? = effectRoll?.let { it + effectModifier }

	/** The reputation roll's total, or null when foiled. */
	fun reputationTotal(): Int? = reputationRoll?.let { it + reputationModifier }

	/** What the saboteur loses (R100): 0 when foiled or on 21+, when the innocent player is blamed instead. */
	fun saboteurLoss(): Int = reputationTotal()?.let { TABLE.reputationLoss(it) } ?: 0

	/** Whether the innocent player named takes the blame (R100: a reputation roll of 21+). */
	fun blamesInnocent(): Boolean = reputationTotal()?.let { TABLE.reputationLoss(it) == null } ?: false

	/** The card its reputation loss gets its player (R136): the innocent blamed, or the saboteur traced. */
	fun reputationNoticeKind(): NoticeKind = if (blamesInnocent()) NoticeKind.SABOTAGE_BLAMED else NoticeKind.SABOTAGE_TRACED

	/** Whether [email] owes the warehouse choice of this sabotage (R99): its saboteur, until they've chosen. */
	fun awaitsLossChoiceBy(email: String): Boolean = !settled && saboteurEmail == email && target is SabotageTarget.Warehouse

	/** Whether this waits for its ship's owner to place the hits (R99). */
	fun awaitsHits(): Boolean = !settled && target is SabotageTarget.Ship

	/** Whether this waits for [email] to place the hits on their ship (R99). */
	fun awaitsHitsFrom(email: String): Boolean = awaitsHits() && target.ownerEmail == email

	/** This sabotage once the saboteur chose the warehouse's missing units, [lots], adding up to its effect (R99). */
	fun withLossChosen(lots: Map<Goods, Int>, effect: Int): Sabotage {
		if (lots.values.any { it < 0 } || lots.values.sum() != effect) {
			throw InvalidSabotageException("Choose $effect units in all.")
		}
		return copy(lost = lots.filterValues { it > 0 }, settled = true)
	}

	/** This sabotage once its effect needed no choice or the choice was made, [lost] being what the target lost. */
	fun settledWith(lost: Map<Goods, Int>): Sabotage = copy(lost = lost.filterValues { it > 0 }, settled = true)

	/** The public log line of its effect (R101), naming the target and not the saboteur; null when nothing happened. */
	fun effectLog(names: CorporationNames): String? = effect().takeIf { it > 0 }?.let { target.effectLog(names, it) }

	/** For the saboteur's own toast and log reason: "rolled 2d6+3: 14". */
	fun reputationRollText(): String {
		val modifier = reputationModifier
		val sign = if (modifier < 0) "−${-modifier}" else "+$modifier"
		return "rolled 2d6$sign: ${reputationTotal()}"
	}

	companion object {
		private val TABLE = SabotageTable()

		/** 20 HT × the target system's legal system (R97). */
		const val COST_PER_LEGAL_LEVEL = 20

		/**
		 * The two rolls of a sabotage (R98), made at once with [dice]: the effect roll adds [effectModifier], the reputation
		 * roll [reputationModifier].
		 */
		fun rolled(
			saboteurEmail: String,
			target: SabotageTarget,
			blamedEmail: String,
			cost: Int,
			effectModifier: Int,
			reputationModifier: Int,
			dice: Dice
		): Sabotage {
			val effect = dice.twoD6()
			val reputation = dice.twoD6()
			return Sabotage(
				saboteurEmail,
				target,
				blamedEmail,
				cost,
				effect,
				effectModifier,
				reputation,
				reputationModifier,
				null,
				emptyMap(),
				false
			)
		}

		/** A sabotage that [agent] stopped before any roll (Silent, R108): paid for, with no effect and no reputation loss. */
		fun foiled(saboteurEmail: String, target: SabotageTarget, blamedEmail: String, cost: Int, agent: String): Sabotage =
			Sabotage(saboteurEmail, target, blamedEmail, cost, null, 0, null, 0, agent, emptyMap(), true)
	}
}
