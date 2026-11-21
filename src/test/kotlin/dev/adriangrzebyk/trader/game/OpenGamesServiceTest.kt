package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenGamesServiceTest {

	private val gameStore = FakeGameStore()
	private val service = OpenGamesService(gameStore)
	private val viewer = Player.newcomer("viewer@example.com", "Viewer", "Viewer Corp")
	private val other = Player.newcomer("other@example.com", "Other", "Other Corp")

	@Test
	fun shouldListOnlyLobbiesWithTheViewersOwnGamesFirstThenTheOnesTheyAskedToJoin() {
		gameStore.create(Game.lobby("a-other", 20, other))
		gameStore.create(Game.lobby("b-asked", 20, other).requestToJoin(viewer))
		gameStore.create(Game.lobby("c-mine", 20, viewer))
		gameStore.create(
			Game.lobby("d-started", 20, other).addPlayer(viewer).start(
				other.email,
				FakeStartingSystemPicker(
					StarSystem.TAU_CETI to StarSystem.BETA_HYDRI,
					StarSystem.GAMMA_LEPORIS to StarSystem.MU_HERCULIS
				),
				START_DICE
			)
		)

		assertThat(service.openGames(viewer.email).map { it.id }).containsExactly("c-mine", "b-asked", "a-other")
	}
}
