package dev.adriangrzebyk.trader.game

/** [turn] is null until the game starts; [log] holds the public events, oldest first. */
data class Game(
	val id: String,
	val turns: Int,
	val status: GameStatus,
	val creatorEmail: String,
	val players: List<Player>,
	val turn: Turn?,
	val log: List<String>
) {

	fun addPlayer(player: Player): Game {
		if (status != GameStatus.LOBBY) {
			throw InvalidGameOperationException("This game has already started")
		}
		if (players.size >= MAX_PLAYERS) {
			throw InvalidGameOperationException("This game is full")
		}
		if (hasPlayer(player.email)) {
			throw InvalidGameOperationException("This player is already in the game")
		}
		return copy(players = players + player)
	}

	fun hasPlayer(email: String): Boolean = players.any { it.email == email }

	/**
	 * Closes joining and sets every player up (R40, R41): 300 HT and two empty ships at the Ports of two
	 * different systems from [picker], then turn 1 Bidding. Only the creator may start, once, with 2+ players.
	 */
	fun start(callerEmail: String, picker: StartingSystemPicker): Game {
		if (callerEmail != creatorEmail) {
			throw NotTheCreatorException()
		}
		if (status != GameStatus.LOBBY) {
			throw InvalidGameOperationException("This game has already started")
		}
		if (players.size < MIN_PLAYERS_TO_START) {
			throw InvalidGameOperationException("At least $MIN_PLAYERS_TO_START players are needed to start")
		}

		val setUp = players.mapIndexed { index, player ->
			val (first, second) = picker.pickTwoDifferentSystems()
			val ships = listOf(
				Ship(Ship.NAMES[index * 2], first, ShipLocation.PORT),
				Ship(Ship.NAMES[index * 2 + 1], second, ShipLocation.PORT)
			)
			player.copy(money = STARTING_MONEY, ships = ships)
		}
		val positions = setUp.joinToString("; ") { player ->
			"${player.corporationName} — " + player.ships.joinToString(", ") { "${it.name} at ${it.system.displayName}" }
		}
		return copy(
			status = GameStatus.RUNNING,
			players = setUp,
			turn = Turn.bidding(1),
			log = log + "Game started: ${setUp.size} players, $turns turns" + "Starting positions: $positions"
		)
	}

	/**
	 * Moves to the next phase (N06, R04): Bidding → Ship-jumps → Trading → the next turn's Bidding. After the
	 * last turn's Trading (R41) the game is over and keeps its last turn. No clock calls this (D09): a phase ends
	 * only when its players are through, and the caller runs it inside [GameStore.update] so it happens once.
	 */
	fun advancePhase(): Game {
		val current = runningTurn()
		return when (current.phase) {
			TurnPhase.BIDDING -> copy(turn = Turn(current.number, TurnPhase.SHIP_JUMPS, emptyList()))
			TurnPhase.SHIP_JUMPS -> copy(turn = current.copy(phase = TurnPhase.TRADING))
			TurnPhase.TRADING -> if (current.number == turns) copy(status = GameStatus.FINISHED) else copy(turn = Turn.bidding(current.number + 1))
		}
	}

	/**
	 * Records that [email] is done bidding (R17: a readiness flag, not a commit, so a repeat changes nothing).
	 * Bidding closes the moment the last player signals done (D09); a signal after that is refused (A04).
	 */
	fun signalDoneBidding(email: String): Game {
		if (!hasPlayer(email)) {
			throw NotAGameMemberException()
		}
		val current = runningTurn()
		if (current.phase != TurnPhase.BIDDING) {
			throw PhaseClosedException(TurnPhase.BIDDING)
		}
		if (email in current.doneBidding) {
			return this
		}
		val doneBidding = current.doneBidding + email
		val signalled = copy(turn = current.copy(doneBidding = doneBidding))
		return if (players.all { it.email in doneBidding }) signalled.advancePhase() else signalled
	}

	private fun runningTurn(): Turn = when (status) {
		GameStatus.LOBBY -> throw InvalidGameOperationException("This game hasn't started")
		GameStatus.FINISHED -> throw InvalidGameOperationException("This game is over")
		GameStatus.RUNNING -> checkNotNull(turn) { "A running game always has a turn" }
	}

	companion object {
		const val MAX_PLAYERS = 6
		const val MIN_PLAYERS_TO_START = 2

		/** R40: every player starts with 300 HT. */
		const val STARTING_MONEY = 300

		/** A new game waiting in the lobby with its creator as the only player. */
		fun lobby(id: String, turns: Int, creator: Player) = Game(id, turns, GameStatus.LOBBY, creator.email, listOf(creator), null, emptyList())
	}
}
