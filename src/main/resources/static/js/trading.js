/*
 * Pure helpers for the Trading screen (s03, E05-S01 to S05) and the Trading parts of the top bar and the decision alert.
 * They read `turn.initiative`, `turn.tradingQueue`, `turn.resolvedGoods` and `turn.resolution` from GET /api/games/{id}
 * (BACKWARD_COMPATIBILITY.md §6). Those are public (R25, R36), and so is `turn.publicOffers`: every offer of the turn
 * once Trading has started (R17), listed under the goods still to resolve (E08-S01). The amount form (E05-S04) also reads the viewer's own `you.tradeMax` and `you.ships`, which only they get,
 * and the wholesale form (E05-S05) their `you.wholesaleMax`.
 */

export const BEFORE_TRADING_TEXT = 'Trading starts after Jumps. Results will appear here.';
/** E05-S07-1: s03 once the last good has resolved and the next turn has started. */
export const TURN_ENDED_TEXT = 'All goods resolved. The next turn starts with Bidding.';
/** E06-S01: s03 once the last good has resolved and players are investing. */
export const INVESTING_TEXT = 'All goods resolved. The next turn starts with Bidding once Opportunity and Investment are over.';
/** E05-S08: s03 once the last turn's last good has resolved and the game is over. */
export const GAME_OVER_TEXT = 'All goods resolved. The game is over.';
export const QUEUE_TITLE = 'Remaining goods';
export const RESOLVE_BUTTON = 'Resolve next';
export const YOUR_QUEUE_NOTE = 'You are first in initiative. Every good resolves, with or without offers. Drag to reorder, or use the arrows, at any time.';
export const YOUR_RESOLVE = "It's your turn to resolve the next good.";
export const YOUR_DICTATOR_DECISION = "You're Dictator of the good resolving: place an offer before the roll, or pass.";
export const GO_TO_TRADING = 'Go to Trading';
export const NO_OFFERS_TEXT = 'No offers for this good.';
export const TRADE_NOTHING_BUTTON = 'Trade nothing';
export const WHOLESALE_HELP = "This doesn't move the price.";
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

