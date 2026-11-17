package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.game.Factory
import dev.adriangrzebyk.trader.game.FakeDice
import dev.adriangrzebyk.trader.game.FakeStartingSystemPicker
import dev.adriangrzebyk.trader.game.Game
import dev.adriangrzebyk.trader.game.GameStatus
import dev.adriangrzebyk.trader.game.Hull
import dev.adriangrzebyk.trader.game.MarketGood
import dev.adriangrzebyk.trader.game.MarketPosition
import dev.adriangrzebyk.trader.game.MarketTier
import dev.adriangrzebyk.trader.game.Module
import dev.adriangrzebyk.trader.game.Offer
import dev.adriangrzebyk.trader.game.Player
import dev.adriangrzebyk.trader.game.PositionRoll
import dev.adriangrzebyk.trader.game.Resolution
import dev.adriangrzebyk.trader.game.Trade
import dev.adriangrzebyk.trader.game.Warehouse
import dev.adriangrzebyk.trader.game.Wholesale
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GameResponseDtoTest {

	private val started = Game.lobby("game-1", 20, Player.newcomer("host@example.com", "Ada", "Orion Freight"))
		.addPlayer(Player.newcomer("guest@example.com", "Grace", "Vega Lines"))
		.start(
			"host@example.com",
			FakeStartingSystemPicker(
				StarSystem.TAU_CETI to StarSystem.BETA_HYDRI,
				StarSystem.GAMMA_LEPORIS to StarSystem.TAU_CETI
			)
		)

	@Test
	fun shouldSendEverySystemWithItsLocationsAndItsMarketInGoodsOrder() {
		val tauCeti = GameResponseDto(started, "host@example.com").board.single { it.id == "TAU_CETI" }

		assertThat(tauCeti).isEqualTo(
			SystemResponseDto(
				id = "TAU_CETI",
				name = "Tau Ceti",
				portClass = 4,
				legalSystem = 2,
				police = 3,
				security = 4,
				market = listOf(MarketEntryResponseDto("ISO", 4, -9), MarketEntryResponseDto("POLY", 6, -10)),
				producibleGoods = listOf("ISO")
			)
		)
	}

	@Test
	fun shouldSendTheGoodsEachSystemCanProduceAndNoneWhereNoFactoryCanStand() {
		val board = GameResponseDto(started, "host@example.com").board.associate { it.id to it.producibleGoods }

		assertThat(board).containsEntry("SIGMA_DRACONIS", listOf("FOOD", "ISO"))
			.containsEntry("GAMMA_LEPORIS", listOf("POLY"))
			.containsEntry("MU_HERCULIS", listOf("CHIPS"))
			.containsEntry("BETA_HYDRI", emptyList())
			.containsEntry("EPSILON_ERIDANI", emptyList())
	}

	@Test
	fun shouldSendNobodyAnyWarehouseOrFactoryInANewGame() {
		val players = GameResponseDto(started, "host@example.com").players

		assertThat(players).allMatch { it.warehouses.isEmpty() && it.factories.isEmpty() }
	}

	@Test
	fun shouldSendEachPlayersWarehousesAndFactoriesToEveryoneButNoMoney() {
		val game = started.copy(
			players = started.players.map {
				if (it.email == "guest@example.com") {
					it.copy(
						warehouses = listOf(Warehouse(StarSystem.TAU_CETI, 2, emptyMap())),
						factories = listOf(Factory(StarSystem.SIGMA_DRACONIS, Goods.FOOD, 3))
					)
				} else {
					it
				}
			}
		)

		val response = GameResponseDto(game, "host@example.com")
		val guest = response.players.single { it.id == "p2" }

		assertThat(guest.warehouses).containsExactly(WarehouseResponseDto("TAU_CETI", 2, 20))
		assertThat(guest.factories).containsExactly(FactoryResponseDto("SIGMA_DRACONIS", "FOOD", 3))
		assertThat(response.you?.money).isEqualTo(300)
		assertThat(guest.toString()).doesNotContain("money")
	}

	/** [started] at the start of Investment, where the host owns an ISO factory and a warehouse already holding 3 ISO at Tau Ceti, where their ship is. */
	private val investing = (1..3).fold(started) { game, _ -> game.advancePhase(FakeDice(2, 1)) }.let { game ->
		game.copy(
			players = game.players.map {
				if (it.email == "host@example.com") {
					it.copy(
						factories = listOf(Factory(StarSystem.TAU_CETI, Goods.ISO, 6)),
						warehouses = listOf(Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.ISO to 3)))
					)
				} else {
					it
				}
			}
		)
	}

	@Test
	fun shouldOfferTheCollectWithEveryDestinationAndItsFreeSpaceToTheFactoryOwner() {
		// E06-S03-1, S03-2: the warehouse first (a null ship), then each ship in port there; free space for ISO, null for CHIPS and FOOD
		val collect = GameResponseDto(investing, "host@example.com").you!!.collect!!

		assertThat(collect.factories).containsExactly(
			CollectFactoryResponseDto(
				"TAU_CETI",
				"Tau Ceti",
				"ISO",
				6,
				listOf(CollectDestinationResponseDto(null, 7), CollectDestinationResponseDto("Wayfarer", 8))
			)
		)
	}

	@Test
	fun shouldOfferWhatCanBeBoughtOnlyOnceTheViewerHasCollectedAndInEverySystem() {
		// E06-S04-1, S04-2, S03-5, creator 2026-11-16: before collecting there is nothing to buy; then all six systems, ships or not
		assertThat(GameResponseDto(investing, "host@example.com").you!!.invest).isNull()

		val collected = investing.collectOutput("host@example.com", emptyList())
		val invest = GameResponseDto(collected, "host@example.com").you!!.invest!!

		assertThat(invest.blockCost).isEqualTo(20)
		assertThat(invest.systems.map { it.system }).containsExactlyInAnyOrderElementsOf(StarSystem.entries.map { it.name })
		assertThat(invest.systems).contains(InvestSystemResponseDto("TAU_CETI", "Tau Ceti", 1))
		assertThat(invest.systems.single { it.system == "BETA_HYDRI" }.blocks).isZero()
	}

	@Test
	fun shouldOfferEveryGoodAnySystemCanProduceWhateverTheViewerHasThere() {
		// E06-S05-1, S05-5: Gamma POLY; Tau ISO; Mu CHIPS; Sigma FOOD and ISO (map order); nothing at Beta or Epsilon; no ship or factory needed
		val collected = investing.collectOutput("host@example.com", emptyList())
		val invest = GameResponseDto(collected, "host@example.com").you!!.invest!!
		val price = collected.prices.priceOf(MarketGood(StarSystem.TAU_CETI, Goods.ISO))

		assertThat(invest.factories.map { it.system to it.goods }).containsExactly(
			"GAMMA_LEPORIS" to "POLY",
			"TAU_CETI" to "ISO",
			"MU_HERCULIS" to "CHIPS",
			"SIGMA_DRACONIS" to "FOOD",
			"SIGMA_DRACONIS" to "ISO"
		)
		assertThat(invest.factories).contains(InvestFactoryResponseDto("TAU_CETI", "Tau Ceti", "ISO", 6, price, 5 * price + 10))
		assertThat(invest.factories.single { it.system == "SIGMA_DRACONIS" && it.goods == "FOOD" }.size).isZero()
	}

	@Test
	fun shouldCostWhatTheTradingPriceNowIs() {
		// S05-2: the price is the game's current one, which Trading moved
		val moved = investing.copy(prices = investing.prices.withPrice(MarketGood(StarSystem.TAU_CETI, Goods.ISO), 20))
		val collected = moved.collectOutput("host@example.com", emptyList())

		val iso = GameResponseDto(collected, "host@example.com").you!!.invest!!.factories.single { it.system == "TAU_CETI" && it.goods == "ISO" }

		assertThat(iso.price).isEqualTo(20)
		assertThat(iso.unitCost).isEqualTo(110)
	}

	@Test
	fun shouldOfferABuyToAPlayerWithNoFactoriesAtOnceAndNobodyOutsideInvestment() {
		assertThat(GameResponseDto(investing, "guest@example.com").you!!.invest).isNotNull()
		assertThat(GameResponseDto(started, "host@example.com").you!!.invest).isNull()
	}

	@Test
	fun shouldOfferNoCollectToAPlayerWithoutFactoriesOnceTheyCollectedOrOutsideInvestment() {
		assertThat(GameResponseDto(investing, "guest@example.com").you!!.collect).isNull()
		assertThat(GameResponseDto(investing.collectOutput("host@example.com", emptyList()), "host@example.com").you!!.collect).isNull()
		assertThat(GameResponseDto(started, "host@example.com").you!!.collect).isNull()
	}

	@Test
	fun shouldSendAWarehouseCargoOnlyToItsOwnerAndShowEveryoneWhoCollected() {
		// R29, A05: players[] carries blocks and cargo spaces, never what a warehouse holds; turn.collected is public
		val collected = investing.collectOutput("host@example.com", emptyList())
		val seenByGuest = GameResponseDto(collected, "guest@example.com")
		val seenByHost = GameResponseDto(collected, "host@example.com")

		assertThat(seenByHost.you!!.warehouses).containsExactly(
			YourWarehouseResponseDto("TAU_CETI", "Tau Ceti", 1, 10, mapOf("ISO" to 3), 3)
		)
		assertThat(seenByGuest.you!!.warehouses).isEmpty()
		assertThat(
			seenByGuest.players.single {
				it.id == "p1"
			}.warehouses
		).containsExactly(WarehouseResponseDto("TAU_CETI", 1, 10))
		assertThat(seenByGuest.turn!!.collected).containsExactly("p1")
	}

	@Test
	fun shouldSendEachPlayersMarketPositionsToEveryone() {
		val position = MarketPosition(StarSystem.MU_HERCULIS, Goods.ISO, MarketTier.DICTATOR)
		val game = started.copy(
			players = started.players.map {
				if (it.email ==
					"guest@example.com"
				) {
					it.copy(marketPositions = listOf(position))
				} else {
					it
				}
			}
		)

		val guest = GameResponseDto(game, "host@example.com").players.single { it.id == "p2" }

		assertThat(guest.marketPositions).containsExactly(MarketPositionResponseDto("MU_HERCULIS", "ISO", "DICTATOR"))
	}

	@Test
	fun shouldSendAPositionWonThisTurnToEveryone() {
		// E05-S06-4, S06-5: public at once, like the roll that won it
		val game = started.copy(
			players = started.players.map {
				if (it.email ==
					"guest@example.com"
				) {
					it.promotedAt(MarketGood(StarSystem.MU_HERCULIS, Goods.ISO))
				} else {
					it
				}
			}
		)

		val guest = GameResponseDto(game, "host@example.com").players.single { it.id == "p2" }

		assertThat(guest.marketPositions).containsExactly(MarketPositionResponseDto("MU_HERCULIS", "ISO", "RETAILER"))
	}

	@Test
	fun shouldGiveTheViewerTheirOwnShipsCargoAndCargoSpaceUsed() {
		val host = started.players.first()
		val loaded = host.copy(
			ships = listOf(host.ships[0].copy(cargo = mapOf(Goods.POLY to 4, Goods.ISO to 3, Goods.CHIPS to 12, Goods.FOOD to 0)))
		)
		val game = started.copy(players = listOf(loaded) + started.players.drop(1))

		val you = GameResponseDto(game, "host@example.com").you!!

		val wayfarer = you.ships.single()
		assertThat(wayfarer.name).isEqualTo("Wayfarer")
		assertThat(wayfarer.cargo).isEqualTo(mapOf("CHIPS" to 12, "ISO" to 3, "POLY" to 4))
		assertThat(wayfarer.cargoUsed).isEqualTo(7)
		assertThat(wayfarer.cargoSpaces).isEqualTo(8)
	}

	@Test
	fun shouldGiveTheViewerTheirOwnShipsHullModulesAndStats() {
		// E07-S03-2, R55: a starting ship is a Clarinet with 4 Cargo, 1 Light weapons and 1 Safe jump module
		val wayfarer = GameResponseDto(started, "host@example.com").you!!.ships.first()

		assertThat(wayfarer.hull).isEqualTo(HullResponseDto("Clarinet", military = false, aerodynamic = false))
		assertThat(wayfarer.modules).containsExactly(
			FittedModuleResponseDto(
				"CARGO", "Cargo", 4,
				weapons = 0, rocketWeapons = 0, crew = 0, cargoSpaces = 2, passengers = 0, military = false,
				resaleValue = 2
			),
			FittedModuleResponseDto(
				"LIGHT_WEAPONS", "Light weapons", 1,
				weapons = 3, rocketWeapons = 5, crew = 0, cargoSpaces = 0, passengers = 0, military = false,
				resaleValue = 8
			),
			FittedModuleResponseDto(
				"SAFE_JUMP", "Safe jump", 1,
				weapons = 0, rocketWeapons = 0, crew = 0, cargoSpaces = 0, passengers = 0, military = false,
				resaleValue = 5
			)
		)
		assertThat(wayfarer.stats).isEqualTo(
			ShipStatsResponseDto(
				weapons = 6,
				rocketWeapons = 5,
				shields = 2,
				intercept = 2,
				crewNeeded = 1,
				passengers = 0,
				modulesUsed = 5,
				moduleCapacity = 5
			)
		)
	}

	@Test
	fun shouldFlagAMilitaryAndAerodynamicHullAndAMilitaryModule() {
		// E07-S03-4, R56: nothing in the game gives a player one yet, but the flags are sent
		val host = started.players.first()
		val dagger = host.ships[0].copy(hull = Hull.DAGGER, modules = listOf(Module.ARSENAL))
		val game = started.copy(players = listOf(host.copy(ships = listOf(dagger))) + started.players.drop(1))

		val ship = GameResponseDto(game, "host@example.com").you!!.ships.single()

		assertThat(ship.hull).isEqualTo(HullResponseDto("Dagger", military = true, aerodynamic = true))
		assertThat(ship.modules.single().military).isTrue()
		assertThat(ship.cargoSpaces).isZero()
	}

	@Test
	fun shouldSendNobodyElsesHullModulesOrStats() {
		// E07-S03-6, R57: other players' ships are a name and a position, nothing more
		val guestShips = GameResponseDto(started, "host@example.com").players.first { it.id == "p2" }.ships

		assertThat(guestShips).containsExactly(
			ShipResponseDto("Meridian", "GAMMA_LEPORIS", "Gamma Leporis", "PORT"),
			ShipResponseDto("Halcyon", "TAU_CETI", "Tau Ceti", "PORT")
		)
	}

	@Test
	fun shouldGiveTheViewerTheirOwnWarehousesCargoAndNobodyElses() {
		// E06-S06, S02-7: a warehouse is public, what it holds is its owner's (R49)
		val game = started.copy(
			players = started.players.map {
				if (it.email == "guest@example.com") {
					it.copy(warehouses = listOf(Warehouse(StarSystem.TAU_CETI, 1, mapOf(Goods.ISO to 90, Goods.POLY to 4, Goods.CHIPS to 0))))
				} else {
					it.copy(warehouses = listOf(Warehouse(StarSystem.MU_HERCULIS, 2, mapOf(Goods.POLY to 7, Goods.CHIPS to 12))))
				}
			}
		)

		val seenByHost = GameResponseDto(game, "host@example.com")

		assertThat(seenByHost.you!!.warehouses).containsExactly(
			YourWarehouseResponseDto(
				"MU_HERCULIS",
				"Mu Herculis",
				2,
				20,
				mapOf(
					"CHIPS" to 12,
					"POLY" to 7
				),
				7
			)
		)
		assertThat(seenByHost.players.single { it.id == "p2" }.warehouses)
			.containsExactly(WarehouseResponseDto("TAU_CETI", 1, 10))
		assertThat(seenByHost.players.map { it.warehouses }.toString()).doesNotContain("90").doesNotContain("ISO").doesNotContain("POLY")
	}

	@Test
	fun shouldSendTheViewerTheirOwnOffersOnly() {
		val game = started
			.placeOffer("guest@example.com", StarSystem.TAU_CETI, Goods.POLY, OfferType.SELL, 19)
			.placeOffer("host@example.com", StarSystem.BETA_HYDRI, Goods.CHIPS, OfferType.BUY, 3)

		val seenByHost = GameResponseDto(game, "host@example.com")

		assertThat(seenByHost.you!!.offers).containsExactly(YourOfferResponseDto("BETA_HYDRI", "Beta Hydri", "CHIPS", "BUY", 3))
		assertThat(seenByHost.toString()).doesNotContain("SELL").doesNotContain("price=19")
	}

	@Test
	fun shouldSendTheViewerTheirOwnBidOnlyAndNobodyElsesMoney() {
		val game = started.placeInitiativeBid("guest@example.com", 137).placeInitiativeBid("host@example.com", 5)

		val seenByHost = GameResponseDto(game, "host@example.com")

		assertThat(seenByHost.you!!.bid).isEqualTo(5)
		assertThat(seenByHost.toString()).doesNotContain("137")
	}

	@Test
	fun shouldSendTheInitiativeOrderToEveryoneOnceBiddingCloses() {
		val closed = started.advancePhase(FakeDice(2, 1))

		assertThat(GameResponseDto(closed, "guest@example.com").turn!!.initiative).containsExactly("p1", "p2")
		assertThat(GameResponseDto(started, "guest@example.com").turn!!.initiative).isEmpty()
	}

	@Test
	fun shouldSendNoOffersBeforeTheGameStarts() {
		val lobby = Game.lobby("game-2", 20, Player.newcomer("host@example.com", "Ada", "Orion Freight"))

		assertThat(GameResponseDto(lobby, "host@example.com").you!!.offers).isEmpty()
	}

	@Test
	fun shouldSendTheCounterAndEachTradeOfTheResolutionToEveryone() {
		// E05-S03, E05-S04-6: public, like the disclosed offers: what the market allowed and what took place
		val vega = Offer("vega@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 11)
		val orion = Offer("orion@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 10)
		val kessler = Offer("kessler@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.SELL, 9)
		val resolution = Resolution(
			MarketGood(StarSystem.MU_HERCULIS, Goods.ISO),
			11,
			listOf(vega, orion),
			null,
			false,
			3,
			listOf(
				Trade(vega, 4, 4, null),
				Trade(orion, 5, 0, null),
				Trade(kessler, 8, 7, PositionRoll(5, MarketTier.DICTATOR, listOf("vega@example.com")))
			),
			emptyList(),
			emptyList(),
			emptyList()
		)

		// Vega, Orion and Kessler joined in that order, so they go by p1, p2 and p3: no email is sent
		val dto = ResolutionResponseDto(resolution, PlayerIds(listOf("vega@example.com", "orion@example.com", "kessler@example.com")))

		assertThat(dto.startingCounter).isEqualTo(3)
		assertThat(dto.trades).containsExactly(
			TradeResponseDto("p1", "BUY", 11, 4, 4, null),
			TradeResponseDto("p2", "SELL", 10, 5, 0, null),
			// E05-S06-4: the position roll is public too
			TradeResponseDto("p3", "SELL", 9, 8, 7, PositionRollResponseDto(5, "DICTATOR", listOf("p1")))
		)
		assertThat(dto.awaitingTrade).isNull()
	}

	// E05-S04: Grace (guest) buys ISO at 11 at Mu Herculis; the counter is at 3, so up to 4 u, and the game waits for her.
	private val gracesBuy = Offer("guest@example.com", StarSystem.MU_HERCULIS, Goods.ISO, OfferType.BUY, 11)

	/** Ada (host) is p1 and Grace (guest) p2, as in [started]; Kim, who the wholesale step also asks, would be p3. */
	private val ids = PlayerIds(listOf("host@example.com", "guest@example.com", "kim@example.com"))
	private val awaitingGrace =
		Resolution(
			MarketGood(
				StarSystem.MU_HERCULIS,
				Goods.ISO
			),
			11, listOf(gracesBuy), null, false, 3, emptyList(), emptyList(), emptyList(), emptyList()
		)

	@Test
	fun shouldTellEveryoneWhoTheGameWaitsForAndWhatTheMarketAllows() {
		// S01-8, D09
		assertThat(ResolutionResponseDto(awaitingGrace, ids).awaitingTrade).isEqualTo(AwaitingTradeResponseDto("p2", "BUY", 11, 4))
	}

	@Test
	fun shouldSendTheTradersOwnMaxOnlyToThem() {
		// S04-1, S04-2, R29: Grace has 30 HT, so 2 u at 11 HT; her max depends on her money, so nobody else sees it. With
		// 5 HT it's 0, and she acknowledges trading nothing (S04-5).
		val withMoney = { money: Int ->
			started.copy(
				players = started.players.map { if (it.email == "guest@example.com") it.pay(300 - money) else it },
				turn = started.turn!!.copy(resolution = awaitingGrace)
			)
		}

		assertThat(GameResponseDto(withMoney(30), "guest@example.com").you!!.tradeMax).isEqualTo(2)
		assertThat(GameResponseDto(withMoney(5), "guest@example.com").you!!.tradeMax).isZero()
		assertThat(GameResponseDto(withMoney(30), "host@example.com").you!!.tradeMax).isNull()
		assertThat(GameResponseDto(started, "guest@example.com").you!!.tradeMax).isNull()
	}

	@Test
	fun shouldSendEachSystemsMarketAtThisGamesPrices() {
		// E05-S05-3, R35: the board shows the price Trading moved; the S/D indicator never changes
		val moved = started.copy(prices = started.prices.withPrice(MarketGood(StarSystem.TAU_CETI, Goods.POLY), 7))

		val tauCeti = GameResponseDto(moved, "guest@example.com").board.single { it.id == "TAU_CETI" }

		assertThat(tauCeti.market).containsExactly(MarketEntryResponseDto("ISO", 4, -9), MarketEntryResponseDto("POLY", 7, -10))
	}

	// E05-S05: Grace's buy traded 4 at Mu Herculis ISO (11 HT), counter 3 → -1, so the price is 11 + modifier(-1) = 11;
	// Ada, Wholesaler there with no offer, sold 2, and Kim, the Dictator, is asked.
	private val wholesaleStep = Resolution(
		MarketGood(StarSystem.MU_HERCULIS, Goods.ISO),
		11,
		listOf(gracesBuy),
		"kim@example.com",
		true,
		3,
		listOf(Trade(gracesBuy, 4, 4, null)),
		emptyList(),
		listOf("host@example.com", "kim@example.com"),
		listOf(Wholesale("host@example.com", OfferType.SELL, 2, 11))
	)

	@Test
	fun shouldSendThePriceChangeAndTheWholesaleStepToEveryone() {
		// S05-1, S05-2, S05-4, S05-5: public, like the trades
		val dto = ResolutionResponseDto(wholesaleStep, ids)

		assertThat(dto.startPrice).isEqualTo(11)
		assertThat(dto.newPrice).isEqualTo(11)
		assertThat(dto.wholesalers).containsExactly("p1", "p3")
		assertThat(dto.awaitingWholesaler).isEqualTo("p3")
		assertThat(dto.wholesales).containsExactly(WholesaleResponseDto("p1", "Wholesaler", "SELL", 2, 11))
		assertThat(dto.finished).isFalse()
		assertThat(ResolutionResponseDto(awaitingGrace, ids).newPrice).isNull()
	}

	@Test
	fun shouldSendTheWholesalersOwnMaxOnlyToThem() {
		// R37, R12, R10, R29: here the guest passed and the host is asked; with 300 HT at 11 HT and no ISO in port
		// they may buy 5 and sell none. Nobody else gets it.
		val askingHost = wholesaleStep.copy(
			dictator = null, // Kim isn't in this two-player game
			wholesalers = listOf("guest@example.com", "host@example.com"),
			wholesales = listOf(Wholesale("guest@example.com", null, 0, 11))
		)
		val game = started.copy(turn = started.turn!!.copy(resolution = askingHost))

		assertThat(GameResponseDto(game, "host@example.com").you!!.wholesaleMax).isEqualTo(WholesaleMaxResponseDto(5, 0))
		assertThat(GameResponseDto(game, "guest@example.com").you!!.wholesaleMax).isNull()
		assertThat(GameResponseDto(started, "host@example.com").you!!.wholesaleMax).isNull()
	}

	@Test
	fun shouldSendEveryonesFinalMoneyAndTheWinnersOnlyOnceTheGameIsOver() {
		// E05-S08-2, S08-3: final money for everyone, most first; R29 until then
		val endMoney = started.copy(players = started.players.map { if (it.email == "guest@example.com") it.receive(2010) else it })
		val finished = endMoney.copy(status = GameStatus.FINISHED)

		assertThat(GameResponseDto(endMoney, "host@example.com").result).isNull()
		listOf("host@example.com", "guest@example.com").forEach { viewer ->
			assertThat(GameResponseDto(finished, viewer).result).isEqualTo(
				GameResultResponseDto(
					listOf(
						StandingResponseDto("p2", "Vega Lines", 2310),
						StandingResponseDto("p1", "Orion Freight", 300)
					),
					listOf("p2")
				)
			)
		}
	}

	@Test
	fun shouldNameEveryPlayerTiedOnMoneyAsAWinner() {
		// S08-4 (proposed): both still have their 300 HT
		val finished = started.copy(status = GameStatus.FINISHED)

		assertThat(GameResponseDto(finished, "guest@example.com").result!!.winners).containsExactly("p1", "p2")
	}
}
