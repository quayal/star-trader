package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.BiddingService
import dev.adriangrzebyk.trader.game.CargoService
import dev.adriangrzebyk.trader.game.Dice
import dev.adriangrzebyk.trader.game.GameCreationService
import dev.adriangrzebyk.trader.game.GameIdGenerator
import dev.adriangrzebyk.trader.game.GameJoiningService
import dev.adriangrzebyk.trader.game.GamePollingService
import dev.adriangrzebyk.trader.game.GameStartingService
import dev.adriangrzebyk.trader.game.GameStore
import dev.adriangrzebyk.trader.game.InvalidGameOperationException
import dev.adriangrzebyk.trader.game.InvestmentService
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.ShipJumpsService
import dev.adriangrzebyk.trader.game.StartingSystemPicker
import dev.adriangrzebyk.trader.game.TradingService
import dev.adriangrzebyk.trader.login.Session
import dev.adriangrzebyk.trader.login.SessionStore
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * The game's HTTP surface (BACKWARD_COMPATIBILITY.md §6). Each endpoint reads the request, calls its service and answers
 * with the caller's view of the game; what a refusal from the game answers is [GameExceptionAdvice]'s.
 */
@RestController
@RequestMapping("/api")
class GameController(
	idGenerator: GameIdGenerator,
	gameStore: GameStore,
	startingSystemPicker: StartingSystemPicker,
	dice: Dice,
	private val sessionStore: SessionStore
) {

	private val gameCreationService = GameCreationService(idGenerator, gameStore)
	private val gameJoiningService = GameJoiningService(gameStore)
	private val gamePollingService = GamePollingService(gameStore)
	private val gameStartingService = GameStartingService(gameStore, startingSystemPicker)
	private val biddingService = BiddingService(gameStore, dice)
	private val cargoService = CargoService(gameStore)
	private val investmentService = InvestmentService(gameStore)
	private val shipJumpsService = ShipJumpsService(gameStore)
	private val tradingService = TradingService(gameStore, dice)

	@PostMapping("/games")
	@ResponseStatus(HttpStatus.CREATED)
	fun createGame(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@RequestBody(required = false) request: CreateGameRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val creator = Player.newcomer(session.email, session.playerName, session.corporationName)
		val game = try {
			gameCreationService.createGame(creator, request?.wholeTurns())
		} catch (ex: InvalidGameOperationException) {
			// The one refusal that isn't about the game's state: a length that can't be used is the caller's to correct.
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		}

		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/join")
	fun joinGame(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val player = Player.newcomer(session.email, session.playerName, session.corporationName)
		val game = gameJoiningService.joinGame(id, player)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The caller's view of the game. A page that polls sends `logAfter`, the number of log entries it already has, and gets
	 * only the newer ones with `logOffset` saying where they start; without it, the whole log.
	 */
	@GetMapping("/games/{id}")
	fun pollGame(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestParam(required = false) logAfter: Int?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = gamePollingService.poll(id, session.email)

		return GameResponseDto(game, session.email, logAfter ?: 0)
	}

	@PostMapping("/games/{id}/start")
	fun startGame(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = gameStartingService.startGame(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/** Done is a readiness flag (R44); the last player's signal closes Investment for everyone and ends the turn (D09). */
	@PostMapping("/games/{id}/investment/done")
	fun signalDoneInvesting(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = investmentService.signalDone(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player with factories confirms where this turn's output goes (R47, E06-S03-5): what isn't placed or doesn't fit is
	 * lost. `400` for a placement the rules refuse or that isn't a whole number, `409` outside Investment or when they have
	 * already collected or have no factories, `403`, `404` and `401` as for the other actions.
	 */
	@PostMapping("/games/{id}/investment/collect")
	fun collectOutput(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: CollectRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val placements = (request ?: CollectRequestDto()).outputPlacements()
			?: throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Choose a destination and a whole number of units, 0 or more, for each placement."
			)
		val game = investmentService.collect(id, session.email, placements)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player buys warehouse blocks at a system where they have a ship (R45, E06-S04): `{ system, blocks }`. `400` for an
	 * unknown system, blocks that aren't a whole number, none, no ship there or too little money; `409` outside Investment or
	 * before they have collected; `403`, `404` and `401` as for the other actions.
	 */
	@PostMapping("/games/{id}/investment/warehouse-blocks")
	fun buyWarehouseBlocks(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: BuyBlocksRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val order = request ?: BuyBlocksRequestDto()
		val system = order.starSystem()
		val blocks = order.wholeBlocks()
		if (system == null || blocks == null) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a system and a whole number of blocks, 1 or more.")
		}
		val game = investmentService.buyWarehouseBlocks(id, session.email, system, blocks)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player builds or extends a factory at a system where they have a ship (R46, E06-S05): `{ system, goods, units }`.
	 * `400` for an unknown system or good, units that aren't a whole number, fewer than 1, a good the system can't produce,
	 * no ship there or too little money; `409` outside Investment or before they have collected; `403`, `404` and `401` as
	 * for the other actions.
	 */
	@PostMapping("/games/{id}/investment/factory-units")
	fun buyFactoryUnits(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: BuyUnitsRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val order = request ?: BuyUnitsRequestDto()
		val system = order.starSystem()
		val goods = order.goodsConstant()
		val units = order.wholeUnits()
		if (system == null || goods == null || units == null) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a system, a good and a whole number of units, 1 or more.")
		}
		val game = investmentService.buyFactoryUnits(id, session.email, system, goods, units)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player moves goods between one of their ships in port and their warehouse there (R58, E08-S02): `{ ship, goods,
	 * units, to }`, in any phase of a running game. `400` for an unknown good or destination, units that aren't a whole
	 * number, and a move the rules refuse (not their ship, not in port, no warehouse there, more than the source holds or
	 * than fits); `409` before the game starts and once it's over; `403`, `404` and `401` as for the other actions.
	 */
	@PostMapping("/games/{id}/cargo/moves")
	fun moveCargo(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: MoveCargoRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val move = request ?: MoveCargoRequestDto()
		val goods = move.goodsConstant()
		val units = move.wholeUnits()
		val to = move.destination()
		if (goods == null || units == null || to == null) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a ship, a good, where it goes and a whole number of units, 1 or more.")
		}
		val game = cargoService.move(id, session.email, move.ship, goods, units, to)

		return GameResponseDto(game, session.email)
	}

	/** Done is a readiness flag (R17); the last player's signal closes Bidding for everyone (D09). */
	@PostMapping("/games/{id}/bidding/done")
	fun signalDoneBidding(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = biddingService.signalDone(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/**
	 * Places the caller's secret offer (R17) and answers with their view of the game, which carries their own
	 * offers only. `409` "Bidding has closed" once it has (A04); `400` for an offer the rules refuse.
	 */
	@PostMapping("/games/{id}/offers")
	@ResponseStatus(HttpStatus.CREATED)
	fun placeOffer(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: PlaceOfferRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val system = request?.system() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a star system.")
		val goods = request.goods() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a good.")
		val type = request.type() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Buy or Sell.")
		val game = biddingService.placeOffer(id, session.email, system, goods, type, request.wholePrice())

		return GameResponseDto(game, session.email)
	}

	/**
	 * Places or changes the caller's secret initiative bid (R27, S05-1, S05-2). Only the caller's own `you.bid` ever
	 * carries it (R29); it's paid when Bidding closes.
	 */
	@PutMapping("/games/{id}/bidding/bid")
	fun placeInitiativeBid(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: PlaceBidRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = biddingService.placeInitiativeBid(id, session.email, request?.wholeAmount())

		return GameResponseDto(game, session.email)
	}

	/**
	 * The caller picks their place in this turn's turn order (R26), in initiative order (R27). `409` out of turn, once
	 * the order is set, or outside Ship-jumps; `400` for a place that isn't free.
	 */
	@PostMapping("/games/{id}/ship-jumps/turn-order")
	fun pickTurnOrderPlace(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: PickPlaceRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = shipJumpsService.pickPlace(id, session.email, request?.wholePlace())

		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/ship-jumps/jumps")
	fun jumpShip(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: JumpShipRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = shipJumpsService.jump(id, session.email, request?.ship, request?.destination())

		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/ship-jumps/landing")
	fun landShip(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: LandShipRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = shipJumpsService.land(id, session.email, request?.location())

		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/ship-jumps/finish")
	fun finishJumping(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = shipJumpsService.finishJumping(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The first player in initiative puts the goods still to resolve in a new order (R25, S01-3). `409` for anyone else
	 * ("Order decided by …"), for an order that no longer matches what's left, or outside Trading; `400` for a body that
	 * doesn't list goods at systems.
	 */
	@PutMapping("/games/{id}/trading/queue")
	fun reorderTradingQueue(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: ReorderQueueRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val order = request?.order() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Send the remaining goods in their new order.")
		val game = tradingService.reorderQueue(id, session.email, order)

		return GameResponseDto(game, session.email)
	}

	/** The first player in initiative resolves the next good in the queue (R25, S01-4); `409` as for the queue. */
	@PostMapping("/games/{id}/trading/resolve-next")
	fun resolveNextGood(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = tradingService.resolveNext(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The Dictator asked places their one offer for the good resolving (R38, E05-S02-2). `400` for a type or price the
	 * rules refuse (R03); `409` "Waiting for … (Dictator) to decide." for anyone else, "No Dictator decision is pending."
	 * when nobody is asked, or outside Trading.
	 */
	@PostMapping("/games/{id}/trading/dictator-offer")
	fun placeDictatorOffer(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: DictatorOfferRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val type = request?.type() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Buy or Sell.")
		val game = tradingService.placeDictatorOffer(id, session.email, type, request.wholePrice())

		return GameResponseDto(game, session.email)
	}

	/** The Dictator asked passes (R38, E05-S02-2); `409` as for the Dictator's offer. */
	@PostMapping("/games/{id}/trading/dictator-pass")
	fun passAsDictator(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = tradingService.passAsDictator(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The trader the counter reached chooses their amount and ships (E05-S04). `400` for an amount or split the rules
	 * refuse ("Enter a whole number from 1 to {max}."); `409` "{corporation} can buy up to 4 u — waiting for their
	 * decision." for anyone else, "No trade is waiting for a decision." when nobody is asked, or outside Trading.
	 */
	@PostMapping("/games/{id}/trading/trade")
	fun trade(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: TradeRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val loads = (request ?: TradeRequestDto()).loads()
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a whole number of units, 0 or more, for each ship.")
		val warehouseUnits = (request ?: TradeRequestDto()).warehouseUnits()
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a whole number of units, 0 or more, for your warehouse.")
		val game = tradingService.trade(id, session.email, request?.wholeAmount(), loads, warehouseUnits)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player the wholesale step asks buys or sells up to 5 u at the new price (R37, E05-S05-4). `400` "Choose Buy or
	 * Sell, or pass." for a missing or unknown type, and for an amount or split the rules refuse ("Enter a whole number
	 * from 1 to {max}.", "You can't afford any units at 10 HT."); `409` "Waiting for {corporation} (Wholesaler) to trade
	 * up to 5 u at 10 HT, or pass." for anyone else, "No wholesale decision is pending." when nobody is asked, or outside
	 * Trading.
	 */
	@PostMapping("/games/{id}/trading/wholesale")
	fun wholesale(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: WholesaleRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val wholesale = request ?: WholesaleRequestDto()
		val loads = wholesale.trade().loads()
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a whole number of units, 0 or more, for each ship.")
		val warehouseUnits = wholesale.trade().warehouseUnits()
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a whole number of units, 0 or more, for your warehouse.")
		val game = tradingService.wholesale(id, session.email, wholesale.type(), wholesale.trade().wholeAmount(), loads, warehouseUnits)

		return GameResponseDto(game, session.email)
	}

	/** The player the wholesale step asks passes (S05-4); `409`, `403`, `404` and `401` as for `/trading/wholesale`. */
	@PostMapping("/games/{id}/trading/wholesale-pass")
	fun passWholesale(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = tradingService.passWholesale(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/** The trader the counter reached trades nothing (E05-S04-5); `409`, `403`, `404` and `401` as for `/trading/trade`. */
	@PostMapping("/games/{id}/trading/trade-nothing")
	fun tradeNothing(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = tradingService.tradeNothing(id, session.email)

		return GameResponseDto(game, session.email)
	}

	private fun sessionFor(authorization: String?): Session {
		val token = authorization?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
			?.substring(BEARER_PREFIX.length)?.trim()
		return token?.takeIf { it.isNotEmpty() }?.let { sessionStore.find(it) }
			?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
	}

	companion object {
		private const val BEARER_PREFIX = "Bearer "
	}
}
