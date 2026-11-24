package dev.adriangrzebyk.trader.game.control

import dev.adriangrzebyk.trader.game.NoticeKind

/**
 * What a reputation control's second roll came to (R129), the creator's table: the [fine] the player pays (assets sold if
 * they can't), [connectionsLost] from each of their connections, the reputation it [reputationSet] to (null: unchanged), and
 * whether the player is [eliminated]. A player who backed the one eliminated loses [backerPoliticalLost] political and
 * [backerOthersLost] of each other connection, and 2d6 reputation.
 */
enum class ControlResult(
	val fine: Int,
	val connectionsLost: Int,
	val reputationSet: Int?,
	val eliminated: Boolean,
	val backerPoliticalLost: Int,
	val backerOthersLost: Int
) {
	INNOCENT(0, 0, INNOCENT_REPUTATION, false, 0, 0),
	SMALL_FINE(100, 2, SMALL_FINE_REPUTATION, false, 0, 0),
	LARGE_FINE(400, 4, null, false, 0, 0),
	TAKEN_OVER(0, 0, null, true, 4, 2),
	CLOSED(0, 0, null, true, MAX_POLITICAL, 3);

	/** The public line's wording of what happened to [corporation]. */
	fun text(corporation: String): String = when (this) {
		INNOCENT -> "$corporation was deemed innocent: reputation 8"
		SMALL_FINE -> "$corporation was fined $fine HT: every connection −2, reputation 6"
		LARGE_FINE -> "$corporation was fined $fine HT: every connection −4"
		TAKEN_OVER -> "$corporation was taken over by its criminal connections and is out of the game"
		CLOSED -> "$corporation was closed by the Federation and is out of the game"
	}

	/** The card its player gets (R136): out of the game, fined, or cleared. */
	fun noticeKind(): NoticeKind = when {
		eliminated -> NoticeKind.ELIMINATED
		fine > 0 -> NoticeKind.CONTROL_FINED
		else -> NoticeKind.CONTROL_CLEARED
	}

	/** The public line for [backer], who backed the [corporation] this result eliminated (R129). */
	fun backerText(backer: String, corporation: String): String {
		val political = if (backerPoliticalLost >= MAX_POLITICAL) "all political" else "$backerPoliticalLost political"
		return "$backer lost $political and $backerOthersLost of each other connection for backing $corporation."
	}
}

private const val INNOCENT_REPUTATION = 8
private const val SMALL_FINE_REPUTATION = 6
private const val MAX_POLITICAL = 10
