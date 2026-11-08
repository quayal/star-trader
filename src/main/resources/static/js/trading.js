/*
 * Pure helpers for the Trading screen (s03, E05-S01 to S04) and the Trading parts of the top bar and the decision alert.
 * They read `turn.initiative`, `turn.tradingQueue`, `turn.resolvedGoods` and `turn.resolution` from GET /api/games/{id}
 * (BACKWARD_COMPATIBILITY.md §6). Those are public (R25, R36): the only offers are those a resolution has disclosed
 * (E05-S02-1). The amount form (E05-S04) also reads the viewer's own `you.tradeMax` and `you.ships`, which only they get.
 */

export const BEFORE_TRADING_TEXT = 'Trading starts after Ship-jumps. Results will appear here.';
export const QUEUE_TITLE = 'Remaining goods';
export const RESOLVE_BUTTON = 'Resolve next';
export const YOUR_QUEUE_NOTE = 'You are first in initiative. Every good resolves, with or without offers. Drag to reorder, or use the arrows, at any time.';
export const YOUR_RESOLVE = "It's your turn to resolve the next good.";
export const YOUR_DICTATOR_DECISION = "You're Dictator of the good resolving: place an offer before the roll, or pass.";
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

/** The offer whose player the game waits for to choose an amount (E05-S04, D09), with the counter's max; else null. */
export function awaitingTrade(game) {
	return resolution(game)?.awaitingTrade ?? null;
}

/**
 * Whom the game waits for to resolve the next good (R25, D09): the first in initiative while goods remain and the last
 * good has finished resolving (S01-4), its Dictator and every trader included, else null.
 */
export function resolver(game) {
	return queue(game).length > 0 && resolution(game)?.finished !== false ? orderedBy(game) : null;
}

