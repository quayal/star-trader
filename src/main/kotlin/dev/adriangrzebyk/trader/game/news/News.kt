package dev.adriangrzebyk.trader.game.news

import dev.adriangrzebyk.trader.game.Dice
import dev.adriangrzebyk.trader.game.InvalidGameOperationException

/**
 * A game's tip-offs (R109): those still in the [pool], those [inPlay] (drawn, not yet effective) and the one-time events
 * [retired] after taking place. [nextSlot] numbers the next draw on the board. [conditions] are what this turn's events
 * changed (R115), and [noShipsThroughTurn] the last turn of the galactic war's ban on buying ships (E3), or null.
 */
data class News(
	val pool: List<TipOff>,
	val inPlay: List<ActiveTipOff>,
	val retired: List<TipOff>,
	val nextSlot: Int,
	val conditions: TurnConditions,
	val noShipsThroughTurn: Int?
) {

	/** Whether anything is in play to read in News (R109); with nothing, the News closes at once. */
	fun hasTipOffsInPlay(): Boolean = inPlay.isNotEmpty()

	/** The tip-offs due in turn [turn]'s News, in the order they were drawn (R109). */
	fun dueIn(turn: Int): List<ActiveTipOff> = inPlay.filter { it.isDueIn(turn) }.sortedBy { it.slot }

	/**
	 * This news once turn [turn]'s due tip-offs became effective (R109): out of play, back in the pool at once, or retired
	 * when a one-time-only event; and the turn's conditions calm again, for the events to change.
	 */
	fun afterEffectiveIn(turn: Int): News {
		val due = dueIn(turn).map { it.tipOff }
		return copy(
			inPlay = inPlay.filterNot { it.isDueIn(turn) },
			pool = pool + due.filter { it.returnsToPool() },
			retired = retired + due.filterNot { it.returnsToPool() },
			conditions = TurnConditions.calm(turn)
		)
	}

	/**
	 * Turn [turn]'s draw (R109): a d3 from [dice], then that many tip-offs at random from the pool (all of it when it holds
	 * fewer); nothing is rolled when the pool is empty.
	 */
	fun drawIn(turn: Int, dice: Dice): Draw {
		if (pool.isEmpty()) {
			return Draw(this, null, emptyList())
		}
		val roll = dice.d3()
		val after = (1..minOf(roll, pool.size)).fold(this) { news, _ -> news.withOneDrawn(turn, dice) }
		return Draw(after, roll, after.inPlay.filter { it.slot >= nextSlot })
	}

	/** This news with one tip-off drawn at random from the pool into the next slot in turn [turn]. */
	private fun withOneDrawn(turn: Int, dice: Dice): News {
		val tipOff = pool[dice.oneOf(pool.size) - 1]
		return copy(pool = pool - tipOff, inPlay = inPlay + ActiveTipOff(nextSlot, tipOff, turn, emptyList(), null), nextSlot = nextSlot + 1)
	}

	/** The tip-off in board [slot], refused when none is in play there. */
	fun inSlot(slot: Int?): ActiveTipOff =
		inPlay.firstOrNull { it.slot == slot } ?: throw InvalidGameOperationException("No tip-off is in play there.")

	/**
	 * The opportunity in board [slot] that [email] may take now (R111): learned by them and not yet taken; refused otherwise,
	 * naming the taker of a legal one ([takerName]).
	 */
	fun opportunityFor(slot: Int?, email: String, takerName: (String) -> String): ActiveTipOff {
		val tipOff = inSlot(slot)
		if (tipOff.tipOff.opportunity == null || !tipOff.isLearnedBy(email)) {
			throw InvalidGameOperationException("Learn this tip-off first; only an opportunity can be taken.")
		}
		tipOff.takenBy?.let {
			throw InvalidGameOperationException(
				if (tipOff.showsTakenTo(email)) "${takerName(it)} already took this opportunity." else "This opportunity was already taken."
			)
		}
		return tipOff
	}

	/** This news with [changed] in place of the tip-off in its slot. */
	fun withTipOff(changed: ActiveTipOff): News = copy(inPlay = inPlay.map { if (it.slot == changed.slot) changed else it })

	/** This news with this turn's [changed] conditions (R115). */
	fun withConditions(changed: TurnConditions): News = copy(conditions = changed)

	/** This news with no ship to be bought through turn [turn] (E3: this turn and the next). */
	fun withNoShipsThrough(turn: Int): News = copy(noShipsThroughTurn = turn)

	/** Whether ships can be bought in turn [turn] (E3). */
	fun shipsForSaleIn(turn: Int): Boolean = noShipsThroughTurn?.let { turn > it } ?: true

	/** One turn's draw: the [news] after it, the d3 [roll] (null with an empty pool) and the tip-offs [drawn]. */
	data class Draw(val news: News, val roll: Int?, val drawn: List<ActiveTipOff>)

	companion object {
		/** R109: the first slot on the board. */
		const val FIRST_SLOT = 1

		/** A game with every tip-off in the pool: how a game is created (R109). */
		fun full(): News = News(TipOff.entries, emptyList(), emptyList(), FIRST_SLOT, TurnConditions.calm(0), null)

		/** A game with no tip-offs at all: test games, whose News closes at once, and games saved before News. */
		val NONE = News(emptyList(), emptyList(), emptyList(), FIRST_SLOT, TurnConditions.calm(0), null)
	}
}
