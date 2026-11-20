/*
 * Pure helpers for notifications (creator, 2026-11-18): pop-up cards for interceptions and battles, a your-move card
 * when the game starts waiting on the viewer, and fading toasts for every other new public line. Cards read
 * `log[].notice` ({ kind, system, systemName, playerIds, round, interception }), never the log's wording; toasts show
 * the log's own lines.
 */

import { battleStep, interceptors } from './battle.js';
import { deckLink, tradingLink } from './game.js';
import { jumper } from './ship-jumps.js';
import { awaitingDictator, awaitingTrade, awaitingWholesaler, priceNote, resolution } from './trading.js';
import { picker } from './turn-order.js';

/** At most this many toasts show at once; the rest are counted. */
export const MAX_TOASTS = 3;
/** How long a toast stays, in ms. */
export const TOAST_MS = 6000;

function corporation(game, playerId) {
	return game.players.find(player => player.id === playerId)?.corporationName ?? playerId;
}

/** Which card a noticed entry belongs to: a try with its reputation line, a round with all its lines, else its own. */
function groupKey(notice, index) {
	if (notice.kind === 'INTERCEPTION') {
		return `I/${notice.interception?.interceptorShip}/${notice.interception?.arrivingShip}/${notice.system}`;
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

/** A card's heading and opening line for [notice]; [texts] are its log lines. */
function cardFor(game, notice, texts, myId) {
	const where = notice.systemName;
	switch (notice.kind) {
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
	case 'SABOTAGE':
		return { title: `Sabotage at ${where}`, lead: 'One of your assets was sabotaged. See Infrastructure.', tone: 'alert', lines: texts };
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
		.map(group => ({
			key: `${game.id}/log/${group.start}`,
			end: group.end,
			evaded: group.notice.kind === 'INTERCEPTION' && group.notice.interception.arrivingId === myId && !group.notice.interception.success,
			...cardFor(game, group.notice, group.texts, myId),
		}));
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
		return { key: `${base}/jump`, title: 'Your move: jump', lead: 'It’s your turn to jump ships.', link: deck, focus: 'jump-title' };
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
export function toasts(log, from) {
	const lines = (log ?? []).slice(Math.max(0, from)).filter(entry => !entry.notice).map(entry => entry.text);
	return { shown: lines.slice(-MAX_TOASTS), more: Math.max(0, lines.length - MAX_TOASTS) };
}
