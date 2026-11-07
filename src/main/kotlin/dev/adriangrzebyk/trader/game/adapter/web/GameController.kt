package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.BiddingService
import dev.adriangrzebyk.trader.game.Dice
import dev.adriangrzebyk.trader.game.GameCreationService
import dev.adriangrzebyk.trader.game.GameIdGenerator
import dev.adriangrzebyk.trader.game.GameJoiningService
import dev.adriangrzebyk.trader.game.GameNotFoundException
import dev.adriangrzebyk.trader.game.GamePollingService
import dev.adriangrzebyk.trader.game.GameStartingService
import dev.adriangrzebyk.trader.game.GameStore
import dev.adriangrzebyk.trader.game.InvalidBidException
import dev.adriangrzebyk.trader.game.InvalidGameOperationException
import dev.adriangrzebyk.trader.game.InvalidJumpException
import dev.adriangrzebyk.trader.game.InvalidLandingException
import dev.adriangrzebyk.trader.game.InvalidOfferException
import dev.adriangrzebyk.trader.game.InvalidPlaceException
import dev.adriangrzebyk.trader.game.NotAGameMemberException
import dev.adriangrzebyk.trader.game.NotTheCreatorException
import dev.adriangrzebyk.trader.game.NotYourTurnException
import dev.adriangrzebyk.trader.game.PhaseClosedException
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

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
	private val shipJumpsService = ShipJumpsService(gameStore)
	private val tradingService = TradingService(gameStore)

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
		val game = try {
			gameJoiningService.joinGame(id, player)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

		return GameResponseDto(game, session.email)
	}

	@GetMapping("/games/{id}")
	fun pollGame(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = try {
			gamePollingService.poll(id, session.email)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		}

		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/start")
	fun startGame(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = try {
			gameStartingService.startGame(id, session.email)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotTheCreatorException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

		return GameResponseDto(game, session.email)
	}

	/** Done is a readiness flag (R17); the last player's signal closes Bidding for everyone (D09). */
	@PostMapping("/games/{id}/bidding/done")
	fun signalDoneBidding(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = try {
			biddingService.signalDone(id, session.email)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		} catch (ex: PhaseClosedException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

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
		val game = try {
			biddingService.placeOffer(id, session.email, system, goods, type, request.wholePrice())
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		} catch (ex: InvalidOfferException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		} catch (ex: PhaseClosedException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

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
		val game = try {
			biddingService.placeInitiativeBid(id, session.email, request?.wholeAmount())
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		} catch (ex: InvalidBidException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		} catch (ex: PhaseClosedException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

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
		val game = try {
			shipJumpsService.pickPlace(id, session.email, request?.wholePlace())
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		} catch (ex: InvalidPlaceException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		} catch (ex: NotYourTurnException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: PhaseClosedException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/ship-jumps/jumps")
	fun jumpShip(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: JumpShipRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = try {
			shipJumpsService.jump(id, session.email, request?.ship, request?.destination())
		} catch (ex: InvalidJumpException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		} catch (ex: NotYourTurnException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: PhaseClosedException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/ship-jumps/landing")
	fun landShip(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String,
		@RequestBody(required = false) request: LandShipRequestDto?
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = try {
			shipJumpsService.land(id, session.email, request?.location())
		} catch (ex: InvalidLandingException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		} catch (ex: NotYourTurnException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: PhaseClosedException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/ship-jumps/finish")
	fun finishJumping(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = try {
			shipJumpsService.finishJumping(id, session.email)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		} catch (ex: NotYourTurnException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: PhaseClosedException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

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
		val game = try {
			tradingService.reorderQueue(id, session.email, order)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		} catch (ex: NotYourTurnException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

		return GameResponseDto(game, session.email)
	}

	/** The first player in initiative resolves the next good in the queue (R25, S01-4); `409` as for the queue. */
	@PostMapping("/games/{id}/trading/resolve-next")
	fun resolveNextGood(
		@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
		@PathVariable id: String
	): GameResponseDto {
		val session = sessionFor(authorization)
		val game = try {
			tradingService.resolveNext(id, session.email)
		} catch (ex: GameNotFoundException) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
		} catch (ex: NotAGameMemberException) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, ex.message)
		} catch (ex: NotYourTurnException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		} catch (ex: InvalidGameOperationException) {
			throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
		}

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
