package dev.adriangrzebyk.trader.trading.adapter.web

import dev.adriangrzebyk.trader.trading.DiceRoller
import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.InvalidTradeOfferException
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import dev.adriangrzebyk.trader.trading.TradeDesk
import dev.adriangrzebyk.trader.trading.TradeOfferBook
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api")
class TradeOfferController(tradeOfferBook: TradeOfferBook, diceRoller: DiceRoller) {

	private val tradeDesk = TradeDesk(tradeOfferBook, diceRoller)

	@GetMapping("/options")
	fun options(): TradeOfferOptions = TradeOfferOptions(
		starSystems = StarSystem.entries.map {
			StarSystemOption(name = it.name, displayName = it.displayName, goods = it.goods.sorted())
		},
		offerTypes = OfferType.entries,
		minPrice = TradeDesk.MIN_PRICE,
		maxPrice = TradeDesk.MAX_PRICE,
	)

	@GetMapping("/trade-offers")
	fun list(): List<TradeOfferView> = tradeDesk.allOffers().map(TradeOfferView::from)

	@PostMapping("/trade-offers")
	fun submit(@RequestBody request: TradeOfferRequest): ResponseEntity<TradeOfferView> {
		val playerName = request.playerName?.trim()
		if (playerName.isNullOrEmpty()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Player name is required")
		}
		val system = request.system
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Star system is required")
		val goods = request.goods
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Goods is required")
		val offerType = request.offerType
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Offer type is required")
		val price = request.price
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Price is required")

		val offer = try {
			tradeDesk.submit(playerName, system, goods, offerType, price, request.turn)
		} catch (ex: InvalidTradeOfferException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
		}

		return ResponseEntity.status(HttpStatus.CREATED).body(TradeOfferView.from(offer))
	}

	@GetMapping("/trade-offers/resolve")
	fun resolve(@RequestParam(defaultValue = "1") turn: Int, @RequestParam system: StarSystem, @RequestParam goods: Goods): TransactionView {
		val result = tradeDesk.resolve(turn, system, goods)
			?: throw ResponseStatusException(
				HttpStatus.NOT_FOUND,
				"No offers for ${goods.name} in ${system.displayName} on turn $turn",
			)

		return TransactionView(offer = TradeOfferView.from(requireNotNull(result.tradeOffer)), amount = result.amount)
	}

	@ExceptionHandler(ResponseStatusException::class)
	fun handleInvalidRequest(ex: ResponseStatusException): ResponseEntity<String> = ResponseEntity.status(ex.statusCode).body(ex.reason)
}
