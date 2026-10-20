package dev.adriangrzebyk.trader.trading.adapter.web

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeOfferControllerTest {

	@LocalServerPort
	private var port: Int = 0

	private lateinit var restTestClient: RestTestClient

	@BeforeEach
	fun setUp() {
		restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
	}

	@Test
	fun shouldExposeOptionsForBuildingTheForm() {
		val options = restTestClient.get().uri("/api/options")
			.exchange()
			.expectStatus().isOk
			.expectBody(TradeOfferOptions::class.java)
			.returnResult()
			.responseBody!!

		assertThat(options.minPrice).isEqualTo(1)
		assertThat(options.maxPrice).isEqualTo(20)
		assertThat(options.offerTypes).containsExactlyInAnyOrder(OfferType.BUY, OfferType.SELL)
		assertThat(options.starSystems.map { it.name }).contains(StarSystem.TAU_CETI.name)
		val tauCeti = options.starSystems.first { it.name == StarSystem.TAU_CETI.name }
		assertThat(tauCeti.goods).containsExactlyInAnyOrder(Goods.POLY, Goods.ISO)
	}

	@Test
	fun shouldExposeLiveMarketDataForEverySystemGood() {
		val options = restTestClient.get().uri("/api/options")
			.exchange()
			.expectStatus().isOk
			.expectBody(TradeOfferOptions::class.java)
			.returnResult()
			.responseBody!!

		val epsilonEridani = options.starSystems.first { it.name == StarSystem.EPSILON_ERIDANI.name }
		val expectedMarket = StarSystem.EPSILON_ERIDANI.goods.map {
			GoodsMarket(goods = it, price = StarSystem.EPSILON_ERIDANI.getPrice(it), indicator = StarSystem.EPSILON_ERIDANI.getIndicator(it))
		}
		assertThat(epsilonEridani.market).containsExactlyInAnyOrderElementsOf(expectedMarket)
	}

	@Test
	fun shouldAcceptAValidTradeOffer() {
		val request = TradeOfferRequest(
			playerName = "Adrian",
			system = StarSystem.TAU_CETI,
			goods = Goods.POLY,
			offerType = OfferType.BUY,
			price = 10,
		)

		val view = restTestClient.post().uri("/api/trade-offers")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.exchange()
			.expectStatus().isCreated
			.expectBody(TradeOfferView::class.java)
			.returnResult()
			.responseBody!!

		assertThat(view.playerName).isEqualTo("Adrian")
		assertThat(view.system).isEqualTo(StarSystem.TAU_CETI)
		assertThat(view.goods).isEqualTo(Goods.POLY)
		assertThat(view.offerType).isEqualTo(OfferType.BUY)
		assertThat(view.price).isEqualTo(10)

		val offers = restTestClient.get().uri("/api/trade-offers")
			.exchange()
			.expectStatus().isOk
			.expectBody(object : ParameterizedTypeReference<List<TradeOfferView>>() {})
			.returnResult()
			.responseBody!!

		assertThat(offers).contains(view)
	}

	@Test
	fun shouldAcceptATradeOfferSubmittedWithoutATurnLikeTheBrowserFormDoes() {
		val rawRequest = """
			{"playerName":"Adrian","system":"TAU_CETI","goods":"POLY","offerType":"BUY","price":10}
		""".trimIndent()

		restTestClient.post().uri("/api/trade-offers")
			.contentType(MediaType.APPLICATION_JSON)
			.body(rawRequest)
			.exchange()
			.expectStatus().isCreated
			.expectBody(TradeOfferView::class.java)
			.value { assertThat(it!!.turn).isEqualTo(1) }
	}

	@Test
	fun shouldRejectGoodsNotTradedInTheChosenSystem() {
		val request = TradeOfferRequest(
			playerName = "Adrian",
			system = StarSystem.GAMMA_LEPORIS,
			goods = Goods.CHIPS,
			offerType = OfferType.SELL,
			price = 5,
		)

		restTestClient.post().uri("/api/trade-offers")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.exchange()
			.expectStatus().isBadRequest
	}

	@Test
	fun shouldRejectPriceOutsideOneToTwenty() {
		val request = TradeOfferRequest(
			playerName = "Adrian",
			system = StarSystem.TAU_CETI,
			goods = Goods.POLY,
			offerType = OfferType.SELL,
			price = 21,
		)

		restTestClient.post().uri("/api/trade-offers")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.exchange()
			.expectStatus().isBadRequest
	}
}
