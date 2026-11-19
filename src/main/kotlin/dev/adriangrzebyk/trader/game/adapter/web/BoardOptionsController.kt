package dev.adriangrzebyk.trader.game.adapter.web

import dev.adriangrzebyk.trader.board.OfferType
import dev.adriangrzebyk.trader.board.StarSystem
import dev.adriangrzebyk.trader.game.Offer
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The board's fixed market data for the home Market Overview and the design-system page. Offers are placed and
 * read per game under `/api/games/{id}/offers` (E03-S03); the old unscoped `/api/trade-offers` is gone (R17, A05).
 */
@RestController
@RequestMapping("/api")
class BoardOptionsController {

	@GetMapping("/options")
	fun options(): TradeOfferOptions = TradeOfferOptions(
		starSystems = StarSystem.entries.map { system ->
			val goods = system.goods.sorted()
			StarSystemOption(
				name = system.name,
				displayName = system.displayName,
				goods = goods,
				market = goods.map { GoodsMarket(goods = it, price = system.getPrice(it), indicator = system.getIndicator(it)) }
			)
		},
		offerTypes = OfferType.entries,
		minPrice = Offer.MIN_PRICE,
		maxPrice = Offer.MAX_PRICE
	)
}
