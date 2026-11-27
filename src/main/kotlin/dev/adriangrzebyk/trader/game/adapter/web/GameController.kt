package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.board.Goods
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.BiddingService
import dev.adriangrzebyk.trader.game.CargoService
import dev.adriangrzebyk.trader.game.ControlService
import dev.adriangrzebyk.trader.game.Dice
import dev.adriangrzebyk.trader.game.GameCreationService
import dev.adriangrzebyk.trader.game.GameIdGenerator
import dev.adriangrzebyk.trader.game.GameJoiningService
import dev.adriangrzebyk.trader.game.GameNotFoundException
import dev.adriangrzebyk.trader.game.GamePollingService
import dev.adriangrzebyk.trader.game.GameStartingService
import dev.adriangrzebyk.trader.game.GameStore
import dev.adriangrzebyk.trader.game.InvalidGameOperationException
import dev.adriangrzebyk.trader.game.InvestmentService
import dev.adriangrzebyk.trader.game.NewsService
import dev.adriangrzebyk.trader.game.NotepadService
import dev.adriangrzebyk.trader.game.NotepadStore
import dev.adriangrzebyk.trader.game.OpenGamesService
import dev.adriangrzebyk.trader.game.OpportunityService
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.ShipJumpsService
import dev.adriangrzebyk.trader.game.TradingService
import dev.adriangrzebyk.trader.login.Session
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
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
class GameController(idGenerator: GameIdGenerator, private val gameStore: GameStore, notepadStore: NotepadStore, dice: Dice) {

	private val gameCreationService = GameCreationService(idGenerator, gameStore)
	private val gameJoiningService = GameJoiningService(gameStore)
	private val gamePollingService = GamePollingService(gameStore)
	private val openGamesService = OpenGamesService(gameStore)
	private val gameStartingService = GameStartingService(gameStore, dice)
	private val biddingService = BiddingService(gameStore, dice)
	private val cargoService = CargoService(gameStore)
	private val investmentService = InvestmentService(gameStore, dice)
	private val controlService = ControlService(gameStore, dice)
	private val notepadService = NotepadService(gameStore, notepadStore)
	private val opportunityService = OpportunityService(gameStore, dice)
	private val newsService = NewsService(gameStore)
	private val shipJumpsService = ShipJumpsService(gameStore, dice)
	private val tradingService = TradingService(gameStore, dice)

	@PostMapping("/games")
	@ResponseStatus(HttpStatus.CREATED)
	fun createGame(@RequestBody(required = false) request: CreateGameRequestDto?, session: Session): GameResponseDto {
		val creator = Player.newcomer(session.email, session.playerName, session.corporationName)
		val game = try {
			gameCreationService.createGame(creator, request?.wholeTurns())
		} catch (ex: InvalidGameOperationException) {
			// The one refusal that isn't about the game's state: a length that can't be used is the caller's to correct.
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		}

		return GameResponseDto(game, session.email)
	}

	/** R119: every game still in its lobby, as the caller sees it, the ones they're in or asked to join first. */
	@GetMapping("/open-games")
	fun openGames(session: Session): List<OpenGameResponseDto> =
		openGamesService.openGames(session.email).map { OpenGameResponseDto(it, session.email) }

	/** R120: the caller asks to join; they're in once the creator accepts. */
	@PostMapping("/games/{id}/join-requests")
	fun requestToJoin(@PathVariable id: String, session: Session): OpenGameResponseDto {
		val player = Player.newcomer(session.email, session.playerName, session.corporationName)
		return OpenGameResponseDto(gameJoiningService.requestToJoin(id, player), session.email)
	}

	/** R120: the caller no longer asks to join; nothing changes when they hadn't asked. */
	@DeleteMapping("/games/{id}/join-requests/mine")
	fun withdrawJoinRequest(@PathVariable id: String, session: Session): OpenGameResponseDto =
		OpenGameResponseDto(gameJoiningService.withdrawJoinRequest(id, session.email), session.email)

