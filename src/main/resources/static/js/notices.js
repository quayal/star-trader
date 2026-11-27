/*
 * Pure helpers for notifications (creator, 2026-11-18): pop-up cards for interceptions and battles, a your-move card
 * when the game starts waiting on the viewer, and fading toasts for every other new public line. Cards read
 * `log[].notice` ({ kind, system, systemName, playerIds, round, interception }), never the log's wording; toasts show
 * the log's own lines.
 */

import { battleStep, interceptors } from './battle.js';
import { deckLink, jumpsLink, tradingLink } from './game.js';
import { jumper } from './ship-jumps.js';
import { awaitingDictator, awaitingTrade, awaitingWholesaler, priceNote, resolution } from './trading.js';
import { picker } from './turn-order.js';

/** At most this many toasts show at once; the rest are counted. */
export const MAX_TOASTS = 3;
/** How long a toast stays, in ms. */
export const TOAST_MS = 6000;

/**
 * A challenge of a market position (R126, creator 2026-11-23): both players get a card, worded for the challenger (the first
 * of the notice's players) or the target, in the tone of what it meant to them.
 */
function challengeCard(succeeded) {
	return (where, notice, myId, game) => {
		const [challengerId, targetId] = notice.playerIds;
		const challenger = myId === challengerId;
		const other = corporation(game, challenger ? targetId : challengerId);
		if (succeeded) {
			return challenger
				? { title: 'Your challenge succeeded', lead: `${other} dropped a tier of their market position at ${where}.`, tone: 'rise' }
				: { title: 'You lost a market position', lead: `${other} challenged your market position at ${where} and won.`, tone: 'alert' };
		}
		return challenger
			? { title: 'Your challenge failed', lead: `${other} kept their market position at ${where}.`, tone: 'alert' }
			: { title: 'You held your market position', lead: `${other} challenged your market position at ${where} and failed.`, tone: 'rise' };
	};
}

/**
 * A position roll that rose its trader a tier (R43, creator 2026-11-24): the trader's card is green, and the Dictators it
 * replaced get a red one. The notice's first player is the trader, the rest the demoted.
 */
function positionGainedCard(where, notice, myId) {
	return notice.playerIds[0] === myId
		? { title: 'You gained a market position', lead: `Your position roll at ${where} moved you up a tier.`, tone: 'rise' }
		: { title: 'You lost a market position', lead: `Another trader's position roll at ${where} took your Dictator place.`, tone: 'alert' };
}

/**
 * R124 (creator, 2026-11-23): a roll that went against a player gets them a card of its own; everyone else sees the
 * line as a toast. Each kind's card, worded for the player it happened to (alert unless it says otherwise), given the
 * place, the notice, the viewer and the game.
 */
const SETBACKS = {
	SHIP_SEIZED: where => ({ title: 'Your ship was seized', lead: `Planet security at ${where} confiscated your ship and everything aboard.` }),
	LANDING_FORCED: where => ({ title: 'Your landing failed', lead: `Planet security at ${where} turned your ship away from the Planet.` }),
	POLICE_CONTROL: where => ({ title: 'Police caught your ship', lead: `Police in Space at ${where} stopped your ship; you lose reputation.` }),
	JUMP_FAILED: () => ({ title: 'Your jump failed', lead: 'Your ship stays where it was; its jump is used for this turn.' }),
	EXPEDITION_LOST: () => ({ title: 'Your expedition was lost', lead: 'The expedition rolled 1: the ship is gone.' }),
	LOST_TO_PIRATES: where => ({ title: 'Your ship was lost to pirates', lead: `Pirates at ${where} took your ship and its cargo.` }),
	NO_PROMOTION: where => ({ title: 'No market position gained', lead: `Your position roll at ${where} rolled too high.` }),
	DEMOTED: where => ({ title: 'You lost a market position', lead: `Another trader's position roll at ${where} took your Dictator place.` }),
	CHALLENGE_SUCCEEDED: challengeCard(true),
	CHALLENGE_FAILED: challengeCard(false),
	// R136 (creator, 2026-11-24): every penalty or loss gets its player a red card.
	PASSENGERS_UNDELIVERED: () => ({ title: 'Your passengers weren’t delivered', lead: 'Passenger groups you took didn’t reach their destination: each costs twice its fare.' }),
	CONTROL_CLEARED: () => ({ title: 'You passed the reputation control', lead: 'You were deemed innocent: your reputation is now 8.', tone: 'rise' }),
	CONTROL_FINED: () => ({ title: 'You were fined', lead: 'Your reputation control ended in a fine, paid from your cash and, where it fell short, your assets.' }),
	ELIMINATED: () => ({ title: 'You’re out of the game', lead: 'Your reputation control eliminated your corporation: everything it owned is gone. You can still follow the game.' }),
	BACKING_LOST: () => ({ title: 'Your backing cost you', lead: 'The corporation you backed in its reputation control was eliminated: you lose connections and reputation.' }),
	INTEREST_UNPAID: () => ({ title: 'Your loan interest went unpaid', lead: 'You lose reputation, and the interest is added to what you owe.' }),
	LOAN_SEIZED: () => ({ title: 'Your loan was collected', lead: 'Your cash didn’t cover the loan that fell due, so your assets were seized to pay it.' }),
	SABOTAGE_BLAMED: () => ({ title: 'You were blamed for sabotage', lead: 'Another corporation’s sabotage was pinned on you: you lose reputation.' }),
	SABOTAGE_TRACED: () => ({ title: 'Your sabotage was traced', lead: 'Your sabotage was traced back to you: you lose reputation.' }),
};

