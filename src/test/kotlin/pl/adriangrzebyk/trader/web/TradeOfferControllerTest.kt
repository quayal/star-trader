package pl.adriangrzebyk.trader.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import pl.adriangrzebyk.trader.domain.Goods
import pl.adriangrzebyk.trader.domain.OfferType
import pl.adriangrzebyk.trader.domain.StarSystem
import pl.adriangrzebyk.trader.web.dto.TradeOfferOptions
import pl.adriangrzebyk.trader.web.dto.TradeOfferRequest
import pl.adriangrzebyk.trader.web.dto.TradeOfferView

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeOfferControllerTest {

	@LocalServerPort
	private var port: Int = 0

	private lateinit var webTestClient: WebTestClient

	@BeforeEach
	fun setUp() {
		webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
	}

	@Test
	fun shouldExposeOptionsForBuildingTheForm() {
		val options = webTestClient.get().uri("/api/options")
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
	fun shouldAcceptAValidTradeOffer() {
		val request = TradeOfferRequest(
			playerName = "Adrian",
			system = StarSystem.TAU_CETI,
			goods = Goods.POLY,
			offerType = OfferType.BUY,
			price = 10,
		)

		val view = webTestClient.post().uri("/api/trade-offers")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
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

		val offers = webTestClient.get().uri("/api/trade-offers")
			.exchange()
			.expectStatus().isOk
			.expectBodyList(TradeOfferView::class.java)
			.returnResult()
			.responseBody!!

		assertThat(offers).contains(view)
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

		webTestClient.post().uri("/api/trade-offers")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
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

		webTestClient.post().uri("/api/trade-offers")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.exchange()
			.expectStatus().isBadRequest
	}
}
