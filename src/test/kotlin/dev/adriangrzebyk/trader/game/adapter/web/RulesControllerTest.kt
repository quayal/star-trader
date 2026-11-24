package dev.adriangrzebyk.trader.game.adapter.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.servlet.client.RestTestClient

// Playtest 2026-11-20: the Guide's tables come from the server.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RulesControllerTest {

	@LocalServerPort
	private var port: Int = 0

	private lateinit var restTestClient: RestTestClient

	@BeforeEach
	fun setUp() {
		restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:$port").build()
	}

	@Test
	fun shouldServeTheRulesWithoutSigningIn() {
		val rules = restTestClient.get().uri("/api/rules")
			.exchange()
			.expectStatus().isOk
			.expectBody(RulesResponseDto::class.java)
			.returnResult()
			.responseBody!!

		assertThat(rules.crewClasses).contains(CrewRuleResponseDto("A", 20, 5, 1), CrewRuleResponseDto("D", 2, 0, -1))
		assertThat(rules.agents).hasSize(10).contains(AgentRuleResponseDto("Badger", rules.agents.first { it.name == "Badger" }.effect, true))
		assertThat(rules.safeJumpBonus).isEqualTo(2)
		assertThat(rules.reputation.bonus[27]).isEqualTo(10)
		assertThat(rules.sabotage.first().from).isNull()
		// Playtest 2026-11-21: every hull and module, so players can plan a ship outside Investment.
		val clarinet = HullRuleResponseDto("CLARINET", "Clarinet", 160, 5, 1, 0, 3, 2, 2, 20, military = false, aerodynamic = false)
		val safeJump =
			ModuleRuleResponseDto("SAFE_JUMP", "Safe jump", 10, 0, 0, 0, 0, 0, military = false, takesModuleCapacity = false, jumpModifier = 2)
		val arsenal =
			ModuleRuleResponseDto("ARSENAL", "Arsenal", 100, 1, 7, 14, 0, 0, military = true, takesModuleCapacity = true, jumpModifier = 0)
		assertThat(rules.hulls).hasSize(13).contains(clarinet)
		assertThat(rules.modules).contains(safeJump, arsenal)
	}
}