/**
 * What a penalty took from the viewer (R136), from their own ledger: the movements of [entry]'s turn with [notice]'s ledger
 * reason, as "You paid 60 HT."; '' when it names none or nothing was paid. The amount is private (R29), so it never is in the log.
 */
function paidNote(game, notice, entry) {
	if (!notice.ledgerReason || !entry) {
		return '';
	}
	const paid = (game.you?.ledger ?? [])
		.filter(movement => movement.turn === entry.turn && movement.reason === notice.ledgerReason)
		.reduce((sum, movement) => sum - movement.amount, 0);
	return paid > 0 ? ` You paid ${paid} HT.` : '';
}

/**
 * The seller's black-market card (R112, creator 2026-11-26): the roll and the result, from their own ledger line ("Black market
 * at Gamma Leporis: d6 4; 10 slaves at 15 HT each"), the [ordinal]-th sale there in [entry]'s turn. Both stay private (R29):
 * the log line says neither.
 */
function blackMarketCard(game, notice, entry, ordinal) {
	const prefix = `Black market at ${notice.systemName}: `;
	const sales = (game.you?.ledger ?? []).filter(movement => movement.turn === entry?.turn && movement.reason.startsWith(prefix)).reverse();
	const sale = sales[ordinal];
	if (!sale) {
		return { title: 'You sold on the black market', lead: `At ${notice.systemName}.`, tone: 'rise', lines: [] };
	}
	const [roll, result] = sale.reason.slice(prefix.length).split('; ');
	return {
		title: `Black market at ${notice.systemName}: you rolled ${roll.replace(/^d6 /, '')}`,
		lead: `${result.charAt(0).toUpperCase()}${result.slice(1)}: you received ${sale.amount} HT.`,
		tone: 'rise',
		lines: [],
	};
}

/** Whether [notice] is someone else's black-market sale: then [myId] gets its line as a toast; the card is the seller's. */
function someoneElsesSale(notice, myId) {
	return Boolean(notice && notice.kind === 'BLACK_MARKET_SOLD' && !notice.playerIds.includes(myId));
}

/** Whether [notice] is a position gained (R43) by someone other than [myId] or a Dictator they replaced: then [myId] gets its line as a toast. */
function someoneElsesGain(notice, myId) {
	return Boolean(notice && notice.kind === 'POSITION_GAINED' && !notice.playerIds.includes(myId));
}

/** Whether [notice] is a setback (R124) that happened to someone other than [myId]: then [myId] gets its line as a toast. */
function someoneElsesSetback(notice, myId) {
	return Boolean(notice && SETBACKS[notice.kind] && !notice.playerIds.includes(myId));
}

function corporation(game, playerId) {
	return game.players.find(player => player.id === playerId)?.corporationName ?? playerId;
}

