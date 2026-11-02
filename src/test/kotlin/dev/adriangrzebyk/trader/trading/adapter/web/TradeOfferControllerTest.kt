package dev.adriangrzebyk.trader.trading.adapter.web

import dev.adriangrzebyk.trader.trading.Goods
import dev.adriangrzebyk.trader.trading.OfferType
import dev.adriangrzebyk.trader.trading.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
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
	fun shouldNoLongerServeTheUnscopedOfferEndpoints() {
		// They listed every player's offers to anyone (R17, A05); offers now live under /api/games/{id}/offers.
		restTestClient.get().uri("/api/trade-offers").exchange().expectStatus().isNotFound
		restTestClient.post().uri("/api/trade-offers").contentType(MediaType.APPLICATION_JSON).body("{}").exchange()
			.expectStatus().is4xxClientError
		restTestClient.get().uri("/api/trade-offers/resolve?system=TAU_CETI&goods=POLY").exchange().expectStatus().isNotFound
	}
}
