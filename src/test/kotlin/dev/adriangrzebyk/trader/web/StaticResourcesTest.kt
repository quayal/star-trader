package dev.adriangrzebyk.trader.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.client.RestTestClient

// A changed stylesheet must reach every browser on its next load, not when a heuristic cache lifetime ends.
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["RESEND_API_KEY=", "RESEND_FROM_ADDRESS="]
)
class StaticResourcesTest {

	@LocalServerPort
	private var port: Int = 0

	private lateinit var restTestClient: RestTestClient

	@BeforeEach
	fun setUp() {
		restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
	}

	@Test
	fun shouldTellBrowsersToRevalidateStaticFiles() {
		val headers = restTestClient.get().uri("/css/components.css").exchange()
			.expectStatus().isOk
			.returnResult(String::class.java).responseHeaders

		assertThat(headers.cacheControl).contains("no-cache")
		assertThat(headers.lastModified).isPositive()
	}

	@Test
	fun shouldAnswerAnUnchangedFileWithNotModified() {
		val lastModified = restTestClient.get().uri("/css/components.css").exchange()
			.returnResult(String::class.java).responseHeaders.getFirst(HttpHeaders.LAST_MODIFIED)!!

		restTestClient.get().uri("/css/components.css").header(HttpHeaders.IF_MODIFIED_SINCE, lastModified).exchange()
			.expectStatus().isEqualTo(HttpStatus.NOT_MODIFIED)
	}
}