/** Which card a noticed entry belongs to: a try with its reputation line, a round with all its lines, else its own. */
function groupKey(notice, index) {
	if (notice.kind === 'INTERCEPTION') {
		return `I/${notice.interception?.interceptorShip}/${notice.interception?.arrivingShip}/${notice.system}`;
	}
	if (SETBACKS[notice.kind]) {
		// The setback with its reputation loss (R124); another player's, though logged next, is a card of its own.
		return `${notice.kind}/${notice.system}/${notice.playerIds.join(',')}`;
	}
	if (notice.kind === 'NEWS_EVENT') {
		// An event's announcement with the lines of what it did (playtest, 2026-11-21); another event that hurt other players is
		// a card of its own, so each card is red for the right players (R136).
		return `NEWS_EVENT/${notice.playerIds.join(',')}`;
	}
	return notice.kind === 'BATTLE_ROUND' ? `R/${notice.system}/${notice.round}` : `${notice.kind}/${index}`;
}

/** An interception card, worded for the interceptor, the intercepted or a bystander. */
function interceptionCard(game, notice, myId) {
	const roll = notice.interception;
	const where = notice.systemName;
	const them = corporation(game, roll.interceptorId);
	const owner = corporation(game, roll.arrivingId);
	if (roll.interceptorId === myId) {
		return {
			title: roll.success ? 'Your interception succeeded' : 'Your interception failed',
			lead: roll.success
				? `Your ${roll.interceptorShip} caught ${owner}’s ${roll.arrivingShip} at ${where}. A battle starts.`
				: `${owner}’s ${roll.arrivingShip} evaded your ${roll.interceptorShip} at ${where}.`,
			tone: roll.success ? 'rise' : 'alert',
		};
	}
	if (roll.arrivingId === myId) {
		return {
			title: roll.success ? 'You were intercepted' : 'You evaded an interception',
			lead: `${them} tried to intercept your ${roll.arrivingShip} with ${roll.interceptorShip} at ${where}. `
				+ (roll.success ? 'They caught it: a battle starts.' : `${roll.arrivingShip} evaded them.`),
			tone: roll.success ? 'alert' : 'rise',
		};
	}
	return {
		title: `Interception at ${where}`,
		lead: `${them} tried to intercept ${owner}’s ${roll.arrivingShip} with ${roll.interceptorShip}. `
			+ (roll.success ? 'It was caught: a battle starts.' : `${roll.arrivingShip} evaded them.`),
		tone: null,
	};
}

/** A News announcement's line without its "News: " prefix, which the card's title says already. */
function newsLead(texts) {
	return texts.map(text => text.replace(/^News: (.)/, (_, first) => first.toUpperCase())).join(' ');
}

/**
 * A card's heading and opening line for [notice]; [texts] are its log lines, [entry] the group's first log entry, [ordinal]
 * how many cards of the same kind and place came before it in that turn.
 */
function cardFor(game, notice, texts, myId, entry, ordinal) {
	const where = notice.systemName;
	if (SETBACKS[notice.kind]) {
		const card = { tone: 'alert', ...SETBACKS[notice.kind](where, notice, myId, game) };
		return { ...card, lead: card.lead + paidNote(game, notice, entry), lines: texts };
	}
	switch (notice.kind) {
	case 'POSITION_GAINED':
		return { ...positionGainedCard(where, notice, myId), lines: texts };
	case 'BLACK_MARKET_SOLD':
		return blackMarketCard(game, notice, entry, ordinal);
	case 'INTERCEPTION': {
		const card = interceptionCard(game, notice, myId);
		return { ...card, lines: [`Rolls: ${notice.interception.interceptorRoll} vs ${notice.interception.arrivingRoll}`, ...texts.slice(1)] };
	}
	case 'BATTLE_STARTED':
		return {
			title: `Battle at ${where}`,
			lead: notice.playerIds.includes(myId) ? 'Your ships are in it.' : 'You’re not in it: you’ll be told how it goes.',
			tone: notice.playerIds.includes(myId) ? 'alert' : null,
			lines: texts,
		};
	case 'BATTLE_ROUND':
		return { title: `Battle at ${where} — round ${notice.round}`, lead: '', tone: null, lines: texts.slice(1) };
	case 'SHIP_DESTROYED':
		return { title: 'Ship destroyed', lead: '', tone: 'alert', lines: texts };
	case 'BATTLE_SUMMARY':
		// Playtest 2026-11-20: the whole battle in one card, by round, then the aftermath (reputation included).
		return { title: `Battle at ${where} — summary`, lead: '', tone: null, lines: [], sections: entry?.battleSummary ?? [] };
	case 'SABOTAGE':
		return { title: `Sabotage at ${where}`, lead: 'One of your assets was sabotaged. See Opportunity.', tone: 'alert', lines: texts };
	case 'NEWS_EVENT': {
		// Playtest 2026-11-21: what News announced, for everyone to acknowledge; red for the players it left worse off (R136).
		const hurt = notice.playerIds.includes(myId);
		return { title: hurt ? 'News — it cost you' : 'News', lead: newsLead(texts.slice(0, 1)), tone: hurt ? 'alert' : null, lines: texts.slice(1) };
	}
	case 'OPPORTUNITY_EXPIRED':
		return { title: 'An opportunity expired', lead: newsLead(texts), tone: null, lines: [] };
	default:
		return { title: `The battle at ${where} is over`, lead: '', tone: null, lines: [] };
	}
}

