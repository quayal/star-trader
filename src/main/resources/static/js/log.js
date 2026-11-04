/*
 * Pure helpers for the Game log (s04, E03-S06): the game's public events, grouped by turn. The log only ever holds
 * public lines (the server never logs offers, bids or money, R17, R29), so this only orders them.
 */

export const EMPTY_LOG_TEXT = 'No public events yet.';

/**
 * [log] ([{ turn, text }], oldest first) as one group per turn, newest turn first (S06-1), each turn's events in
 * the order they happened: [{ turn, title: 'Turn 4', events: [text] }].
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
		.map(([turn, events]) => ({ turn, title: `Turn ${turn}`, events }));
}

/** The key a folded turn is remembered by: per game, so a fold never carries into another game (lesson 2026-11-04). */
export function foldKey(gameId, turn) {
	return `${gameId}/${turn}`;
}
