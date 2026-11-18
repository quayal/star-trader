package dev.adriangrzebyk.trader.game.battle

import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.InvalidGameOperationException

/**
 * A battle in Space at [system] (R73–R77), a sub-phase of Ship-jumps. [ships] are the ships still fighting, [interceptions]
 * which ship caught which (a caught ship can't break away while its interceptor fights, R74), and [leftShips] those
 * that broke away. A battle started by a ship staying in Space has no interceptions (R80). In round [round] it is at
 * [stage]: in [BattleStage.ORDERS] every player with a ship fighting gives [orders], secret until all have ([done]); in
 * [BattleStage.HITS] the owners of [pendingHits] place them; in [BattleStage.LANDING] every player with a surviving ship
 * from the battle places it ([done]). [tries] are every interception try at the ship that started it, successful or not
 * (R70), and [firingPenalized] the players who have already lost reputation for firing at a ship that didn't try to
 * intercept any of theirs, once per battle each (R86, E13-S04).
 */
data class Battle(
	val system: StarSystem,
	val round: Int,
	val stage: BattleStage,
	val ships: List<BattleShip>,
	val interceptions: List<BattleInterception>,
	val leftShips: List<BattleShip>,
	val orders: List<BattleOrder>,
	val pendingHits: List<PendingHits>,
	val done: List<String>,
	val tries: List<BattleInterception>,
	val firingPenalized: List<String>
) {

	/** The players with ships still fighting, in the order of [ships]. */
	fun fighters(): List<String> = ships.map { it.ownerEmail }.distinct()

	/** The players with a surviving ship from this battle, fighting or not (R77). */
	fun landers(): List<String> = (ships + leftShips).map { it.ownerEmail }.distinct()

	/** The ships still fighting that hold [ship] (R74): those that intercepted it and haven't been destroyed or left. */
	fun interceptorsOf(ship: String): List<String> =
		interceptions.filter { it.ship == ship && ships.any { fighting -> fighting.name == it.interceptor } }.map { it.interceptor }

	/** Whether [ship] may break away (R74): nothing that intercepted it is still fighting. */
	fun canBreakAway(ship: String): Boolean = interceptorsOf(ship).isEmpty()

	/**
	 * The players this round's fire orders cost reputation (R86, E13-S04), in order: each who fires at a ship that didn't
	 * try to intercept any of their ships, once per battle. Returning fire at a ship that tried costs nothing; in a battle
	 * nobody intercepted (R80), the first shot does.
	 */
	fun firingPenalties(): List<String> = fireOrders()
		.filter { order -> tries.none { it.interceptor == order.targetShip() && ownerOf(it.ship) == ownerOf(order.ship) } }
		.map { ownerOf(it.ship) }
		.distinct()
		.filterNot { it in firingPenalized }

	/** This battle once [emails] have lost reputation for firing (R86): they don't again in this battle. */
	fun firingPenalizedFor(emails: List<String>): Battle = copy(firingPenalized = firingPenalized + emails)

	/** Whom this battle waits for now. */
	fun waitingFor(): List<String> = when (stage) {
		BattleStage.ORDERS -> fighters() - done.toSet()
		BattleStage.HITS -> pendingHits.map { it.ownerEmail }.distinct()
		BattleStage.LANDING -> landers() - done.toSet()
	}

	/** The owner of [ship], fighting or not. */
	fun ownerOf(ship: String): String =
		checkNotNull((ships + leftShips).firstOrNull { it.name == ship }) { "$ship isn't in this battle" }.ownerEmail

	/** [email]'s ships still fighting. */
	fun fightingShipsOf(email: String): List<String> = ships.filter { it.ownerEmail == email }.map { it.name }

	/** [email]'s surviving ships from this battle, which they place when it ends (R77). */
	fun survivorsOf(email: String): List<String> = (ships + leftShips).filter { it.ownerEmail == email }.map { it.name }

	/** [email]'s orders this round, which only they may see until everyone has given theirs (R74). */
	fun ordersOf(email: String): List<BattleOrder> = orders.filter { ownerOf(it.ship) == email }

	/**
	 * This battle with [email]'s [given] orders for this round (R74): exactly one for each of their fighting ships; fire at
	 * a ship of another player in the battle; break away only for a ship that wasn't intercepted.
	 */
	fun giveOrders(email: String, given: List<BattleOrder>): Battle {
		if (stage != BattleStage.ORDERS) {
			throw InvalidGameOperationException("This round's orders are closed.")
		}
		if (email !in fighters()) {
			throw InvalidGameOperationException("You have no ship fighting in this battle.")
		}
		if (email in done) {
			throw InvalidGameOperationException("You have already given your orders this round.")
		}
		val mine = fightingShipsOf(email)
		if (given.map { it.ship }.sorted() != mine.sorted()) {
			throw InvalidBattleException("Give one order to each of your ships in the battle: ${mine.joinToString(", ")}.")
		}
		given.forEach { checkOrder(email, it) }
		return copy(orders = orders + given, done = done + email)
	}

	private fun checkOrder(email: String, order: BattleOrder) {
		when (order.action) {
			BattleAction.BREAK_AWAY -> if (!canBreakAway(order.ship)) {
				val by = interceptorsOf(order.ship).joinToString(" and ")
				throw InvalidBattleException("${order.ship} was intercepted by $by: it can't break away while $by fights.")
			}
			BattleAction.FIRE -> if (ships.none { it.name == order.target && it.ownerEmail != email }) {
				throw InvalidBattleException("Choose an enemy ship in the battle for ${order.ship} to fire at.")
			}
		}
	}

	/** Whether every fighting player has given their orders, so the round resolves (R74). */
	fun isRoundOrdered(): Boolean = stage == BattleStage.ORDERS && waitingFor().isEmpty()

	/** This round's orders to fire, in the order they were given. */
	fun fireOrders(): List<BattleOrder> = orders.filter { it.action == BattleAction.FIRE }

	/** The ships breaking away this round (R74). */
	fun breakingAway(): List<String> = orders.filter { it.action == BattleAction.BREAK_AWAY }.map { it.ship }

	/** Whether [ship] breaks away this round (R74): a salvo at it misses. */
	fun isBreakingAway(ship: String): Boolean = ship in breakingAway()

	/** The names of the ships still fighting. */
	fun fightingShips(): List<String> = ships.map { it.name }

	/**
	 * This round once its [salvos] have landed (R74–R77): the ships breaking away leave; a rocket kill, or more hits than
	 * [hitsSurvivable] says a ship can absorb (by ship name), destroys it; the rest of the hits wait for their owners to
	 * place them. The round's public log lines (R78) and its report to the players come with it.
	 */
	fun fired(salvos: List<Salvo>, hitsSurvivable: Map<String, Int>): FiredRound {
		val leaving = breakingAway()
		val rocketKills = salvos.filter { it.destroysTarget() }.map { it.target }.toSet()
		val hitsByShip = salvos.filter { it.hits > 0 }.groupBy { it.target }.mapValues { (_, hits) -> hits.sumOf { it.hits } }
		val overwhelmed = hitsByShip.filter { (ship, hits) -> ship !in rocketKills && hits > hitsSurvivable.getValue(ship) }.keys
		val destroyed = rocketKills + overwhelmed
		val pending = hitsByShip.filterKeys { it !in destroyed }.map { (ship, hits) -> PendingHits(ship, ownerOf(ship), hits) }
		val log = listOf("Battle at ${system.displayName}, round $round") +
			leaving.map { "$it breaks away" } + salvos.map { it.log() } + destroyed.map { "$it is destroyed" }
		val report = BattleReport(
			system,
			round,
			salvos.map { SalvoResult.of(it, ownerOf(it.firer), ownerOf(it.target), destroyed) }
		)
		return FiredRound(afterSalvos(leaving.toSet(), destroyed, pending), destroyed, log, report)
	}

	/**
	 * This battle once the round's salvos landed: [leaving] ships have left, [destroyed] ones are gone, and [hits] wait to
	 * be placed; with none, the next round starts or the battle is over.
	 */
	private fun afterSalvos(leaving: Set<String>, destroyed: Set<String>, hits: List<PendingHits>): Battle {
		val after = copy(
			ships = ships.filterNot { it.name in leaving || it.name in destroyed },
			leftShips = leftShips + ships.filter { it.name in leaving },
			orders = emptyList(),
			pendingHits = hits,
			done = emptyList(),
			stage = BattleStage.HITS
		)
		return if (hits.isEmpty()) after.nextRound() else after
	}

	/** [email]'s ships with hits to place (R76), refused when they have none to place now. */
	fun pendingHitsOf(email: String): List<PendingHits> {
		val mine = pendingHits.filter { it.ownerEmail == email }
		if (stage != BattleStage.HITS || mine.isEmpty()) {
			throw InvalidGameOperationException("You have no hits to place.")
		}
		return mine
	}

	/** [email]'s hits to place (R76), refused unless [placed] names exactly their ships that took them. */
	fun hitsToPlace(email: String, placed: Set<String>): List<PendingHits> {
		val mine = pendingHitsOf(email)
		if (placed != mine.map { it.ship }.toSet()) {
			throw InvalidBattleException("Place the hits on each of your ships that took them: ${mine.joinToString(", ") { it.ship }}.")
		}
		return mine
	}

	/** This battle once [email] placed their hits, which [destroyed] their ships named there (R76, R77). */
	fun hitsPlaced(email: String, destroyed: Set<String>): Battle {
		pendingHitsOf(email)
		val after = copy(ships = ships.filterNot { it.name in destroyed }, pendingHits = pendingHits.filterNot { it.ownerEmail == email })
		return if (after.pendingHits.isEmpty()) after.nextRound() else after
	}

	/** The next round, or the landing once fewer than two players have ships fighting (R77). */
	private fun nextRound(): Battle = if (fighters().size >= 2) {
		copy(round = round + 1, stage = BattleStage.ORDERS, done = emptyList())
	} else {
		copy(stage = BattleStage.LANDING, done = emptyList())
	}

	/**
	 * This battle once [email] placed their surviving ships (R77), [placed] naming exactly those ships; refused before the
	 * battle is over, or twice.
	 */
	fun landedBy(email: String, placed: Set<String>): Battle {
		if (stage != BattleStage.LANDING) {
			throw InvalidGameOperationException("The battle isn't over yet.")
		}
		if (email !in landers() || email in done) {
			throw InvalidGameOperationException("You have no ship from the battle to place.")
		}
		val survivors = survivorsOf(email)
		if (placed != survivors.toSet()) {
			throw InvalidBattleException("Choose where each of your ships from the battle ends up: ${survivors.joinToString(", ")}.")
		}
		return copy(done = done + email)
	}

	/** The ships [email] still has to place now the battle is over (R77): none before then, or once they have. */
	fun shipsToLand(email: String): List<String> = if (stage == BattleStage.LANDING && email !in done) survivorsOf(email) else emptyList()

	/** Whether the battle has just ended (R77): nobody fights on and nobody has placed a surviving ship yet. */
	fun isJustOver(): Boolean = stage == BattleStage.LANDING && done.isEmpty()

	/** Whether the battle is over and every surviving ship has been placed (R77): the jumps go on. */
	fun isFinished(): Boolean = stage == BattleStage.LANDING && waitingFor().isEmpty()

	companion object {
		/**
		 * The battle at [system] with every ship in Space there (R73, R80): after a ship was caught by [interceptions], or
		 * with none when a ship stayed in Space next to other players' ships. Of [tries], this turn's interception tries, it
		 * keeps those between two of [ships], which decide whose fire costs reputation (R86), even with no interception.
		 */
		fun start(system: StarSystem, ships: List<BattleShip>, interceptions: List<BattleInterception>, tries: List<BattleInterception>): Battle {
			val names = ships.map { it.name }.toSet()
			val here = tries.filter { it.ship in names && it.interceptor in names }
			return Battle(system, 1, BattleStage.ORDERS, ships, interceptions, emptyList(), emptyList(), emptyList(), emptyList(), here, emptyList())
		}
	}
}

