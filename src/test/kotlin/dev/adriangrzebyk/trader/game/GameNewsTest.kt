package dev.adriangrzebyk.trader.game

import dev.adriangrzebyk.trader.board.StarSystem.BETA_HYDRI
import dev.adriangrzebyk.trader.board.StarSystem.GAMMA_LEPORIS
import dev.adriangrzebyk.trader.board.StarSystem.MU_HERCULIS
import dev.adriangrzebyk.trader.board.StarSystem.TAU_CETI
import dev.adriangrzebyk.trader.game.news.ActiveTipOff
import dev.adriangrzebyk.trader.game.news.News
import dev.adriangrzebyk.trader.game.news.TipOff
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

// R109–R110: the News phase between Initiative and Jumps.
class GameNewsTest {

	private val ada = Player.newcomer("ada@example.com", "Ada", "Orion Freight")
	private val grace = Player.newcomer("grace@example.com", "Grace", "Vega Lines")

	/** Ada (connections placed CRIM 0) and Grace in turn 1's Initiative, with every tip-off in the pool. */
	private val initiative = Game.lobby("game-1", 5, ada, News.full()).addPlayer(grace)
		.start("ada@example.com", START_DICE).let {
			StartingShips(FakeStartingSystemPicker(TAU_CETI to BETA_HYDRI, GAMMA_LEPORIS to MU_HERCULIS)).placedIn(it)
		}
		.let { ConnectionsPlaced(it).byEveryone() }
		.advancePhase(FakeDice(2, 1))

	/** d3 2, drawing O1 (CRIM 4, lead 3) and then the 6th of those left, O7 (CRIM 6, lead 1). */
	private val newsDice = FakeDice(emptyList(), emptyList(), listOf(2), listOf(1, 6))

	private val news = initiative.pickTurnOrderPlace("ada@example.com", 1, newsDice)

	@Test
	fun shouldOpenNewsOnceTheTurnOrderIsSetAndDrawTipOffsWithTheirNeedAndTurnPublic() {
		assertThat(news.turn!!.phase).isEqualTo(TurnPhase.NEWS)
		assertThat(
			news.news.inPlay
		).containsExactly(ActiveTipOff(1, TipOff.O1, 1, emptyList(), null), ActiveTipOff(2, TipOff.O7, 1, emptyList(), null))
		assertThat(news.log.last().text).isEqualTo("News: rolled 2 for new tip-offs: #1 CRIM 4, effective turn 4, #2 CRIM 6, effective turn 2")
	}

	@Test
	fun shouldLetAPlayerLearnATipOffForFiveHtWhenTheirConnectionReachesItsLevel() {
		val connected = news.copy(
			players = news.players.map {
				if (it.email ==
					"ada@example.com"
				) {
					it.copy(connections = Connections(1, 1, 4))
				} else {
					it
				}
			}
		)
		val before = connected.players.first { it.email == "ada@example.com" }.money

		val learned = connected.learnTipOff("ada@example.com", 1)

		assertThat(learned.news.inSlot(1).isLearnedBy("ada@example.com")).isTrue()
		assertThat(learned.players.first { it.email == "ada@example.com" }.money).isEqualTo(before - 5)
		assertThat(learned.log).isEqualTo(connected.log)
		assertThat(learned.viewFor("ada@example.com")!!.tipOffs.first().learned).isTrue()
		assertThat(learned.viewFor("grace@example.com")!!.tipOffs.first().learned).isFalse()
		assertThatThrownBy { learned.learnTipOff("ada@example.com", 1) }.hasMessage("You already know this tip-off.")
		assertThatThrownBy { connected.learnTipOff("ada@example.com", 2) }.hasMessage("You need CRIM 6 to learn this (yours: 4).")
	}

	@Test
	fun shouldOpenJumpsOnceEveryoneIsDoneWithTheNews() {
		val oneDone = news.signalDoneNews("ada@example.com")

		assertThat(oneDone.turn!!.phase).isEqualTo(TurnPhase.NEWS)
		assertThat(oneDone.signalDoneNews("grace@example.com").turn!!.phase).isEqualTo(TurnPhase.JUMPS)
		assertThatThrownBy { initiative.learnTipOff("ada@example.com", 1) }.hasMessage("News hasn't started.")
	}

	@Test
	fun shouldAnnounceAnOpportunityThatExpiresAndReturnItToThePool() {
		// O7 (lead 1) drawn in turn 1 expires in turn 2's News; the draw then rolls 1 and takes the pool's first, O2
		val turnTwo = (1..5).fold(news.signalDoneNews("ada@example.com").signalDoneNews("grace@example.com")) { game, _ ->
			game.advancePhase(FakeDice(2, 1))
		}
		assertThat(turnTwo.turn!!.phase).isEqualTo(TurnPhase.INITIATIVE)

		val opened = turnTwo.advancePhase(FakeDice(emptyList(), emptyList(), listOf(1), listOf(1)))

		assertThat(opened.log.map { it.text }).contains(
			"News: the opportunity to buy drugs on Beta Hydri's Planet (15 HT) for sale on Gamma Leporis's Planet has expired."
		)
		// Playtest 2026-11-21: a pop-up for everyone, with no system and nobody singled out.
		val expired = opened.log.first { it.text.endsWith("has expired.") }
		assertThat(expired.notice).isEqualTo(Notice(NoticeKind.OPPORTUNITY_EXPIRED, null, emptyList(), null, null))
		assertThat(opened.news.pool).contains(TipOff.O7)
		assertThat(opened.news.inPlay.map { it.tipOff }).containsExactly(TipOff.O1, TipOff.O2)
	}
}