	@PostMapping("/games/{id}/join-requests/{request}/accept")
	fun acceptJoinRequest(@PathVariable id: String, @PathVariable request: String, session: Session): GameResponseDto {
		val game = gameJoiningService.acceptJoinRequest(id, session.email, requesterEmail(id, request))
		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/join-requests/{request}/decline")
	fun declineJoinRequest(@PathVariable id: String, @PathVariable request: String, session: Session): GameResponseDto {
		val game = gameJoiningService.declineJoinRequest(id, session.email, requesterEmail(id, request))
		return GameResponseDto(game, session.email)
	}

	/**
	 * The caller's view of the game. A page that polls sends `logAfter`, the number of log entries it already has, and gets
	 * only the newer ones with `logOffset` saying where they start; without it, the whole log.
	 */
	@GetMapping("/games/{id}")
	fun pollGame(@PathVariable id: String, @RequestParam(required = false) logAfter: Int?, session: Session): GameResponseDto {
		val game = gamePollingService.poll(id, session.email)

		return GameResponseDto(game, session.email, logAfter ?: 0)
	}

	@PostMapping("/games/{id}/start")
	fun startGame(@PathVariable id: String, session: Session): GameResponseDto {
		val game = gameStartingService.startGame(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player names their starting ship and chooses its Port in turn 1's Bidding (R132): `{ name, system }`. `400` for a bad
	 * name or one in use; `409` outside turn 1's Bidding or once placed; `403`, `404` and `401` as for the other actions.
	 */
	@PostMapping("/games/{id}/starting-ship")
	fun placeStartingShip(
		@PathVariable id: String,
		@RequestBody(required = false) request: StartingShipRequestDto?,
		session: Session
	): GameResponseDto {
		val placement = request ?: StartingShipRequestDto()

		return GameResponseDto(biddingService.placeStartingShip(id, session.email, placement.name, placement.system()), session.email)
	}

	/**
	 * The player places their starting connection points in turn 1's Bidding (R82, E12-S01): `{ political, business,
	 * criminal }`. `400` for numbers that aren't whole, out of 0–10 or don't add up to their points; `409` outside Bidding
	 * or once placed; `403`, `404` and `401` as for the other actions.
	 */
	@PostMapping("/games/{id}/connections/placement")
	fun placeConnections(
		@PathVariable id: String,
		@RequestBody(required = false) request: PlaceConnectionsRequestDto?,
		session: Session
	): GameResponseDto {
		val placement = request ?: PlaceConnectionsRequestDto()
		val game = biddingService.placeConnections(
			id,
			session.email,
			placement.wholePolitical(),
			placement.wholeBusiness(),
			placement.wholeCriminal()
		)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player raises one connection by one point in Investment (R83, E12-S02): `{ kind }`, a `ConnectionKind` constant.
	 * `400` for an unknown kind, one at 10 or too little money; `409` outside Investment, before they have collected, or
	 * once they raised one this Investment; `403`, `404` and `401` as for the other actions.
	 */
	@PostMapping("/games/{id}/connections/raise")
	fun raiseConnection(
		@PathVariable id: String,
		@RequestBody(required = false) request: RaiseConnectionRequestDto?,
		session: Session
	): GameResponseDto {
		val kind = (request ?: RaiseConnectionRequestDto()).connectionKind()
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose political, business or criminal connections.")
		val game = investmentService.raiseConnection(id, session.email, kind)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player buys reputation in Investment (R87, E13-S03): 20 HT for 1d6 more. `400` at 40 or with too little money;
	 * `409` outside Investment, before they have collected, or once they bought this Investment; `403`, `404` and `401` as
	 * for the other actions.
	 */
	@PostMapping("/games/{id}/reputation/buy")
	fun buyReputation(@PathVariable id: String, session: Session): GameResponseDto =
		GameResponseDto(investmentService.buyReputation(id, session.email), session.email)

	/** Done is a readiness flag (R44); the last player's signal closes Investment for everyone and ends the turn (D09). */
	@PostMapping("/games/{id}/investment/done")
	fun signalDoneInvesting(@PathVariable id: String, session: Session): GameResponseDto {
		val game = investmentService.signalDone(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player with factories confirms where this turn's output goes (R47, E06-S03-5): what isn't placed or doesn't fit is
	 * lost. `400` for a placement the rules refuse or that isn't a whole number, `409` outside Investment or when they have
	 * already collected or have no factories, `403`, `404` and `401` as for the other actions.
	 */
	@PostMapping("/games/{id}/opportunity/collect")
	fun collectOutput(
		@PathVariable id: String,
		@RequestBody(required = false) request: CollectRequestDto?,
		session: Session
	): GameResponseDto {
		val placements = (request ?: CollectRequestDto()).outputPlacements()
			?: throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Choose a destination and a whole number of units, 0 or more, for each placement."
			)
		val game = opportunityService.collect(id, session.email, placements)

		return GameResponseDto(game, session.email)
	}

	/** The player pays 5 HT to learn the tip-off in board [slot] (R110). `400` without the connection or the money; `409` outside News. */
	@PostMapping("/games/{id}/news/{slot}/learn")
	fun learnTipOff(@PathVariable id: String, @PathVariable slot: Int, session: Session): GameResponseDto =
		GameResponseDto(newsService.learn(id, session.email, slot), session.email)

	/** The player takes the opportunity in [slot] (R111–R114) on their Opportunity turn; `400` when the rules refuse it. */
	@PostMapping("/games/{id}/opportunity/tip-offs/{slot}/take")
	fun takeOpportunity(
		@PathVariable id: String,
		@PathVariable slot: Int,
		@RequestBody(required = false) request: TakeOpportunityRequestDto?,
		session: Session
	): GameResponseDto = GameResponseDto(
		opportunityService.takeOpportunity(id, session.email, slot, (request ?: TakeOpportunityRequestDto()).order()),
		session.email
	)

	/** The player brings their expedition ship back to `{ system }`'s Port (R114). */
	@PostMapping("/games/{id}/opportunity/expeditions/return")
	fun returnExpedition(
		@PathVariable id: String,
		@RequestBody(required = false) request: SpecialSaleRequestDto?,
		session: Session
	): GameResponseDto {
		val body = request ?: SpecialSaleRequestDto()
		return GameResponseDto(opportunityService.returnExpedition(id, session.email, body.ship, body.system()), session.email)
	}

	/** The player boards `{ groups }` passenger groups onto `{ ship }`, bound for `{ system }` (R125); `400` when refused. */
	@PostMapping("/games/{id}/opportunity/passengers")
	fun takePassengers(
		@PathVariable id: String,
		@RequestBody(required = false) request: PassengerRequestDto?,
		session: Session
	): GameResponseDto {
		val body = request ?: PassengerRequestDto()
		return GameResponseDto(opportunityService.takePassengers(id, session.email, body.ship, body.system(), body.groups), session.email)
	}

	/** The player sells their illegal `{ goods }` on `{ system }`'s black market (R112). */
	@PostMapping("/games/{id}/opportunity/black-market")
	fun sellOnBlackMarket(
		@PathVariable id: String,
		@RequestBody(required = false) request: SpecialSaleRequestDto?,
		session: Session
	): GameResponseDto {
		val body = request ?: SpecialSaleRequestDto()
		return GameResponseDto(opportunityService.sellOnBlackMarket(id, session.email, body.system(), body.goods()), session.email)
	}

	/** The player completes the delivery aboard `{ ship }` in its destination Port (R114, R121); `400` elsewhere or with none. */
	@PostMapping("/games/{id}/opportunity/deliveries")
	fun deliver(@PathVariable id: String, @RequestBody(required = false) request: SpecialSaleRequestDto?, session: Session): GameResponseDto =
		GameResponseDto(opportunityService.deliver(id, session.email, (request ?: SpecialSaleRequestDto()).ship), session.email)

	/** The player sells their furs in `{ system }`'s Port (R112). */
	@PostMapping("/games/{id}/opportunity/furs")
	fun sellFurs(@PathVariable id: String, @RequestBody(required = false) request: SpecialSaleRequestDto?, session: Session): GameResponseDto =
		GameResponseDto(opportunityService.sellFurs(id, session.email, (request ?: SpecialSaleRequestDto()).system()), session.email)

	/** The player is done with the news (R109); once everyone is, Jumps starts. `409` outside News. */
	@PostMapping("/games/{id}/news/done")
	fun finishNews(@PathVariable id: String, session: Session): GameResponseDto =
		GameResponseDto(newsService.finish(id, session.email), session.email)

	/** The player on their Opportunity turn is done with opportunities (R102); `409` out of turn, before Collect, or with a choice open. */
	@PostMapping("/games/{id}/opportunity/done")
	fun finishOpportunities(@PathVariable id: String, session: Session): GameResponseDto =
		GameResponseDto(opportunityService.finish(id, session.email), session.email)

	/**
	 * The player sabotages another player's ship in Port, warehouse or factory on their Opportunity turn (R97): `{
	 * targetPlayerId, kind, system, ship, goods, blamedId }`. `400` for a target or blamed player the rules refuse or too
	 * little money; `409` out of turn, before Collect, or a second time this Opportunity.
	 */
	@PostMapping("/games/{id}/sabotage")
	fun sabotage(@PathVariable id: String, @RequestBody(required = false) request: SabotageRequestDto?, session: Session): GameResponseDto {
		val body = request ?: SabotageRequestDto()
		val ids = PlayerIds(gamePollingService.poll(id, session.email))
		val owner = ids.emailOf(body.targetPlayerId) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose another player's asset.")
		val target =
			body.target(owner) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a ship, warehouse or factory to sabotage.")
		val game = opportunityService.sabotage(id, session.email, target, ids.emailOf(body.blamedId))

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player challenges another player's market position on their Opportunity turn (R126): `{ targetPlayerId, system,
	 * goods, kind }`, `kind` `POLITICAL` or `BUSINESS`. `400` for a target, position or connection the rules refuse, a
	 * difference that can't win or too little money; `409` out of turn, before Collect, or a second time this Opportunity.
	 */
	@PostMapping("/games/{id}/challenge")
	fun challengeMarketPosition(
		@PathVariable id: String,
		@RequestBody(required = false) request: ChallengeRequestDto?,
		session: Session
	): GameResponseDto {
		val body = request ?: ChallengeRequestDto()
		val ids = PlayerIds(gamePollingService.poll(id, session.email))
		val system = body.starSystem() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a position to challenge.")
		val goods = body.tradedGoods() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a position to challenge.")
		val kind =
			body.connectionKind() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Challenge with political or business connections.")
		val game = opportunityService.challenge(id, session.email, ids.emailOf(body.targetPlayerId), system, goods, kind)

		return GameResponseDto(game, session.email)
	}

	/** The saboteur chooses the units missing from the warehouse they hit (R99): `{ lots: { GOODS: units } }`. */
	@PostMapping("/games/{id}/sabotage/losses")
	fun chooseSabotageLosses(
		@PathVariable id: String,
		@RequestBody(required = false) request: SabotageLossesRequestDto?,
		session: Session
	): GameResponseDto {
		val lots = (request ?: SabotageLossesRequestDto()).goodsLots()
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose whole numbers of units of the goods it holds.")
		return GameResponseDto(opportunityService.chooseLosses(id, session.email, lots), session.email)
	}

	/** The ship's owner places a sabotage's hits on it, out of turn (R99): one ship as in `/ship-jumps/battle/hits`. */
	@PostMapping("/games/{id}/sabotage/hits")
	fun placeSabotageHits(
		@PathVariable id: String,
		@RequestBody(required = false) request: ShipHitsRequestDto?,
		session: Session
	): GameResponseDto {
		val body = request ?: ShipHitsRequestDto()
		val ship = body.ship ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name the ship you place hits on.")
		return GameResponseDto(opportunityService.placeHits(id, session.email, ship, body.placement()), session.email)
	}

	/** The player pays to recruit an agent on their Opportunity turn (R103): `{ amount, ship }` or `{ amount, system, location }`. */
	@PostMapping("/games/{id}/agents/recruit")
	fun recruitAgent(@PathVariable id: String, @RequestBody(required = false) request: AgentRequestDto?, session: Session): GameResponseDto {
		val body = request ?: AgentRequestDto()
		val start = body.position() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose where the agent starts.")
		return GameResponseDto(opportunityService.recruit(id, session.email, body.wholeAmount(), start), session.email)
	}

	/**
	 * The player places the agent they recruited this turn where it starts (playtest, 2026-11-21): `{ agent, ship }` or
	 * `{ agent, system, location }`, anywhere a new agent may start.
	 */
	@PostMapping("/games/{id}/agents/place")
	fun placeNewAgent(@PathVariable id: String, @RequestBody(required = false) request: AgentRequestDto?, session: Session): GameResponseDto {
		val body = request ?: AgentRequestDto()
		val to = body.position() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose where the agent starts.")
		return GameResponseDto(opportunityService.placeNewAgent(id, session.email, body.agentNamed(), to), session.email)
	}

	/** The player moves one of their agents on their Opportunity turn (R104): `{ agent, ship }` or `{ agent, system, location }`. */
	@PostMapping("/games/{id}/agents/move")
	fun moveAgent(@PathVariable id: String, @RequestBody(required = false) request: AgentRequestDto?, session: Session): GameResponseDto {
		val body = request ?: AgentRequestDto()
		val to = body.position() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose where the agent goes.")
		return GameResponseDto(opportunityService.moveAgent(id, session.email, body.agentNamed(), to), session.email)
	}

	/** The player offers one of their agents to another player on their Opportunity turn (R106): `{ agent, buyerId, price }`. */
	@PostMapping("/games/{id}/agents/offer")
	fun offerAgent(
		@PathVariable id: String,
		@RequestBody(required = false) request: OfferAgentRequestDto?,
		session: Session
	): GameResponseDto {
		val body = request ?: OfferAgentRequestDto()
		val buyer = PlayerIds(gamePollingService.poll(id, session.email)).emailOf(body.buyerId)
		return GameResponseDto(opportunityService.offerAgent(id, session.email, body.agentNamed(), buyer, body.wholePrice()), session.email)
	}

	/** The buyer accepts an agent offer made to them (R106); `409` once it's gone. */
	@PostMapping("/games/{id}/agents/offers/{offer}/accept")
	fun acceptAgentOffer(@PathVariable id: String, @PathVariable offer: Int, session: Session): GameResponseDto =
		GameResponseDto(opportunityService.answerAgentOffer(id, session.email, offer, true), session.email)

	/** The buyer declines an agent offer made to them (R106). */
	@PostMapping("/games/{id}/agents/offers/{offer}/decline")
	fun declineAgentOffer(@PathVariable id: String, @PathVariable offer: Int, session: Session): GameResponseDto =
		GameResponseDto(opportunityService.answerAgentOffer(id, session.email, offer, false), session.email)

	/** The player borrows in Investment (R90): `{ amount, term }`, term 4 or 8. `400` above the cap or with a loan open. */
	@PostMapping("/games/{id}/loan")
	fun takeLoan(@PathVariable id: String, @RequestBody(required = false) request: TakeLoanRequestDto?, session: Session): GameResponseDto {
		val body = request ?: TakeLoanRequestDto()
		return GameResponseDto(investmentService.takeLoan(id, session.email, body.wholeAmount(), body.loanTerm()), session.email)
	}

	/** The player pays this turn's loan interest (R92). */
	@PostMapping("/games/{id}/loan/interest")
	fun payInterest(@PathVariable id: String, session: Session): GameResponseDto =
		GameResponseDto(investmentService.payInterest(id, session.email), session.email)

	/** The player repays their whole loan (R93). */
	@PostMapping("/games/{id}/loan/repay")
	fun repayLoan(@PathVariable id: String, session: Session): GameResponseDto =
		GameResponseDto(investmentService.repayLoan(id, session.email), session.email)

	/**
	 * The player sells blocks of their warehouse at a system in Investment (R141): `{ system, blocks, dropGoods, dropModules }`,
	 * the last two saying what they throw away so the rest fits (`{ "ISO": 3 }`, `{ "CARGO": 1 }`). Selling every block
	 * removes the warehouse with all it held. `400` for anything unreadable, or when what's left doesn't fit.
	 */
	@PostMapping("/games/{id}/warehouses/sell")
	fun sellWarehouseBlocks(
		@PathVariable id: String,
		@RequestBody(required = false) request: SellWarehouseRequestDto?,
		session: Session
	): GameResponseDto {
		val body = request ?: SellWarehouseRequestDto()
		val system = body.starSystem() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of your warehouses.")
		val blocks = body.wholeBlocks() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a whole number of blocks.")
		val goods = body.droppedGoods() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a whole number of units for each good.")
		val modules = body.droppedModules() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a whole number for each module.")
		return GameResponseDto(investmentService.sellWarehouseBlocks(id, session.email, system, blocks, goods, modules), session.email)
	}

	/** The player sells units of one of their factories in Investment (R96): `{ system, goods, units }`. */
	@PostMapping("/games/{id}/factories/sell")
	fun sellFactoryUnits(
		@PathVariable id: String,
		@RequestBody(required = false) request: SellFactoryRequestDto?,
		session: Session
	): GameResponseDto {
		val body = request ?: SellFactoryRequestDto()
		val system = body.starSystem() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of your factories.")
		val goods = body.goodsType() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of your factories.")
		val units = body.wholeUnits() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a whole number of units.")
		return GameResponseDto(investmentService.sellFactoryUnits(id, session.email, system, goods, units), session.email)
	}

	/**
	 * The player buys warehouse blocks at a system where they have a ship (R45, E06-S04): `{ system, blocks }`. `400` for an
	 * unknown system, blocks that aren't a whole number, none, no ship there or too little money; `409` outside Investment or
	 * before they have collected; `403`, `404` and `401` as for the other actions.
	 */
	@PostMapping("/games/{id}/investment/warehouse-blocks")
	fun buyWarehouseBlocks(
		@PathVariable id: String,
		@RequestBody(required = false) request: BuyBlocksRequestDto?,
		session: Session
	): GameResponseDto {
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
		@PathVariable id: String,
		@RequestBody(required = false) request: BuyUnitsRequestDto?,
		session: Session
	): GameResponseDto {
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
	 * The player buys a ship (R61, E09-S02): `{ name, shipyard, hull, modules, crewClass }`. `400` for an unknown shipyard,
	 * hull, module or crew class, and for a ship the rules refuse (a taken or bad name, no shipyard there, military parts,
	 * too many modules, too little money); `409` outside Investment or before they have collected; `403`, `404` and `401`
	 * as for the other actions.
	 */
	@PostMapping("/games/{id}/investment/ships")
	fun buyShip(@PathVariable id: String, @RequestBody(required = false) request: BuyShipRequestDto?, session: Session): GameResponseDto {
		val order = request ?: BuyShipRequestDto()
		val shipyard = order.shipyardSystem() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a shipyard.")
		val hull = order.hullConstant() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a hull.")
		val modules = order.moduleConstants() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose modules from the list.")
		val crewClass = order.crewClassConstant()
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a crew class: A, B, C or D.")
		val game = investmentService.buyShip(id, session.email, order.name, shipyard, hull, modules, crewClass)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The player fits modules to their ship at a shipyard (R65, E09-S04): `{ module }` for one, or `{ modules: [...] }` for
	 * several in one go, all or none (creator, 2026-11-26). `400` for an unknown or military
	 * module, a ship that isn't theirs or isn't at a shipyard, one the hull can't take, too little money; `409` outside
	 * Investment or before they have collected.
	 */
	@PostMapping("/games/{id}/investment/ships/{ship}/modules")
	fun addModule(
		@PathVariable id: String,
		@PathVariable ship: String,
		@RequestBody(required = false) request: FitModuleRequestDto?,
		session: Session
	): GameResponseDto {
		val modules = request?.moduleConstants() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a module.")
		return GameResponseDto(investmentService.addModules(id, session.email, ship, modules), session.email)
	}

	/**
	 * The player removes one module from their ship at a shipyard (R65, E09-S04), paid half its price back. `400` for an
	 * unknown module, one the ship hasn't got, a ship that isn't theirs or isn't at a shipyard, and a removal that would
	 * leave too little cargo space for its POLY and ISO; `409` outside Investment or before they have collected.
	 */
	@DeleteMapping("/games/{id}/investment/ships/{ship}/modules/{module}")
	fun removeModule(@PathVariable id: String, @PathVariable ship: String, @PathVariable module: String, session: Session): GameResponseDto {
		val fitted = moduleNamed(module) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a module.")
		return GameResponseDto(investmentService.removeModule(id, session.email, ship, fitted), session.email)
	}

	/**
	 * The player stores one legal module of their ship in Port in their warehouse there (R122), free. `400` for an unknown
	 * or military module, a ship out of port, no warehouse there or no room in it; `409` outside Investment.
	 */
	@PostMapping("/games/{id}/investment/ships/{ship}/modules/{module}/store")
	fun storeModule(@PathVariable id: String, @PathVariable ship: String, @PathVariable module: String, session: Session): GameResponseDto {
		val fitted = moduleNamed(module) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a module.")
		return GameResponseDto(investmentService.storeModule(id, session.email, ship, fitted), session.email)
	}

	/**
	 * The player fits a module stored in their warehouse to their ship in that Port (R122): `{ module }`, free. `400` for an
	 * unknown module, one not stored there, a ship out of port or without room; `409` outside Investment.
	 */
	@PostMapping("/games/{id}/investment/ships/{ship}/modules/stored")
	fun fitStoredModule(
		@PathVariable id: String,
		@PathVariable ship: String,
		@RequestBody(required = false) request: FitModuleRequestDto?,
		session: Session
	): GameResponseDto {
		val module = request?.moduleConstant() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a module.")
		return GameResponseDto(investmentService.fitStoredModule(id, session.email, ship, module), session.email)
	}

	/**
	 * The player moves one military module from their ship to another of theirs in the same Port, at legal system 1 or 2
	 * (R123): `{ ship }` names the receiving ship. Free. `400` when the rules refuse it; `409` outside Investment.
	 */
	@PostMapping("/games/{id}/investment/ships/{ship}/modules/{module}/move")
	fun moveMilitaryModule(
		@PathVariable id: String,
		@PathVariable ship: String,
		@PathVariable module: String,
		@RequestBody(required = false) request: SpecialSaleRequestDto?,
		session: Session
	): GameResponseDto {
		val fitted = moduleNamed(module) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a module.")
		return GameResponseDto(investmentService.moveMilitaryModule(id, session.email, ship, request?.ship, fitted), session.email)
	}

	/**
	 * The player replaces the whole crew of their ship with one of another class (R89), in port or in the yard: `{ crewClass }`.
	 * `400` for an unknown class, the class it already has, a ship that isn't theirs or isn't in port or the yard, and too
	 * little money; `409` outside Investment or before they have collected.
	 */
	@PutMapping("/games/{id}/investment/ships/{ship}/crew")
	fun replaceCrew(
		@PathVariable id: String,
		@PathVariable ship: String,
		@RequestBody(required = false) request: ReplaceCrewRequestDto?,
		session: Session
	): GameResponseDto {
		val crewClass = request?.crewClassConstant()
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a crew class: A, B, C or D.")
		return GameResponseDto(investmentService.replaceCrew(id, session.email, ship, crewClass), session.email)
	}

	/**
	 * The player repairs all the damage on their ship (R79, E10-S05) at a shipyard. `400` for a ship that isn't theirs,
	 * isn't damaged, isn't at a shipyard, or costs more than they have; `409` outside Investment or before they have collected.
	 */
	@PostMapping("/games/{id}/investment/ships/{ship}/repair")
	fun repairShip(@PathVariable id: String, @PathVariable ship: String, session: Session): GameResponseDto =
		GameResponseDto(investmentService.repairShip(id, session.email, ship), session.email)

	/**
	 * The player sells their ship (R66, E09-S05), wherever it is, for half its hull and modules. Its cargo goes into their
	 * warehouse at that system as far as it fits; the rest is lost. `400` for a ship that isn't theirs; `409` outside
	 * Investment or before they have collected.
	 */
	@DeleteMapping("/games/{id}/investment/ships/{ship}")
	fun sellShip(@PathVariable id: String, @PathVariable ship: String, session: Session): GameResponseDto =
		GameResponseDto(investmentService.sellShip(id, session.email, ship), session.email)

	/**
	 * The player moves goods between two of their holders in the same port (R58, E08-S02, revised 2026-11-17): `{ from,
	 * to, goods, units }`, each end a ship's name or left out for their warehouse there, in any phase of a running game.
	 * `400` for an unknown good, units that aren't a whole number, and a move the rules refuse (not their ship, not in
	 * port, ships in different ports, no warehouse there, more than the source holds or than fits); `409` before the game
	 * starts and once it's over; `403`, `404` and `401` as for the other actions.
	 */
	@PostMapping("/games/{id}/cargo/moves")
	fun moveCargo(@PathVariable id: String, @RequestBody(required = false) request: MoveCargoRequestDto?, session: Session): GameResponseDto {
		val move = request ?: MoveCargoRequestDto()
		val goods = move.goodsConstant()
		val units = move.wholeUnits()
		if (goods == null || units == null) {
			throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Choose where the goods come from and go, a good and a whole number of units, 1 or more."
			)
		}
		val game = cargoService.move(id, session.email, move.source(), move.destination(), goods, units)

		return GameResponseDto(game, session.email)
	}

	/** Done is a readiness flag (R17); the last player's signal closes Bidding for everyone (D09). */
	@PostMapping("/games/{id}/bidding/done")
	fun signalDoneBidding(@PathVariable id: String, session: Session): GameResponseDto {
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
		@PathVariable id: String,
		@RequestBody(required = false) request: PlaceOfferRequestDto?,
		session: Session
	): GameResponseDto {
		val system = request?.system() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a star system.")
		val goods = request.goods() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a good.")
		val type = request.type() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Buy or Sell.")
		val game = biddingService.placeOffer(id, session.email, system, goods, type, request.wholePrice())

		return GameResponseDto(game, session.email)
	}

	/**
	 * Withdraws the caller's offer on [goods] at [system] while Bidding is open (R59) and answers with their view of the
	 * game. Withdrawing an offer they don't have changes nothing; `409` "Bidding has closed" once it has (A04).
	 */
	@DeleteMapping("/games/{id}/offers/{system}/{goods}")
	fun withdrawOffer(@PathVariable id: String, @PathVariable system: String, @PathVariable goods: String, session: Session): GameResponseDto {
		val starSystem = StarSystem.entries.firstOrNull { it.name == system }
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a star system.")
		val good = Goods.entries.firstOrNull { it.name == goods }
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a good.")
		val game = biddingService.withdrawOffer(id, session.email, starSystem, good)

		return GameResponseDto(game, session.email)
	}

	/**
	 * Places or changes the caller's secret initiative bid (R27, S05-1, S05-2). Only the caller's own `you.bid` ever
	 * carries it (R29); it's paid when Bidding closes.
	 */
	@PutMapping("/games/{id}/bidding/bid")
	fun placeInitiativeBid(
		@PathVariable id: String,
		@RequestBody(required = false) request: PlaceBidRequestDto?,
		session: Session
	): GameResponseDto {
		val game = biddingService.placeInitiativeBid(id, session.email, request?.wholeAmount())

		return GameResponseDto(game, session.email)
	}

	/**
	 * The caller picks their place in this turn's turn order (R26), in initiative order (R27). `409` out of turn, once
	 * the order is set, or outside Ship-jumps; `400` for a place that isn't free.
	 */
	@PostMapping("/games/{id}/ship-jumps/turn-order")
	fun pickTurnOrderPlace(
		@PathVariable id: String,
		@RequestBody(required = false) request: PickPlaceRequestDto?,
		session: Session
	): GameResponseDto {
		val game = shipJumpsService.pickPlace(id, session.email, request?.wholePlace())

		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/ship-jumps/jumps")
	fun jumpShip(@PathVariable id: String, @RequestBody(required = false) request: JumpShipRequestDto?, session: Session): GameResponseDto {
		val game = shipJumpsService.jump(id, session.email, request?.ship, request?.destination())

		return GameResponseDto(game, session.email)
	}

	@PostMapping("/games/{id}/ship-jumps/landing")
	fun landShip(@PathVariable id: String, @RequestBody(required = false) request: LandShipRequestDto?, session: Session): GameResponseDto {
		val game = shipJumpsService.land(id, session.email, request?.location())

		return GameResponseDto(game, session.email)
	}

	/** The caller's notepad (R135): `{ text }`, empty when they never wrote one. `403` for anyone but a player of the game. */
	@GetMapping("/games/{id}/notepad")
	fun notepad(@PathVariable id: String, session: Session): NotepadDto = NotepadDto(notepadService.read(id, session.email))

	/** Saves the caller's notepad (R135): `{ text }`, at most 5,000 characters, replacing the last. `409` for more, or once eliminated. */
	@PutMapping("/games/{id}/notepad")
	fun saveNotepad(@PathVariable id: String, @RequestBody(required = false) request: NotepadDto?, session: Session): NotepadDto =
		NotepadDto(notepadService.write(id, session.email, request?.text))

	/**
	 * A player's answer to whether they back the corporation under reputation control (R129): `{ back }`, true to back it. Once
	 * nobody is left to ask, the second roll is made and Control goes on. `409` when nobody asks them or the answer is closed.
	 */
	@PostMapping("/games/{id}/control/backing")
	fun answerBacking(
		@PathVariable id: String,
		@RequestBody(required = false) request: BackingRequestDto?,
		session: Session
	): GameResponseDto = GameResponseDto(controlService.answerBacking(id, session.email, request?.back == true), session.email)

	/**
	 * The jumping player's answer to the Parking Hangar question (R127): `{ stay }`, true to park the ship asked about (or keep it
	 * parked) for 5 HT. `400` without the money; `409` when no question is open or it isn't their slot.
	 */
	@PostMapping("/games/{id}/ship-jumps/hangar")
	fun answerHangar(@PathVariable id: String, @RequestBody(required = false) request: HangarRequestDto?, session: Session): GameResponseDto =
		GameResponseDto(shipJumpsService.answerHangar(id, session.email, request?.stay == true), session.email)

	/**
	 * The player decides which one of their ships in Space tries to intercept the ship that just arrived (R70, E10-S02):
	 * `{ ship }`, null or absent for none. `409` when they aren't asked or have decided; `400` for a ship that isn't theirs in Space there.
	 */
	@PostMapping("/games/{id}/ship-jumps/interception")
	fun intercept(
		@PathVariable id: String,
		@RequestBody(required = false) request: InterceptionRequestDto?,
		session: Session
	): GameResponseDto = GameResponseDto(shipJumpsService.intercept(id, session.email, request?.ship), session.email)

	/**
	 * The player's orders for this battle round (R74, E10-S03): `{ orders: [{ ship, action, target, rockets }] }`. `400` for
	 * an order the rules refuse; `409` when they have no ship fighting or have given this round's orders.
	 */
	@PostMapping("/games/{id}/ship-jumps/battle/orders")
	fun giveBattleOrders(
		@PathVariable id: String,
		@RequestBody(required = false) request: BattleOrdersRequestDto?,
		session: Session
	): GameResponseDto = GameResponseDto(shipJumpsService.giveOrders(id, session.email, request?.battleOrders().orEmpty()), session.email)

	/**
	 * Where the player puts the hits their ships took this round (R76, E10-S03): `{ ships: [{ ship, hull, modules: [{ module,
	 * damaged, hits }], jettison }] }`. `400` for a placement the rules refuse; `409` when they have no hits to place.
	 */
	@PostMapping("/games/{id}/ship-jumps/battle/hits")
	fun placeBattleHits(
		@PathVariable id: String,
		@RequestBody(required = false) request: BattleHitsRequestDto?,
		session: Session
	): GameResponseDto = GameResponseDto(shipJumpsService.placeHits(id, session.email, request?.placements().orEmpty()), session.email)

	/**
	 * Where the player's surviving ships end up once the battle is over (R77, E10-S04): `{ ships: [{ ship, location }] }`.
	 * `400` for a ship missing or a location that isn't Port, Planet or Space; `409` before the battle is over or twice.
	 */
	@PostMapping("/games/{id}/ship-jumps/battle/landing")
	fun landAfterBattle(
		@PathVariable id: String,
		@RequestBody(required = false) request: BattleLandingRequestDto?,
		session: Session
	): GameResponseDto = GameResponseDto(shipJumpsService.landAfterBattle(id, session.email, request?.locations().orEmpty()), session.email)

	@PostMapping("/games/{id}/ship-jumps/finish")
	fun finishJumping(@PathVariable id: String, session: Session): GameResponseDto {
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
		@PathVariable id: String,
		@RequestBody(required = false) request: ReorderQueueRequestDto?,
		session: Session
	): GameResponseDto {
		val order = request?.order() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Send the remaining goods in their new order.")
		val game = tradingService.reorderQueue(id, session.email, order)

		return GameResponseDto(game, session.email)
	}

	/** The first player in initiative resolves the next good in the queue (R25, S01-4); `409` as for the queue. */
	@PostMapping("/games/{id}/trading/resolve-next")
	fun resolveNextGood(@PathVariable id: String, session: Session): GameResponseDto {
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
		@PathVariable id: String,
		@RequestBody(required = false) request: DictatorOfferRequestDto?,
		session: Session
	): GameResponseDto {
		val type = request?.type() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Buy or Sell.")
		val game = tradingService.placeDictatorOffer(id, session.email, type, request.wholePrice())

		return GameResponseDto(game, session.email)
	}

	/** The Dictator asked passes (R38, E05-S02-2); `409` as for the Dictator's offer. */
	@PostMapping("/games/{id}/trading/dictator-pass")
	fun passAsDictator(@PathVariable id: String, session: Session): GameResponseDto {
		val game = tradingService.passAsDictator(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The trader the counter reached chooses their amount and ships (E05-S04). `400` for an amount or split the rules
	 * refuse ("Enter a whole number from 1 to {max}."); `409` "{corporation} can buy up to 4 u — waiting for their
	 * decision." for anyone else, "No trade is waiting for a decision." when nobody is asked, or outside Trading.
	 */
	@PostMapping("/games/{id}/trading/trade")
	fun trade(@PathVariable id: String, @RequestBody(required = false) request: TradeRequestDto?, session: Session): GameResponseDto {
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
	fun wholesale(@PathVariable id: String, @RequestBody(required = false) request: WholesaleRequestDto?, session: Session): GameResponseDto {
		val wholesale = request ?: WholesaleRequestDto()
		val loads = wholesale.trade().loads()
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a whole number of units, 0 or more, for each ship.")
		val warehouseUnits = wholesale.trade().warehouseUnits()
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a whole number of units, 0 or more, for your warehouse.")
		val game = tradingService.wholesale(id, session.email, wholesale.type(), wholesale.trade().wholeAmount(), loads, warehouseUnits)

		return GameResponseDto(game, session.email)
	}

	/** The player asked about Limpy acts as Dictator of the next good, or passes (R108): `{ act }`. */
	@PostMapping("/games/{id}/trading/limpy")
	fun answerLimpy(@PathVariable id: String, @RequestBody(required = false) request: LimpyRequestDto?, session: Session): GameResponseDto =
		GameResponseDto(tradingService.answerLimpy(id, session.email, request?.act == true), session.email)

	/** The player the wholesale step asks passes (S05-4); `409`, `403`, `404` and `401` as for `/trading/wholesale`. */
	@PostMapping("/games/{id}/trading/wholesale-pass")
	fun passWholesale(@PathVariable id: String, session: Session): GameResponseDto {
		val game = tradingService.passWholesale(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/** The trader the counter reached trades nothing (E05-S04-5); `409`, `403`, `404` and `401` as for `/trading/trade`. */
	@PostMapping("/games/{id}/trading/trade-nothing")
	fun tradeNothing(@PathVariable id: String, session: Session): GameResponseDto {
		val game = tradingService.tradeNothing(id, session.email)

		return GameResponseDto(game, session.email)
	}

	/**
	 * The email of the player whose request to join game [gameId] the id [request] names ([JoinRequestIds]); empty when it
	 * names no waiting request, which the game then refuses after checking the caller is its creator.
	 */
	private fun requesterEmail(gameId: String, request: String): String {
		val game = gameStore.find(gameId) ?: throw GameNotFoundException()
		return JoinRequestIds(gameId).emailOf(request, game.joinRequests.map { it.email }).orEmpty()
	}
}