/**
 * The cards for [game]'s log entries from index [from] on, oldest first: `{ key, end, evaded, title, lead, tone, lines }`,
 * where [end] is the log length the card covers up to (what dismissing it marks as seen) and [evaded] says it tells
 * [myId] their arriving ship evaded an interception. A battle round is left out for the
 * players fighting in it, who get the battle report instead.
 */
export function noticeCards(game, from, myId) {
	const log = game.log ?? [];
	const groups = [];
	for (let index = Math.max(0, from); index < log.length; index += 1) {
		const notice = log[index].notice;
		if (!notice) {
			continue;
		}
		const key = groupKey(notice, index);
		const last = groups[groups.length - 1];
		if (last && last.key === key && last.end === index) {
			last.texts.push(log[index].text);
			last.end = index + 1;
		} else {
			groups.push({ key, start: index, end: index + 1, notice, texts: [log[index].text] });
		}
	}
	return groups
		.filter(group => !(group.notice.kind === 'BATTLE_ROUND' && group.notice.playerIds.includes(myId)))
		// R101: a sabotage's card is for its target alone; everyone else reads the line in the log.
		.filter(group => group.notice.kind !== 'SABOTAGE' || group.notice.playerIds.includes(myId))
		// R124: a setback's card is for the player it happened to; everyone else gets the line as a toast.
		.filter(group => !someoneElsesSetback(group.notice, myId))
		// A gained position's card is for the trader and the Dictators it replaced; everyone else reads the line in the log.
		.filter(group => group.notice.kind !== 'POSITION_GAINED' || group.notice.playerIds.includes(myId))
		// A black-market sale's card is for the seller; everyone else reads the line in the log (creator, 2026-11-26).
		.filter(group => !someoneElsesSale(group.notice, myId))
		.map(group => ({
			key: `${game.id}/log/${group.start}`,
			end: group.end,
			evaded: group.notice.kind === 'INTERCEPTION' && group.notice.interception.arrivingId === myId && !group.notice.interception.success,
			...cardFor(game, group.notice, group.texts, myId, log[group.start], sameBefore(log, group.start)),
		}));
}

/**
 * How many log lines before [start] carry the same kind of notice at the same place, for the same players, in the same turn:
 * the whole log, cards already seen included, so a second sale at a place reads the second ledger line.
 */
function sameBefore(log, start) {
	const { notice, turn } = log[start];
	return log.slice(0, start).filter(entry => entry.turn === turn && entry.notice?.kind === notice.kind
		&& entry.notice.system === notice.system && entry.notice.playerIds.join(',') === notice.playerIds.join(',')).length;
}

/**
 * Whether [cards] tell [myId]'s arriving ship evaded an interception: then that card shows before the landing dialog
 * asks where the ship ends up (creator, 2026-11-19).
 */
export function evasionFirst(cards) {
	return cards.some(card => card.evaded);
}

/**
 * The your-move card when the game waits on [myId] for a blocking step, else null: `{ key, title, lead, link, focus }`,
 * [focus] the id of the heading of the panel where it's done. The key names the step, so the card shows once per step. Resolving the next good isn't one: it repeats for every good.
 */
