package pl.adriangrzebyk.trader.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import pl.adriangrzebyk.trader.domain.OfferType
import pl.adriangrzebyk.trader.domain.Player
import pl.adriangrzebyk.trader.domain.StarSystem
import pl.adriangrzebyk.trader.domain.TradeOffer
import pl.adriangrzebyk.trader.service.TradeOfferBook
import pl.adriangrzebyk.trader.web.dto.StarSystemOption
import pl.adriangrzebyk.trader.web.dto.TradeOfferOptions
import pl.adriangrzebyk.trader.web.dto.TradeOfferRequest
import pl.adriangrzebyk.trader.web.dto.TradeOfferView

private const val MIN_PRICE = 1
private const val MAX_PRICE = 20

@RestController
@RequestMapping("/api")
class TradeOfferController(private val tradeOfferBook: TradeOfferBook) {

	@GetMapping("/options")
	fun options(): TradeOfferOptions = TradeOfferOptions(
		starSystems = StarSystem.entries.map {
			StarSystemOption(name = it.name, displayName = it.displayName, goods = it.goods.sorted())
		},
		offerTypes = OfferType.entries,
		minPrice = MIN_PRICE,
		maxPrice = MAX_PRICE,
	)

	@GetMapping("/trade-offers")
	fun list(): List<TradeOfferView> = tradeOfferBook.all().map(TradeOfferView::from)

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
		if (goods !in system.goods) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "${goods.name} is not traded in ${system.displayName}")
		}
		if (price !in MIN_PRICE..MAX_PRICE) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be between $MIN_PRICE and $MAX_PRICE")
		}

		val offer = TradeOffer(
			player = Player(playerName),
			system = system,
			goods = goods,
			offerType = offerType,
			price = price,
			initiative = tradeOfferBook.nextInitiative(),
		)
		tradeOfferBook.add(offer)

		return ResponseEntity.status(HttpStatus.CREATED).body(TradeOfferView.from(offer))
	}

	@ExceptionHandler(ResponseStatusException::class)
	fun handleInvalidRequest(ex: ResponseStatusException): ResponseEntity<String> =
		ResponseEntity.status(ex.statusCode).body(ex.reason)
}