function corporation(game, playerId) {
	return game.players.find(p => p.id === playerId)?.corporationName ?? playerId;
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

/** Whom the wholesale step waits for to trade or pass (R37, E05-S05-4, D09); else null. */
export function awaitingWholesaler(game) {
	return resolution(game)?.awaitingWholesaler ?? null;
}

/** "Dictator" or "Wholesaler": why [playerId] is asked in the wholesale step of the current resolution (R37). */
function wholesaleRole(game, playerId) {
	return resolution(game)?.dictator === playerId ? 'Dictator' : 'Wholesaler';
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
 * How much the viewer may trade now (creator, 2026-11-18), from their own `you.tradeMax` (after money and cargo, R12,
 * R10): "you can sell 4 u", "you can't sell any".
 */
function canTradeText(game, type) {
	const max = game.you?.tradeMax ?? 0;
	return max > 0 ? `you can ${verb(type)} ${max} u` : `you can't ${verb(type)} any`;
}

/**
 * S01-8 (D09): whom the game waits for, named. "" outside Trading; once every good is resolved, says so (the turn's end
 * is E05-S07).
 */
export function waitingText(game, playerId) {
	if (!isTrading(game)) {
		return '';
	}
	const dictator = awaitingDictator(game);
	if (dictator !== null) {
		return dictator === playerId
			? `You're Dictator of ${resolution(game).good.goods} here${priceNote(game)}: place an offer before the roll, or pass.`
			: `Waiting for ${corporation(game, dictator)} (Dictator) to decide.`;
	}
	const trade = awaitingTrade(game);
	if (trade !== null) {
		return trade.playerId === playerId
			? `The market has reached your ${verb(trade.type)} at ${trade.price} HT (${canTradeText(game, trade.type)}): your decision.`
			: `${corporation(game, trade.playerId)} can ${verb(trade.type)} up to ${trade.maxUnits} u — waiting for their decision.`;
	}
	const wholesaler = awaitingWholesaler(game);
	if (wholesaler !== null) {
		const { newPrice } = resolution(game);
		return wholesaler === playerId
			? `As ${wholesaleRole(game, playerId)}, you may buy or sell up to 5 u at the new price, ${newPrice} HT, or pass: your decision.`
			: `Waiting for ${corporation(game, wholesaler)} (${wholesaleRole(game, wholesaler)}) to trade up to 5 u at ${newPrice} HT, or pass.`;
	}
	const who = resolver(game);
	if (who === null) {
		return `All ${resolved(game).length} goods resolved.`;
	}
	return who === playerId ? 'You are first in initiative: resolve the next good.' : `Waiting for ${corporation(game, who)} to resolve the next good.`;
}

/**
 * s03's status line: during Trading, whom the game waits for (S01-8); during the Investment that follows it, that every
 * good resolved and the next turn waits for everyone's investing (E06-S01); in the Bidding that follows a turn's
 * Investment, that every good resolved and the new turn has started (E05-S07-1: Trading ends the moment its last good
 * resolves, and the turn once Investment has); once the game is over, that it is (E05-S08); before Trading otherwise,
 * when it starts (S01-7).
 */
export function tradingStatus(game, playerId) {
	if (isTrading(game)) {
		return waitingText(game, playerId);
	}
	if (game.status === 'FINISHED') {
		return GAME_OVER_TEXT;
	}
	if (game.status === 'RUNNING' && (game.turn?.phase === 'OPPORTUNITY' || game.turn?.phase === 'INVESTMENT')) {
		return INVESTING_TEXT;
	}
	if (game.status === 'RUNNING' && game.turn?.phase === 'BIDDING' && game.turn.number > 1) {
		return TURN_ENDED_TEXT;
	}
	return BEFORE_TRADING_TEXT;
}

/** S01-3: the read-only list's note for everyone but the first in initiative. */
export function queueNote(game, playerId) {
	const who = orderedBy(game);
	if (who === null) {
		return '';
	}
	return who === playerId ? YOUR_QUEUE_NOTE : `Order decided by ${corporation(game, who)} (first in initiative); it can change until each one resolves.`;
}

function publicOffers(game) {
	return game.turn?.publicOffers ?? [];
}

/**
 * E08-S01-1: every offer on [good] this turn, as the queue lists them under it: [playerId]'s own first, as "You", then
 * the others in the order they were placed. Offers are public once Trading has started (R17).
 */
export function queueOffers(game, good, playerId) {
	const offers = publicOffers(game).filter(offer => offer.system === good.system && offer.goods === good.goods);
	return [...offers.filter(offer => offer.playerId === playerId), ...offers.filter(offer => offer.playerId !== playerId)]
		.map(offer => ({
			who: offer.playerId === playerId ? 'You' : corporation(game, offer.playerId),
			type: offer.type,
			typeName: TYPE_NAMES[offer.type] ?? offer.type,
			price: offer.price,
		}));
}

export const DICTATOR_MARK = "You're Dictator here: you place your offer when it resolves";

/**
 * Whether [playerId] is Dictator of [good] at its system (R28, public), so they place their offer for it when it
 * resolves and not during Bidding (R38): worth knowing when ordering the queue, as an offer is.
 */
export function isDictatorOf(game, good, playerId) {
	const positions = game.players.find(p => p.id === playerId)?.marketPositions ?? [];
	return positions.some(position => position.system === good.system && position.goods === good.goods && position.tier === 'DICTATOR');
}

export const OFFERS_NOTE = 'Each good lists the offers on it: every offer became public when Trading started.';
export const NO_OFFERS_LEFT_NOTE = 'None of the remaining goods has an offer.';
export const NO_OFFERS_PLACED_NOTE = 'Nobody placed an offer this turn.';

/** E08-S01-4: the line under the queue: what the offers under the goods are, or that none is left. "" outside Trading. */
export function queueOffersNote(game) {
	if (!isTrading(game)) {
		return '';
	}
	if (queue(game).some(good => queueOffers(game, good, null).length > 0)) {
		return OFFERS_NOTE;
	}
	return publicOffers(game).length > 0 ? NO_OFFERS_LEFT_NOTE : NO_OFFERS_PLACED_NOTE;
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
		corporation: corporation(game, offer.playerId),
		dictator: offer.playerId === current.dictator,
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
	const offer = current.offers.find(o => o.playerId === current.dictator);
	return offer ? `${who} \u2014 offered ${TYPE_NAMES[offer.type] ?? offer.type} at ${offer.price} HT` : `${who} \u2014 passed`;
}

/**
 * E05-S03 (S03-6, D07): how the market traded, in plain terms with no dice arithmetic: "Market +3 → buyers first",
 * each trade as what took place (E05-S04-6: "Vega Lines buys 10 u for 130 HT"; the unit price is the offer's, listed
 * above, and what the market allowed was on screen while it waited) and the position roll it made, if any (E05-S06-4),
 * then "No one else can trade at this market." once it's over, or "No one can trade at this market." when no unit
 * traded (S03-7). The price change (E05-S05-2) ends the last trade that moved units, "Star Dust sells 4 u for 48 HT; the
 * price falls to 18", or the "No one can trade" line when none did. Then each wholesale answer: "Kessler Cargo
 * (Wholesaler) buys 3 u for 30 HT" (S05-5), at that new price. Empty until the counter starts (after the Dictator).
 *
 * Each line is a list of parts: text, or `{ text, tone }` for a word shown in colour: "buyers" and "sellers" in the
 * Buy and Sell colours (`buy`, `sell`), "rises" and "falls" in green and red (`rise`, `fall`). [lineText] joins them.
 */
export function counterLines(game) {
	const current = resolution(game);
	const counter = current?.startingCounter;
	if (counter === null || counter === undefined) {
		return [];
	}
	const signed = counter > 0 ? `+${counter}` : String(counter).replace('-', '\u2212');
	const trades = current.trades ?? [];
	const side = counter >= 0 ? { text: 'buyers', tone: 'buy' } : { text: 'sellers', tone: 'sell' };
	const over = current.newPrice !== null && current.newPrice !== undefined;
	const price = over ? priceParts(current) : [];
	const lastTraded = trades.findLastIndex(trade => trade.units > 0);
	const lines = [
		[`Market ${signed} \u2192 `, side, ' first'],
		...trades.flatMap((trade, i) => [
			[tradeLine(game, trade), ...(i === lastTraded ? price : [])],
			positionRollLine(game, trade, current.good),
		].filter(Boolean)).map(line => (Array.isArray(line) ? line : [line])),
	];
	if (over || current.finished) {
		lines.push(lastTraded >= 0 ? ['No one else can trade at this market.'] : ['No one can trade at this market', ...(over ? price : []), '.']);
	}
	if (over) {
		lines.push(...(current.wholesales ?? []).map(wholesale => [wholesaleLine(game, wholesale)]));
	}
	return lines;
}

/** A line of [counterLines] as plain text. */
export function lineText(parts) {
	return parts.map(part => (typeof part === 'string' ? part : part.text)).join('');
}

/** E05-S05-2: "; the price rises to 12", "; the price falls to 10" or "; the price stays at 11", ending a trade line. */
function priceParts(current) {
	const { startPrice, newPrice } = current;
	if (newPrice === startPrice) {
		return [`; the price stays at ${newPrice}`];
	}
	const move = newPrice > startPrice ? { text: 'rises', tone: 'rise' } : { text: 'falls', tone: 'fall' };
	return ['; the price ', move, ` to ${newPrice}`];
}

function wholesaleLine(game, wholesale) {
	const who = `${corporation(game, wholesale.playerId)} (${wholesale.role})`;
	if (!wholesale.type) {
		return `${who} passes on wholesale`;
	}
	const done = wholesale.type === 'SELL' ? 'sells' : 'buys';
	return `${who} ${done} ${wholesale.units} u for ${wholesale.units * wholesale.price} HT`;
}

/** R28: the market-position tiers' names. */
const TIER_NAMES = { RETAILER: 'Retailer', WHOLESALER: 'Wholesaler', DICTATOR: 'Dictator' };

/**
 * E05-S06-4: a trade's position roll, if it made one: "Vega Lines trades 7 u — rolls 5: now Wholesaler of ISO at Mu
 * Herculis", "… rolls 9: no change", the rise in business connections it brings, "…; business connections rise to
 * 4" (E12-S02-6), and a Dictator's demotion, "…; Altair Trade Co. is demoted to Wholesaler". Null for a trade that made
 * no roll.
 */
function positionRollLine(game, trade, good) {
	const roll = trade.positionRoll;
	if (!roll) {
		return null;
	}
	const change = roll.promotedTo
		? `now ${TIER_NAMES[roll.promotedTo] ?? roll.promotedTo} of ${good.goods} at ${good.systemName}`
		: 'no change';
	const business = roll.businessConnections == null ? '' : `; business connections rise to ${roll.businessConnections}`;
	const demoted = (roll.demoted ?? []).map(playerId => `; ${corporation(game, playerId)} is demoted to Wholesaler`).join('');
	return `${corporation(game, trade.playerId)} trades ${trade.units} u \u2014 rolls ${roll.roll}: ${change}${business}${demoted}`;
}

/** E05-S04-6: "Star Dust sells 4 u for 48 HT", or "Vega Lines buys nothing" (present tense, creator 2026-11-18). */
function tradeLine(game, trade) {
	const done = trade.type === 'SELL' ? 'sells' : 'buys';
	const who = corporation(game, trade.playerId);
	return trade.units === 0 ? `${who} ${done} nothing` : `${who} ${done} ${trade.units} u for ${trade.units * trade.price} HT`;
}

/** R42: POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. */
const TAKES_CARGO_SPACE = new Set(['POLY', 'ISO']);

/** "Wayfarer — POLY 4, CHIPS 12 · cargo 4 / 10 used" (E05-S04-3), "Wayfarer — empty · cargo 0 / 10 used". */
export function shipLabel(ship) {
	const carried = Object.entries(ship.cargo ?? {}).map(([goods, units]) => `${goods} ${units}`).join(', ');
	return `${ship.name} — ${carried || 'empty'} · cargo ${ship.cargoUsed} / ${ship.cargoSpaces} used`;
}

/** "Warehouse — POLY 4, CHIPS 12 · cargo 4 / 10 used" (R49): a warehouse of the viewer, shown like a ship. */
export function warehouseLabel(warehouse) {
	const held = Object.entries(warehouse.cargo ?? {}).map(([goods, units]) => `${goods} ${units}`).join(', ');
	return `Warehouse \u2014 ${held || 'empty'} \u00b7 cargo ${warehouse.cargoUsed} / ${warehouse.cargoSpaces} used`;
}

/** Whether [form] also offers the player's warehouse at its system, beside their ships (R49). */
function hasWarehouse(form) {
	return form.ships.some(place => place.warehouse);
}

/** "ships", or "ships and warehouse" when [form] offers a warehouse too, for the form's wording (R49). */
function placesWord(form) {
	return hasWarehouse(form) ? 'ships and warehouse' : 'ships';
}

/**
 * [playerId]'s ships in port at [good]'s system and their warehouse there, if any (trading happens from them, R23, R42,
 * R49), each with what it holds of the good and its free cargo space for it (unlimited for CHIPS and FOOD). The
 * warehouse comes last and is marked `warehouse: true`.
 */
function shipsInPort(game, playerId, good) {
	const here = (game.players.find(p => p.id === playerId)?.ships ?? [])
		.filter(ship => ship.system === good.system && ship.location === 'PORT')
		.map(ship => ship.name);
	const ships = (game.you.ships ?? []).filter(ship => here.includes(ship.name)).map(ship => ({
		name: ship.name,
		label: shipLabel(ship),
		holding: ship.cargo?.[good.goods] ?? 0,
		free: TAKES_CARGO_SPACE.has(good.goods) ? Math.max(0, ship.cargoSpaces - ship.cargoUsed) : Infinity,
	}));
	const warehouse = (game.you.warehouses ?? []).find(candidate => candidate.system === good.system);
	return warehouse === undefined ? ships : [...ships, {
		name: 'Warehouse',
		warehouse: true,
		label: warehouseLabel(warehouse),
		holding: warehouse.cargo?.[good.goods] ?? 0,
		free: TAKES_CARGO_SPACE.has(good.goods) ? Math.max(0, warehouse.cargoSpaces - warehouse.cargoUsed) : Infinity,
	}];
}

/** What the form calls the places a sell is capped by, in its "nothing to sell" message (R10, R49). */
function sellSources(ships) {
	return ships.some(place => place.warehouse) ? 'ships in port or warehouse' : 'ships in port';
}

/** The legend over the places the amount splits across: "Your ships in port at Tau Ceti", plus "and warehouse" (R49). */
function shipsLegend(ships, good) {
	return ships.some(place => place.warehouse)
		? `Your ships in port and warehouse at ${good.systemName}`
		: `Your ships in port at ${good.systemName}`;
}

/**
 * E05-S04: the amount form for [playerId] when the game waits for their amount, else null. [max] is their own max after
 * money and cargo (R12, R10); [ships] are their ships in port at that system (trading happens in port, R23, R42), each
 * with what it holds of the good and its free cargo space for it (unlimited for CHIPS and FOOD). With a max of 0,
 * [cannot] says why, and the player acknowledges by trading nothing (S04-5); otherwise it's "".
 */
export function tradeForm(game, playerId) {
	const trade = awaitingTrade(game);
	const max = game.you?.tradeMax;
	if (trade === null || trade.playerId !== playerId || max === null || max === undefined) {
		return null;
	}
	const { good } = resolution(game);
	const ships = shipsInPort(game, playerId, good);
	const action = verb(trade.type);
	return {
		key: `${game.id}/${game.turn.number}/${good.system}/${good.goods}/${playerId}`,
		type: trade.type,
		goods: good.goods,
		systemName: good.systemName,
		price: trade.price,
		max,
		ships,
		heading: max > 0 ? `Your ${action} at ${trade.price} HT can trade now` : `The market has reached your ${action} at ${trade.price} HT`,
		amountLabel: `Amount to ${action} (u)`,
		help: `${trade.type === 'SELL' ? 'You get' : 'You pay'} your offer price, ${trade.price} HT each. Trading more leaves less for others.`,
		shipsLegend: shipsLegend(ships, good),
		noShip: ships.length === 0 ? `You have no ship in port at ${good.systemName}.` : '',
		cannot: max > 0 ? '' : trade.type === 'SELL'
			? `You have no ${good.goods} in your ${sellSources(ships)} at ${good.systemName}, so you can't sell any.`
			: `You can't afford any units at ${trade.price} HT.`,
	};
}

/**
 * E05-S05-4: the wholesale form for [playerId] when the wholesale step waits for them, else null; shaped like [tradeForm]
 * so the same split, warning and checks apply. [type] is the Buy or Sell they chose; null picks one they can trade (Buy
 * first). [max] is theirs for that type, after money and cargo, at most 5 u (R37, R12, R10); with 0, [cannot] says why,
 * and they can still pass.
 */
export function wholesaleForm(game, playerId, type) {
	const maxes = game.you?.wholesaleMax;
	if (awaitingWholesaler(game) !== playerId || !maxes) {
		return null;
	}
	const chosen = type ?? (maxes.buy > 0 || maxes.sell === 0 ? 'BUY' : 'SELL');
	const max = chosen === 'SELL' ? maxes.sell : maxes.buy;
	const current = resolution(game);
	const { good } = current;
	const price = current.newPrice;
	const ships = shipsInPort(game, playerId, good);
	return {
		key: `${game.id}/${game.turn.number}/${good.system}/${good.goods}/${playerId}/wholesale/${chosen}`,
		wholesale: true,
		type: chosen,
		goods: good.goods,
		systemName: good.systemName,
		price,
		max,
		ships,
		heading: `As ${wholesaleRole(game, playerId)}, buy or sell up to 5 u of ${good.goods} at the new price, ${price} HT?`,
		amountLabel: `Amount to ${verb(chosen)} (u)`,
		help: `${chosen === 'SELL' ? 'You get' : 'You pay'} ${price} HT each, from 1 to ${max}. ${WHOLESALE_HELP}`,
		shipsLegend: shipsLegend(ships, good),
		noShip: ships.length === 0 ? `You have no ship in port at ${good.systemName}.` : '',
		cannot: max > 0 ? '' : chosen === 'SELL'
			? `You have no ${good.goods} in your ${sellSources(ships)} at ${good.systemName}, so you can't sell any.`
			: `You can't afford any units at ${price} HT.`,
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
		? `You have no ship in port at ${form.systemName}, so everything you buy will be lost.`
		: `Only ${amount - lost} u fit in the ${placesWord(form)} you chose; the rest will be lost.`;
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
		return { error: `Enter a whole number of units, 0 or more, for each ${hasWarehouse(form) ? 'ship and your warehouse' : 'ship'}.`, field: bad };
	}
	const short = form.type === 'SELL' ? form.ships.findIndex((ship, index) => split[index] > ship.holding) : -1;
	if (short >= 0) {
		const place = form.ships[short];
		return { error: `${place.warehouse ? 'Your warehouse' : place.name} has only ${place.holding} ${form.goods}.`, field: short };
	}
	if (form.ships.length > 0 && split.reduce((sum, units) => sum + units, 0) !== amount) {
		return { error: `Your ${hasWarehouse(form) ? "ships' and warehouse's" : "ships'"} units must add up to ${amount}.`, field: 0 };
	}
	const body = {
		amount,
		ships: form.ships.flatMap((place, index) => place.warehouse ? [] : [{ name: place.name, units: split[index] }]),
	};
	const warehouse = form.ships.findIndex(place => place.warehouse);
	return { body: warehouse < 0 ? body : { ...body, warehouse: split[warehouse] } };
}

/** S02-2: the Dictator form's question, for the Dictator asked; "" for everyone else. */
/**
 * The market price of the good resolving, from the board, so whoever is asked to decide sees it (creator, 2026-11-20);
 * null when the board doesn't list it.
 */
export function resolvingPrice(game) {
	const good = resolution(game)?.good;
	const system = good ? (game.board ?? []).find(entry => entry.id === good.system) : null;
	return system?.market?.find(entry => entry.goods === good.goods)?.price ?? null;
}

/** " (market price 12 HT)" after the good a decision is about, or nothing when the price isn't known. */
export function priceNote(game) {
	const price = resolvingPrice(game);
	return price === null ? '' : ` (market price ${price} HT)`;
}

export function dictatorQuestion(game, playerId) {
	return awaitingDictator(game) === playerId ? `You're Dictator of ${resolution(game).good.goods} here${priceNote(game)}. Place an offer before the roll?` : '';
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
 * S01-9: the decision the game waits on [playerId] for, as the alert's text, or "" when there's none: the Dictator's offer
 * (E05-S02), their amount (E05-S04), the wholesale step (E05-S05) or resolving the next good.
 */
export function decisionText(game, playerId) {
	if (awaitingDictator(game) === playerId) {
		const { good } = resolution(game);
		return `Your decision is needed: you're Dictator of ${good.goods} at ${good.systemName}${priceNote(game)}. Place an offer or pass.`;
	}
	if (awaitingTrade(game)?.playerId === playerId) {
		const { good } = resolution(game);
		const type = awaitingTrade(game).type;
		return `Your decision is needed: the market has reached your ${verb(type)} of ${good.goods} at ${good.systemName} (${canTradeText(game, type)}).`;
	}
	if (awaitingWholesaler(game) === playerId) {
		const { good, newPrice } = resolution(game);
		return `Your decision is needed: as ${wholesaleRole(game, playerId)}, buy or sell up to 5 u of ${good.goods} at ${good.systemName} at ${newPrice} HT, or pass.`;
	}
	return resolver(game) === playerId ? 'Your decision is needed: resolve the next good.' : '';
}