export function moveCard(game, myId) {
	if (game.status !== 'RUNNING' || !game.turn || !myId) {
		return null;
	}
	const base = `${game.id}/${game.turn.number}`;
	const deck = deckLink(game.id);
	const trading = tradingLink(game.id);
	if (interceptors(game).length) {
		const ship = game.turn.interception?.ship ?? '';
		return { key: `${base}/intercept/${ship}`, title: 'Your move: intercept?', lead: `${ship} has arrived where your ships wait in Space. Choose whether to try to intercept it.`, link: deck, focus: 'fight-title' };
	}
	const step = battleStep(game, myId);
	if (step) {
		const battle = game.turn.battle;
		const lead = {
			ORDERS: 'Give your ships their battle orders.',
			HITS: 'Your ships were hit. Place the damage.',
			LANDING: 'The battle is over. Choose where your ships end up.',
		}[step];
		return { key: `${base}/battle/${battle?.system}/${battle?.round}/${step}`, title: `Your move: battle at ${battle?.systemName ?? battle?.system}`, lead, link: deck, focus: 'fight-title' };
	}
	if (picker(game) === myId) {
		return { key: `${base}/pick`, title: 'Your move: turn order', lead: 'Choose your place in the turn order.', link: deck, focus: 'turn-order-title' };
	}
	if (!game.turn.interception && !game.turn.battle && jumper(game) === myId) {
		return { key: `${base}/jump`, title: 'Your move: jump', lead: 'It’s your turn to jump ships.', link: jumpsLink(game.id), focus: 'jumps-title' }; // R139: jumps are made on the Jumps screen
	}
	const good = resolution(game)?.good;
	const at = good ? `${good.goods} at ${good.systemName}` : '';
	const goodKey = good ? `${good.system}/${good.goods}` : '';
	if (awaitingDictator(game) === myId) {
		return { key: `${base}/dictator/${goodKey}`, title: 'Your move: Dictator', lead: `You’re Dictator of ${at}${priceNote(game)}. Place an offer or pass.`, link: trading, focus: 'trading-title' };
	}
	const trade = awaitingTrade(game);
	if (trade?.playerId === myId) {
		const verb = trade.type === 'SELL' ? 'sell' : 'buy';
		const max = game.you?.tradeMax ?? 0;
		const can = max > 0 ? `You can ${verb} ${max} u.` : `You can’t ${verb} any.`;
		return { key: `${base}/trade/${goodKey}`, title: 'Your move: trade', lead: `The market has reached your ${verb} of ${at} at ${trade.price} HT. ${can}`, link: trading, focus: 'trading-title' };
	}
	if (awaitingWholesaler(game) === myId) {
		return { key: `${base}/wholesale/${goodKey}`, title: 'Your move: wholesale', lead: `You may buy or sell up to 5 u of ${at} at the new price, or pass.`, link: trading, focus: 'trading-title' };
	}
	return null;
}

/** Where the cards start for a game: what this browser remembered, or, the first time it opens the game, the log's end. */
export function cardsFrom(remembered, logLength) {
	return Number.isInteger(remembered) && remembered >= 0 && remembered <= logLength ? remembered : logLength;
}

/**
 * The toasts for the log lines from index [from] on that have no card: the newest [MAX_TOASTS], newest last, and how
 * many more there were.
 */
export function toasts(log, from, myId) {
	const lines = (log ?? []).slice(Math.max(0, from)).filter(entry => !entry.notice || someoneElsesSetback(entry.notice, myId) || someoneElsesGain(entry.notice, myId) || someoneElsesSale(entry.notice, myId)).map(entry => entry.text);
	return { shown: lines.slice(-MAX_TOASTS), more: Math.max(0, lines.length - MAX_TOASTS) };
}

/**
 * One card per offer of the viewer's that resolved without trading (playtest, 2026-11-20), from `you.untradedOffers`, to
 * acknowledge: `{ key, title, lead, tone, lines }`. The key names the turn and the offer, so each shows once.
 */
export function untradedCards(game) {
	const turn = game.turn?.number;
	return (game.you?.untradedOffers ?? []).map(offer => ({
		key: `${game.id}/${turn}/untraded/${offer.system}/${offer.goods}/${offer.type}`,
		title: 'Your offer didn’t trade',
		lead: `Your ${offer.goods} ${offer.type === 'SELL' ? 'sell' : 'buy'} at ${offer.systemName} (${offer.price} HT) didn’t trade: another offer won, or the market roll left nothing for it.`,
		tone: null,
		lines: [],
	}));
}
