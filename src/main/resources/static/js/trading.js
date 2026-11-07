/*
 * Pure helpers for the Trading screen (s03, E05-S01, S02) and the Trading parts of the top bar and the decision alert.
 * They read `turn.initiative`, `turn.tradingQueue`, `turn.resolvedGoods` and `turn.resolution` from GET /api/games/{id}
 * (BACKWARD_COMPATIBILITY.md §6). Everything here is public (R25, R36): the only offers are those a resolution has
 * disclosed (E05-S02-1), and no bid or money is needed.
 */

export const BEFORE_TRADING_TEXT = 'Trading starts after Ship-jumps. Results will appear here.';
export const QUEUE_TITLE = 'Remaining goods';
export const RESOLVE_BUTTON = 'Resolve next';
export const YOUR_QUEUE_NOTE = 'You are first in initiative. Every good resolves, with or without offers. Drag to reorder, or use the arrows, at any time.';
export const YOUR_RESOLVE = "It's your turn to resolve the next good.";
export const GO_TO_TRADING = 'Go to Trading';
export const NO_OFFERS_TEXT = 'No offers for this good.';
export const DICTATOR_HELP = "You see everyone's offers above. Your offer has no other advantage: it trades only if the market and your price allow it.";

const TYPE_NAMES = { BUY: 'Buy', SELL: 'Sell' };

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

/** The good resolving now, or the last one resolved (E05-S02), during Trading; null otherwise and before the first. */
export function resolution(game) {
	return isTrading(game) ? game.turn.resolution ?? null : null;
}

/** Whom the resolution waits for to place an offer or pass (R38, D09): the Dictator asked, else null. */
export function awaitingDictator(game) {
	return resolution(game)?.awaitingDictator ?? null;
}

/**
 * Whom the game waits for to resolve the next good (R25, D09): the first in initiative while goods remain and the last
 * good has finished resolving (S01-4), else null.
 */
export function resolver(game) {
	return queue(game).length > 0 && awaitingDictator(game) === null ? orderedBy(game) : null;
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
	const resolving = resolution(game)?.finished === false ? 1 : 0;
	return `${done} of ${done + resolving + queue(game).length} resolved`;
}

/**
 * S01-8 (D09): whom the game waits for, named. "" outside Trading; once every good is resolved, says so (the turn's end
 * is E05-S07).
 */
export function waitingText(game, email) {
	if (!isTrading(game)) {
		return '';
	}
	const dictator = awaitingDictator(game);
	if (dictator !== null) {
		return dictator === email
			? `You're Dictator of ${resolution(game).good.goods} here: place an offer before the roll, or pass.`
			: `Waiting for ${corporation(game, dictator)} (Dictator) to decide.`;
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

/** The resolution panel's heading: "Now resolving: Mu Herculis · ISO", or "Resolved: …" once finished; "" with none. */
export function resolutionTitle(game) {
	const current = resolution(game);
	if (current === null) {
		return '';
	}
	return `${current.finished ? 'Resolved' : 'Now resolving'}: ${goodLabel(current.good)}`;
}

/**
 * S02-1, S02-3: the disclosed offers as the panel lists them, in order: who (the Dictator marked), Buy or Sell, and the
 * terms, "Buy at 11 HT".
 */
export function disclosedOffers(game) {
	const current = resolution(game);
	return (current?.offers ?? []).map(offer => ({
		corporation: corporation(game, offer.playerEmail),
		dictator: offer.playerEmail === current.dictator,
		type: offer.type,
		typeName: TYPE_NAMES[offer.type] ?? offer.type,
		price: offer.price,
	}));
}

/** S02-2: the Dictator's line under the offers: deciding, what they offered, or that they passed; "" with no Dictator. */
export function dictatorLine(game) {
	const current = resolution(game);
	if (!current?.dictator) {
		return '';
	}
	const who = `Dictator: ${corporation(game, current.dictator)}`;
	if (current.awaitingDictator) {
		return `${who} \u2014 deciding before the roll`;
	}
	const offer = current.offers.find(o => o.playerEmail === current.dictator);
	return offer ? `${who} \u2014 offered ${TYPE_NAMES[offer.type] ?? offer.type} at ${offer.price} HT` : `${who} \u2014 passed`;
}

/** S02-2: the Dictator form's question, for the Dictator asked; "" for everyone else. */
export function dictatorQuestion(game, email) {
	return awaitingDictator(game) === email ? `You're Dictator of ${resolution(game).good.goods} here. Place an offer before the roll?` : '';
}

/** The good a Dictator form belongs to, so what was typed is dropped when another good (or game, or turn) asks. */
export function dictatorFormKey(game) {
	const current = resolution(game);
	return current ? `${game.id}/${game.turn.number}/${current.good.system}/${current.good.goods}` : null;
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
 * S01-9: the decision the game waits on [email] for, as the alert's text, or "" when there's none: the Dictator's offer
 * (E05-S02) or resolving the next good. The amount and wholesale decisions join it in S04 and S05.
 */
export function decisionText(game, email) {
	if (awaitingDictator(game) === email) {
		const { good } = resolution(game);
		return `Your decision is needed: you're Dictator of ${good.goods} at ${good.systemName}. Place an offer or pass.`;
	}
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
