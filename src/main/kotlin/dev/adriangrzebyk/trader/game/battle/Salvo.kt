package dev.adriangrzebyk.trader.game.battle

import dev.adriangrzebyk.trader.game.Dice
import dev.adriangrzebyk.trader.game.Ship

/**
 * One ship firing at another in a battle round (R75): [firepower] + [roll] (d6) against the target's [shields] gives
 * [hits] from the battle table ([AttackTotal]). With [rockets], a hit target is destroyed when [rocketRoll] (d6) is at or
 * below the hits. A salvo at a ship that broke away that round [missed] and rolls nothing.
 */
data class Salvo(
	val firer: String,
	val target: String,
	val rockets: Boolean,
	val firepower: Int,
	val roll: Int,
	val shields: Int,
	val hits: Int,
	val rocketRoll: Int?,
	val missed: Boolean
) {

	/** Whether the rocket roll destroyed the target outright (R75). */
	fun destroysTarget(): Boolean = rocketRoll != null && rocketRoll <= hits

	/** What this salvo rolled, for the players it concerns; null when it [missed] and rolled nothing. */
	fun rolls(): SalvoRolls? = if (missed) null else SalvoRolls(firepower, roll, shields, rocketRoll)

	/** The public log line (R78): "Salvo: Lantern → Wayfarer, 6 + d6 4 = 10 vs shields 2: 1 hit". */
	fun log(): String {
		val kind = if (rockets) "Rocket salvo" else "Salvo"
		if (missed) {
			return "$kind: $firer → $target misses: $target broke away"
		}
		val result = "$kind: $firer → $target, $firepower + d6 $roll = ${firepower + roll} vs shields $shields: ${hitsText()}"
		return rocketRoll?.let { "$result; d6 $it: ${if (destroysTarget()) "destroyed" else "holds"}" } ?: result
	}

	private fun hitsText(): String = if (hits == 1) "1 hit" else "$hits hits"

	companion object {
		/**
		 * [firer] fires at [target] (R75), with [rockets] or not, rolling a d6 and, for a rocket salvo that hits, another d6 from
		 * [dice]. When the target [brokeAway] that round the salvo misses without rolling (R74). [bonus] is what an agent aboard
		 * adds to its weapons (X, R108).
		 */
		fun fire(firer: Ship, target: Ship, rockets: Boolean, brokeAway: Boolean, bonus: Int, dice: Dice): Salvo {
			val firepower = firer.firepower(rockets) + bonus
			if (brokeAway) {
				return Salvo(firer.name, target.name, rockets, firepower, 0, target.shields(), 0, null, true)
			}
			val roll = dice.d6()
			val hits = AttackTotal(firepower + roll).hitsAgainst(target.shields())
			val rocketRoll = if (rockets && hits > 0) dice.d6() else null
			return Salvo(firer.name, target.name, rockets, firepower, roll, target.shields(), hits, rocketRoll, false)
		}
	}
}
