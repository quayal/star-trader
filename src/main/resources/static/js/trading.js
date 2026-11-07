/*
 * Pure helpers for the Trading screen (s03, E05-S01) and the Trading parts of the top bar and the decision alert. They
 * read `turn.initiative`, `turn.tradingQueue` and `turn.resolvedGoods` from GET /api/games/{id}
 * (BACKWARD_COMPATIBILITY.md §6). Everything here is public (R25, R36); no offer, bid or money is needed.
 */

export const BEFORE_TRADING_TEXT = 'Trading starts after Ship-jumps. Results will appear here.';
export const QUEUE_TITLE = 'Remaining goods';
export const RESOLVE_BUTTON = 'Resolve next';
export const YOUR_QUEUE_NOTE = 'You are first in initiative. Every good resolves, with or without offers. Drag to reorder, or use the arrows, at any time.';
export const YOUR_RESOLVE = "It's your turn to resolve the next good.";
export const GO_TO_TRADING = 'Go to Trading';

/** Whether [game] is running and in this turn's Trading. */
export function isTrading(game) {
	return game.status === 'RUNNING' && game.turn?.phase === 'TRADING';
}

function queue(game) {
	return game.turn?.tradingQueue ?? [];
}

function resolved(game) {
	return game.turn?.resolvedGoods ?? [];
}

function corporation(game, email) {
	return game.players.find(p => p.email === email)?.corporationName ?? email;
}

/** Who orders the queue (R25): the first player in initiative, during Trading; null otherwise. */
export function orderedBy(game) {
	return isTrading(game) ? game.turn.initiative?.[0] ?? null : null;
}

/** Whom the game waits for to resolve the next good (R25, D09): the first in initiative while goods remain, else null. */
export function resolver(game) {
	return queue(game).length > 0 ? orderedBy(game) : null;
}

/** "Mu Herculis · ISO": a good at a system, as the queue lists it. */
export function goodLabel(good) {
	return `${good.systemName} · ${good.goods}`;
}

/** S01-6: the top bar's addition during Trading, "3 of 18 resolved"; "" outside Trading. */
export function resolvedText(game) {
	if (!isTrading(game)) {
		return '';
	}
	const done = resolved(game).length;
	return `${done} of ${done + queue(game).length} resolved`;
}

/**
 * S01-8 (D09): whom the game waits for, named. "" outside Trading; once every good is resolved, says so (the turn's end
 * is E05-S07).
 */
export function waitingText(game, email) {
	if (!isTrading(game)) {
		return '';
	}
	const who = resolver(game);
	if (who === null) {
		return `All ${resolved(game).length} goods resolved.`;
	}
	return who === email ? 'You are first in initiative: resolve the next good.' : `Waiting for ${corporation(game, who)} to resolve the next good.`;
}

/** S01-3: the read-only list's note for everyone but the first in initiative. */
export function queueNote(game, email) {
	const who = orderedBy(game);
	if (who === null) {
		return '';
	}
	return who === email ? YOUR_QUEUE_NOTE : `Order decided by ${corporation(game, who)} (first in initiative); it can change until each one resolves.`;
}

/** The good resolved last, as "Last resolved: Mu Herculis · ISO", or "" before the first. */
export function lastResolvedText(game) {
	const last = resolved(game).at(-1);
	return isTrading(game) && last ? `Last resolved: ${goodLabel(last)}` : '';
}

/** [goods] with the good at [from] moved to [to]; the same list (a copy) when the move goes nowhere. */
export function moveGood(goods, from, to) {
	const moved = [...goods];
	if (to < 0 || to >= goods.length || from === to || from < 0 || from >= goods.length) {
		return moved;
	}
	const [good] = moved.splice(from, 1);
	moved.splice(to, 0, good);
	return moved;
}

/** What a move announces to a screen reader: "Mu Herculis · ISO moved to position 2 of 18." */
export function movedText(goods, to) {
	return `${goodLabel(goods[to])} moved to position ${to + 1} of ${goods.length}.`;
}

/** The body of PUT /api/games/{id}/trading/queue for [goods] in their new order. */
export function queueBody(goods) {
	return { queue: goods.map(({ system, goods: good }) => ({ system, goods: good })) };
}

/** Whether [a] and [b] list the same goods in the same order. */
export function sameOrder(a, b) {
	return a.length === b.length && a.every((good, index) => good.system === b[index].system && good.goods === b[index].goods);
}

/**
 * S01-9: the decision the game waits on [email] for, as the alert's text, or "" when there's none. In this story the
 * only Trading decision is resolving the next good; the amount, Dictator and wholesale decisions join it in S02–S05.
 */
export function decisionText(game, email) {
	return resolver(game) === email ? 'Your decision is needed: resolve the next good.' : '';
}

/**
 * S01-1: whether [game] has just entered Trading, as seen after [previous] (the last state this page drew): the same
 * game, a moment ago not yet in this turn's Trading. A first load (no previous, or another game's) never counts.
 */
export function tradingJustStarted(previous, game) {
	if (!previous || previous.id !== game.id || !isTrading(game)) {
		return false;
	}
	return previous.turn?.number !== game.turn.number || previous.turn?.phase !== 'TRADING';
}
