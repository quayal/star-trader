package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GameJoiningServiceTest {

	private val gameStore = FakeGameStore()
	private val service = GameJoiningService(gameStore)
	private val creator = Player.newcomer("creator@example.com", "Creator", "Creator Corp")
	private val joiner = Player.newcomer("joiner@example.com", "Joiner", "Joiner Corp")
	private val game = Game.lobby("game-1", 20, creator)

	@Test
	fun shouldStoreTheRequestAndThenThePlayerOnceTheCreatorAccepts() {
		gameStore.create(game)

		service.requestToJoin("game-1", joiner)
		val joined = service.acceptJoinRequest("game-1", creator.email, joiner.email)

		assertThat(joined.players).containsExactly(creator, joiner)
		assertThat(joined.joinRequests).isEmpty()
		assertThat(gameStore.find("game-1")).isEqualTo(joined)
	}

	@Test
	fun shouldStoreADeclineAndAWithdrawal() {
		gameStore.create(game)

		service.requestToJoin("game-1", joiner)
		assertThat(service.declineJoinRequest("game-1", creator.email, joiner.email).joinRequests).isEmpty()
		service.requestToJoin("game-1", joiner)
		assertThat(service.withdrawJoinRequest("game-1", joiner.email).joinRequests).isEmpty()
		assertThat(gameStore.find("game-1")!!.joinRequests).isEmpty()
	}

	@Test
	fun shouldRejectAnUnknownGame() {
		assertThatThrownBy { service.requestToJoin("missing", joiner) }
			.isInstanceOf(GameNotFoundException::class.java)
			.hasMessage("This game doesn't exist")
	}

	@Test
	fun shouldLeaveTheGameUnchangedWhenThePlayerCannotAsk() {
		gameStore.create(game)

		assertThatThrownBy { service.requestToJoin("game-1", creator) }.isInstanceOf(InvalidGameOperationException::class.java)
		assertThat(gameStore.find("game-1")).isEqualTo(game)
	}
}
