package dev.adriangrzebyk.trader.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R135: a free-form notepad per player, private, kept apart from the game.
class NotepadServiceTest {

	private val gameStore = FakeGameStore()
	private val notepads = FakeNotepadStore()
	private val service = NotepadService(gameStore, notepads)
	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")
	private val game = Game.lobby("game-1", 20, ada).addPlayer(grace).start("ada@example.com", START_DICE)

	init {
		gameStore.create(game)
	}

	@Test
	fun shouldGiveAnEmptyNotepadToAPlayerWhoNeverWroteOne() {
		assertThat(service.read("game-1", "ada@example.com")).isEqualTo(Notepad.EMPTY)
	}

	@Test
	fun shouldKeepWhatAPlayerWroteAndReplaceItOnTheNextSave() {
		service.write("game-1", "ada@example.com", "Turn 5: buy a factory at Tau Ceti")
		service.write("game-1", "ada@example.com", "Turn 5: buy a factory at Tau Ceti\nturn 6: jump to Beta Hydri")

		assertThat(service.read("game-1", "ada@example.com").text).isEqualTo("Turn 5: buy a factory at Tau Ceti\nturn 6: jump to Beta Hydri")
	}

	@Test
	fun shouldNeverShowAPlayersNotesToAnotherPlayerOfTheSameGame() {
		service.write("game-1", "ada@example.com", "my secret plan")

		assertThat(service.read("game-1", "grace@example.com")).isEqualTo(Notepad.EMPTY)
	}

	@Test
	fun shouldKeepEachGamesNotepadApart() {
		gameStore.create(Game.lobby("game-2", 20, ada).addPlayer(grace).start("ada@example.com", START_DICE))
		service.write("game-1", "ada@example.com", "game one")

		assertThat(service.read("game-2", "ada@example.com")).isEqualTo(Notepad.EMPTY)
	}

	@Test
	fun shouldRefuseAnyoneWhoIsNotAPlayerOrAGameThatDoesNotExist() {
		assertThatThrownBy { service.read("game-1", "stranger@example.com") }.isInstanceOf(NotAGameMemberException::class.java)
		assertThatThrownBy { service.write("game-1", "stranger@example.com", "hi") }.isInstanceOf(NotAGameMemberException::class.java)
		assertThatThrownBy { service.read("nope", "ada@example.com") }.isInstanceOf(GameNotFoundException::class.java)
	}

	@Test
	fun shouldRefuseMoreThanFiveThousandCharacters() {
		assertThatThrownBy { service.write("game-1", "ada@example.com", "x".repeat(Notepad.MAX_LENGTH + 1)) }
			.isInstanceOf(InvalidGameOperationException::class.java)
			.hasMessage("A notepad holds at most 5000 characters.")
		service.write("game-1", "ada@example.com", "x".repeat(Notepad.MAX_LENGTH))
	}

	@Test
	fun shouldLetAnEliminatedPlayerReadButNotChangeTheirNotes() {
		service.write("game-1", "ada@example.com", "before")
		gameStore.update("game-1") {
			it.copy(
				players = it.players.map { player ->
					if (player.email ==
						"ada@example.com"
					) {
						player.eliminate()
					} else {
						player
					}
				}
			)
		}

		assertThat(service.read("game-1", "ada@example.com").text).isEqualTo("before")
		assertThatThrownBy { service.write("game-1", "ada@example.com", "after") }
			.isInstanceOf(InvalidGameOperationException::class.java).hasMessage("You are out of the game; your notes are read-only.")
	}
}