/** Where a battle is (R74–R77): players give orders, place hits, or place their surviving ships. */
enum class BattleStage { ORDERS, HITS, LANDING }

/** [interceptor] caught [ship] on its arrival (R70): [ship] can't break away while [interceptor] fights (R74). */
data class BattleInterception(val ship: String, val interceptor: String)

/** A ship in a battle, [name], and its owner. */
data class BattleShip(val name: String, val ownerEmail: String)

/** What a player orders [ship] to do this round (R74): fire at [target], with [rockets] or not, or break away. */
data class BattleOrder(val ship: String, val action: BattleAction, val target: String?, val rockets: Boolean) {

	/** The ship an order to fire is aimed at. */
	fun targetShip(): String = checkNotNull(target) { "$ship's order isn't to fire" }
}

/**
 * A battle round once its salvos have landed (R74–R78): the [battle] after it, the ships it [destroyed], its public [log]
 * and the [report] each player is shown.
 */
data class FiredRound(val battle: Battle, val destroyed: Set<String>, val log: List<String>, val report: BattleReport)

/** A ship's order in a battle round (R74). */
enum class BattleAction { FIRE, BREAK_AWAY }

/** [hits] that [ship] took this round, waiting for its owner to place them (R76). */
data class PendingHits(val ship: String, val ownerEmail: String, val hits: Int)