function verb(type) {
	return type === 'SELL' ? 'sell' : 'buy';
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
	const trade = awaitingTrade(game);
	if (trade !== null) {
		return trade.playerEmail === email
			? `Your ${verb(trade.type)} at ${trade.price} HT can trade now: choose how many units.`
			: `${corporation(game, trade.playerEmail)} can ${verb(trade.type)} up to ${trade.maxUnits} u — waiting for their decision.`;
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

/**
 * E05-S03 (S03-6, D07): how the market traded, in plain terms with no dice arithmetic: "Market +3 → buyers first",
 * each trade ("Vega Lines bought 4 u at 11 HT each"), then "No one else can trade at this market." once it's over, or
 * "No one can trade at this market." when nothing traded (S03-7). Empty until the counter starts (after the Dictator).
 * A trader who could trade nothing reads "Vega Lines couldn't trade"; to that trader, with why (E05-S04-5, R29).
 */
export function counterLines(game, email) {
	const current = resolution(game);
	const counter = current?.startingCounter;
	if (counter === null || counter === undefined) {
		return [];
	}
	const signed = counter > 0 ? `+${counter}` : String(counter).replace('-', '\u2212');
	const trades = current.trades ?? [];
	const lines = [
		`Market ${signed} \u2192 ${counter >= 0 ? 'buyers' : 'sellers'} first`,
		...trades.map(trade => tradeLine(game, trade, email)),
	];
	if (current.finished) {
		lines.push(trades.some(trade => trade.units > 0) ? 'No one else can trade at this market.' : 'No one can trade at this market.');
	}
	return lines;
}

function tradeLine(game, trade, email) {
	if (trade.units === 0) {
		return trade.playerEmail === email && trade.reason ? `You couldn't trade. ${trade.reason}.` : `${corporation(game, trade.playerEmail)} couldn't trade`;
	}
	return `${corporation(game, trade.playerEmail)} ${trade.type === 'BUY' ? 'bought' : 'sold'} ${trade.units} u at ${trade.price} HT each`;
}

/** R42: POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. */
const TAKES_CARGO_SPACE = new Set(['POLY', 'ISO']);

/** "Wayfarer — POLY 4, CHIPS 12 · cargo 4 / 10 used" (E05-S04-3), "Wayfarer — empty · cargo 0 / 10 used". */
export function shipLabel(ship) {
	const carried = Object.entries(ship.cargo ?? {}).map(([goods, units]) => `${goods} ${units}`).join(', ');
	return `${ship.name} — ${carried || 'empty'} · cargo ${ship.cargoUsed} / ${ship.cargoSpaces} used`;
}

/**
 * E05-S04: the amount form for [email] when the game waits for their amount, else null. [max] is their own max after
 * money and cargo (R12, R10); [ships] are their ships at that system (R42), each with what it holds of the good and
 * its free cargo space for it (unlimited for CHIPS and FOOD).
 */
export function tradeForm(game, email) {
	const trade = awaitingTrade(game);
	if (trade === null || trade.playerEmail !== email || !game.you?.tradeMax) {
		return null;
	}
	const { good } = resolution(game);
	const here = (game.players.find(p => p.email === email)?.ships ?? []).filter(ship => ship.system === good.system).map(ship => ship.name);
	const ships = (game.you.ships ?? []).filter(ship => here.includes(ship.name)).map(ship => ({
		name: ship.name,
		label: shipLabel(ship),
		holding: ship.cargo?.[good.goods] ?? 0,
		free: TAKES_CARGO_SPACE.has(good.goods) ? Math.max(0, ship.cargoSpaces - ship.cargoUsed) : Infinity,
	}));
	const action = verb(trade.type);
	return {
		key: `${game.id}/${game.turn.number}/${good.system}/${good.goods}/${email}`,
		type: trade.type,
		goods: good.goods,
		systemName: good.systemName,
		price: trade.price,
		max: game.you.tradeMax,
		ships,
		heading: `Your ${action} at ${trade.price} HT can trade now`,
		amountLabel: `Amount to ${action} (u)`,
		help: `${trade.type === 'SELL' ? 'You get' : 'You pay'} your offer price, ${trade.price} HT each. Trading more leaves less for others.`,
		shipsLegend: `Your ships at ${good.systemName}`,
		noShip: ships.length === 0 ? `You have no ship at ${good.systemName}.` : '',
	};
}

/**
 * The form's starting split of [amount] across its ships (S04-3): a sell draws from each ship in order of what it holds;
 * a buy fills each ship's free space in order, then puts the rest on the first ship, where it's lost (S04-4).
 */
export function defaultSplit(form, amount) {
	let left = amount;
	const split = form.ships.map(ship => {
		const units = Math.min(left, form.type === 'SELL' ? ship.holding : ship.free);
		left -= units;
		return units;
	});
	if (left > 0 && form.type === 'BUY' && split.length > 0) {
		split[0] += left;
	}
	return split;
}

/** How many of a buy's units won't fit (S04-4): POLY and ISO beyond each ship's free space, everything with no ship there. */
export function lostUnits(form, amount, split) {
	if (form.type !== 'BUY') {
		return 0;
	}
	if (form.ships.length === 0) {
		return amount;
	}
	return form.ships.reduce((lost, ship, index) => lost + Math.max(0, (split[index] ?? 0) - ship.free), 0);
}

/** S04-4: said before confirming, when some of a buy will be lost; "" otherwise. */
export function lossWarning(form, amount, split) {
	const lost = lostUnits(form, amount, split);
	if (lost <= 0) {
		return '';
	}
	return form.ships.length === 0
		? `You have no ship at ${form.systemName}, so everything you buy will be lost.`
		: `Only ${amount - lost} u fit in the ships you chose; the rest will be lost.`;
}

function wholeNumber(text) {
	const value = String(text ?? '').trim();
	return /^\d+$/.test(value) ? Number(value) : null;
}

/**
 * Checks the amount form as the server will (S04-1, S04-3): { error, field } with field 'amount' or a ship's index, or
 * { body } for POST /api/games/{id}/trading/trade.
 */
export function validateTrade(form, amountText, splitTexts) {
	const amount = wholeNumber(amountText);
	if (amount === null || amount < 1 || amount > form.max) {
		return { error: `Enter a whole number from 1 to ${form.max}.`, field: 'amount' };
	}
	const split = splitTexts.map(wholeNumber);
	const bad = split.findIndex(units => units === null);
	if (bad >= 0) {
		return { error: 'Enter a whole number of units, 0 or more, for each ship.', field: bad };
	}
	const short = form.type === 'SELL' ? form.ships.findIndex((ship, index) => split[index] > ship.holding) : -1;
	if (short >= 0) {
		const ship = form.ships[short];
		return { error: `${ship.name} has only ${ship.holding} ${form.goods}.`, field: short };
	}
	if (form.ships.length > 0 && split.reduce((sum, units) => sum + units, 0) !== amount) {
		return { error: `Your ships' units must add up to ${amount}.`, field: 0 };
	}
	return { body: { amount, ships: form.ships.map((ship, index) => ({ name: ship.name, units: split[index] })) } };
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
 * (E05-S02), their amount (E05-S04) or resolving the next good. The wholesale decision joins it in S05.
 */
export function decisionText(game, email) {
	if (awaitingDictator(game) === email) {
		const { good } = resolution(game);
		return `Your decision is needed: you're Dictator of ${good.goods} at ${good.systemName}. Place an offer or pass.`;
	}
	if (awaitingTrade(game)?.playerEmail === email) {
		const { good } = resolution(game);
		return `Your decision is needed: your ${verb(awaitingTrade(game).type)} of ${good.goods} at ${good.systemName} can trade now. Choose how many units.`;
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
