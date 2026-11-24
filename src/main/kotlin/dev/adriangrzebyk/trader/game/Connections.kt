package dev.adriangrzebyk.trader.game

/**
 * A player's connections (R81, E12): [political], [business] and [criminal], each from 0 to [MAX], public to every
 * player. Political plus business caps the units of factory a player may own (R84); criminal has no effect yet.
 */
data class Connections(val political: Int, val business: Int, val criminal: Int) {

	init {
		require(listOf(political, business, criminal).all { it in 0..MAX }) { "Connections run from 0 to $MAX." }
	}

	/** The level of [kind]. */
	fun of(kind: ConnectionKind): Int = when (kind) {
		ConnectionKind.POLITICAL -> political
		ConnectionKind.BUSINESS -> business
		ConnectionKind.CRIMINAL -> criminal
	}

	/** Whether [kind] is at [MAX] already, so it can't rise (R83). */
	fun isAtMax(kind: ConnectionKind): Boolean = of(kind) == MAX

	/**
	 * What raising [kind] by one point costs (R83, E12-S02): the new level × [RAISE_COST_PER_LEVEL] HT, so 3 → 4 costs
	 * 40 HT. Null at [MAX], where it can't rise.
	 */
	fun raiseCost(kind: ConnectionKind): Int? = if (isAtMax(kind)) null else (of(kind) + 1) * RAISE_COST_PER_LEVEL

	/** These connections with [kind] one point higher (R83); only below [MAX]. */
	fun raised(kind: ConnectionKind): Connections {
		check(!isAtMax(kind)) { "A connection can't go above $MAX" }
		return when (kind) {
			ConnectionKind.POLITICAL -> copy(political = political + 1)
			ConnectionKind.BUSINESS -> copy(business = business + 1)
			ConnectionKind.CRIMINAL -> copy(criminal = criminal + 1)
		}
	}

	/** These connections after a rise in market position (R83, E12-S02): business one point higher, unchanged at [MAX]. */
	fun afterMarketRise(): Connections = if (isAtMax(ConnectionKind.BUSINESS)) this else raised(ConnectionKind.BUSINESS)

	/**
	 * What these political connections take off a reputation control's second roll (R129): 1 from [CONTROL_HELP_FROM] to 8,
	 * 2 from 9 up (creator's table).
	 */
	fun controlReduction(): Int = when {
		political >= CONTROL_BIG_HELP_FROM -> 2
		political >= CONTROL_HELP_FROM -> 1
		else -> 0
	}

	/** These connections with [political] fewer political points and [others] fewer of each other kind, none below 0 (R129). */
	fun lowered(political: Int, others: Int): Connections = Connections(
		(this.political - political).coerceAtLeast(0),
		(business - others).coerceAtLeast(0),
		(criminal - others).coerceAtLeast(0)
	)

	/** The most units of factory a player with these connections may own in total, across all systems and goods (R84). */
	fun factoryCap(): Int = political + business

	/** The refusal of a factory purchase beyond [factoryCap], worded as the creator did (R84, E12-S03). */
	fun factoryCapText(): String = "You can only own ${factoryCap()} units of factory (POL: $political, BUSS: $business)."

	companion object {
		/** R81: the highest level of a connection. */
		const val MAX = 10

		/** R129: the political connections that start lowering a reputation control's second roll, by 1, and by 2. */
		const val CONTROL_HELP_FROM = 5
		const val CONTROL_BIG_HELP_FROM = 9

		/** R83: a raise costs the new level × 10 HT. */
		const val RAISE_COST_PER_LEVEL = 10

		/** R82: a player starts with 3 + d3 points to place. */
		const val BASE_STARTING_POINTS = 3

		/** Before a player places their starting points (R82); also what a saved game from before connections loads as. */
		val NONE = Connections(0, 0, 0)

		/**
		 * The connections a player's [points] starting points are placed as (R82, E12-S01): whole numbers from 0 to [MAX]
		 * that add up to [points]. A null is a number that was missing or not whole.
		 */
		fun placed(political: Int?, business: Int?, criminal: Int?, points: Int): Connections {
			if (political == null || business == null || criminal == null || listOf(political, business, criminal).any { it !in 0..MAX }) {
				throw InvalidConnectionsException("Enter a whole number from 0 to $MAX for each connection.")
			}
			if (political + business + criminal != points) {
				throw InvalidConnectionsException("Your connections must add up to $points points.")
			}
			return Connections(political, business, criminal)
		}
	}
}
