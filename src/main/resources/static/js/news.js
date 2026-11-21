/*
 * Pure helpers for the News screen (R109–R110). The server sends the tip-offs in play as `you.tipOffs` ({ slot, need,
 * effectiveTurn, learned, kind, details, taken, needRefusal }): everyone sees the need and the turn, only a player who
 * learned one its details. A tip-off is only information; what a player takes is the opportunity (creator, 2026-11-20).
 */

/** R110: what learning one tip-off costs. */
export const LEARN_COST = 5;

export const NO_TIP_OFFS_TEXT = 'No tip-offs are in play.';

export function isNews(game) {
	return game?.status === 'RUNNING' && game.turn?.phase === 'NEWS';
}

/** Who the News is waiting for (R109): "you" first, then the other players not yet done, by corporation. */
export function waitingForNews(game, playerId) {
	if (!isNews(game)) {
		return [];
	}
	const pending = game.players.filter(p => !(game.turn.doneNews ?? []).includes(p.id));
	const you = pending.some(p => p.id === playerId) ? ['you'] : [];
	return [...you, ...pending.filter(p => p.id !== playerId).map(p => p.corporationName)];
}

/**
 * One row per tip-off in play: `{ slot, effectiveTurn, title: 'Tip-off #3', need: 'CRIM 5', details, taken, learn: {
 * enabled, reason } }`. Learning is offered only in News and only for what isn't learned yet; the reason says why
 * the button is disabled: the connection (in the server's words), or that it opens in News.
 */
export function tipOffRows(game) {
	return (game.you?.tipOffs ?? []).map(tipOff => {
		const learnable = !tipOff.learned;
		const reason = !learnable ? '' : tipOff.needRefusal ? tipOff.needRefusal : isNews(game) ? '' : 'Tip-offs are learned in News.';
		return {
			slot: tipOff.slot,
			title: `Tip-off #${tipOff.slot}`,
			effectiveTurn: tipOff.effectiveTurn,
			need: tipOff.need,
			details: tipOff.learned ? tipOff.details : null,
			taken: tipOff.taken ? 'Already taken.' : '',
			learn: learnable ? { enabled: reason === '', reason, label: `Learn (${LEARN_COST} HT)` } : null,
		};
	});
}

/** "This turn (turn 3)", "Next turn (turn 4)" or "Turn 6": when a group of tip-offs takes place or expires. */
export function turnHeading(effectiveTurn, currentTurn) {
	if (effectiveTurn === currentTurn) {
		return `This turn (turn ${effectiveTurn})`;
	}
	return effectiveTurn === currentTurn + 1 ? `Next turn (turn ${effectiveTurn})` : `Turn ${effectiveTurn}`;
}

/**
 * The tip-offs in play grouped by the turn they take place or expire (playtest, 2026-11-21), soonest first: `[{ heading,
 * rows }]`, the rows as [tipOffRows]. Within a turn the order doesn't matter, so they keep the server's.
 */
export function tipOffGroups(game) {
	const byTurn = new Map();
	for (const row of tipOffRows(game)) {
		byTurn.set(row.effectiveTurn, [...(byTurn.get(row.effectiveTurn) ?? []), row]);
	}
	return [...byTurn.keys()].sort((a, b) => a - b)
		.map(turn => ({ heading: turnHeading(turn, game.turn?.number), rows: byTurn.get(turn) }));
}

/** This turn's announcements (R109): the log lines of the current turn that start with "News:", oldest first. */
export function newsAnnouncements(game) {
	const turn = game.turn?.number;
	return (game.log ?? []).filter(entry => entry.turn === turn && entry.text.startsWith('News:')).map(entry => entry.text);
}
