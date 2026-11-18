/*
 * Pure helpers for the Game log (s04, E03-S06): the game's public events, grouped by turn. The log only ever holds
 * public lines (the server never logs offers, bids or money, R17, R29), so this only orders them.
 */

export const EMPTY_LOG_TEXT = 'No public events yet.';

/**
 * [log] ([{ turn, text }], oldest first) as one group per turn, newest turn first (S06-1), each turn's events newest
 * first too (creator, 2026-11-18): [{ turn, title: 'Turn 4', events: [text] }].
 */
export function logTurns(log) {
	const byTurn = new Map();
	for (const { turn, text } of log ?? []) {
		if (!byTurn.has(turn)) {
			byTurn.set(turn, []);
		}
		byTurn.get(turn).push(text);
	}
	return [...byTurn.entries()]
		.sort(([a], [b]) => b - a)
		.map(([turn, events]) => ({ turn, title: `Turn ${turn}`, events: events.reverse() }));
}

/** The key a folded turn is remembered by: per game, so a fold never carries into another game (lesson 2026-11-04). */
export function foldKey(gameId, turn) {
	return `${gameId}/${turn}`;
}

// ── The poll asks only for the log entries it doesn't have ──
//
// GET /api/games/{id}?logAfter={n} answers with the entries after the first n and `logOffset: n`; without it, or when n
// isn't a number of entries the log has, with the whole log and `logOffset: 0` (BACKWARD_COMPATIBILITY.md §6). [known] is
// what the page last drew: `{ id, entries }`, or null.

/** The poll's query for game [gameId]: the entries after the ones [known] holds for that game, or "" to get the whole log. */
export function logAfterQuery(known, gameId) {
	return known?.id === gameId && known.entries.length > 0 ? `?logAfter=${known.entries.length}` : '';
}

/**
 * The whole log of [game] as a poll answered it, joined to what [known] held when the poll was sent; null when the
 * answer doesn't follow on from it (another game's, or fewer entries than its offset), so the caller asks again for all.
 */
export function mergeLog(known, game) {
	const offset = game.logOffset ?? 0;
	const newer = game.log ?? [];
	if (offset === 0) {
		return newer;
	}
	if (known?.id !== game.id || known.entries.length < offset) {
		return null;
	}
	return [...known.entries.slice(0, offset), ...newer];
}
