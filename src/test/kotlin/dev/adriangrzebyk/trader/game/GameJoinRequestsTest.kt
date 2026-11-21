package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** R119, R120: asking to join a game in its lobby, and the creator's answer. */
class GameJoinRequestsTest {

	private val creator = Player.newcomer("creator@example.com", "Creator", "Creator Corp")
	private val ada = Player.newcomer("ada@example.com", "Ada", "Ada Lines")
	private val bo = Player.newcomer("bo@example.com", "Bo", "Bo Freight")
	private val lobby = Game.lobby("game-1", 20, creator)

	@Test
	fun shouldKeepARequestUntilTheCreatorAcceptsItAndThenAddThePlayer() {
		val asked = lobby.requestToJoin(ada)

		assertThat(asked.players).containsExactly(creator)
		assertThat(asked.joinRequests).containsExactly(ada)
		assertThat(asked.lobbyStandingOf(ada.email)).isEqualTo(LobbyStanding.ASKED)

		val accepted = asked.acceptJoinRequest(creator.email, ada.email)

		assertThat(accepted.players).containsExactly(creator, ada)
		assertThat(accepted.joinRequests).isEmpty()
		assertThat(accepted.lobbyStandingOf(ada.email)).isEqualTo(LobbyStanding.IN)
	}

	@Test
	fun shouldDropADeclinedRequestWithoutAddingThePlayer() {
		val declined = lobby.requestToJoin(ada).requestToJoin(bo).declineJoinRequest(creator.email, ada.email)

		assertThat(declined.players).containsExactly(creator)
		assertThat(declined.joinRequests).containsExactly(bo)
		assertThat(declined.lobbyStandingOf(ada.email)).isEqualTo(LobbyStanding.NONE)
	}

	@Test
	fun shouldLetOnlyTheCreatorDecide() {
		val asked = lobby.requestToJoin(ada).requestToJoin(bo)

		assertThatThrownBy { asked.acceptJoinRequest(bo.email, ada.email) }
			.isInstanceOf(NotTheCreatorException::class.java)
			.hasMessage("Only the creator can accept or decline a request to join")
		assertThatThrownBy { asked.declineJoinRequest(ada.email, ada.email) }.isInstanceOf(NotTheCreatorException::class.java)
	}

	@Test
	fun shouldRefuseToDecideOnARequestThatIsNoLongerWaiting() {
		val withdrawn = lobby.requestToJoin(ada).withdrawJoinRequest(ada.email)

		assertThatThrownBy { withdrawn.acceptJoinRequest(creator.email, ada.email) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("That request to join is no longer waiting")
		assertThatThrownBy { withdrawn.declineJoinRequest(creator.email, "") }.isInstanceOf(InvalidGameOperationException::class.java)
	}

	@Test
	fun shouldChangeNothingWhenAPlayerWithdrawsARequestTheyNeverMade() {
		assertThat(lobby.withdrawJoinRequest(ada.email)).isEqualTo(lobby)
	}

	@Test
	fun shouldRefuseASecondRequestOrOneFromAPlayerAlreadyIn() {
		assertThatThrownBy { lobby.requestToJoin(ada).requestToJoin(ada) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("You have already asked to join this game")
		assertThatThrownBy { lobby.requestToJoin(creator) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This player is already in the game")
	}

	@Test
	fun shouldRefuseARequestToAFullGameAndAcceptingOneOnceItIsFull() {
		val fiveIn = (1..4).fold(lobby) { game, n -> game.addPlayer(Player.newcomer("p$n@example.com", "P$n", "Corp $n")) }
		val asked = fiveIn.requestToJoin(ada).requestToJoin(bo)
		val full = asked.acceptJoinRequest(creator.email, ada.email)

		assertThatThrownBy { full.requestToJoin(Player.newcomer("late@example.com", "Late", "Late Co")) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game is full")
		assertThatThrownBy { full.acceptJoinRequest(creator.email, bo.email) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game is full")
	}

	@Test
	fun shouldDropWaitingRequestsWhenTheGameStartsAndRefuseNewOnes() {
		val started = lobby.addPlayer(bo).requestToJoin(ada)
			.start(creator.email, FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS), START_DICE)

		assertThat(started.joinRequests).isEmpty()
		assertThatThrownBy { started.requestToJoin(ada) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("This game has already started")
	}

	@Test
	fun shouldNameItsCreator() {
		assertThat(lobby.addPlayer(ada).creator()).isEqualTo(creator)
	}
}
