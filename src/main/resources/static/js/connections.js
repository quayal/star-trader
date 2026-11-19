/*
 * Pure helpers for connections (E12): placing the starting points in turn 1's Bidding on the Deck (R82), raising one
 * in Investment on Infrastructure (R83), and the factory cap there (R84). A player's `connections` and unplaced
 * `connectionPoints` are public in `players[]` (R81); what a raise costs and the factory room are the viewer's own
 * `you.invest`. The server decides every one of these; the checks here only say it sooner.
 */

import { isBidding } from './offers.js';
import { canBuy } from './investment.js';

/** The three kinds in the order every screen lists them. */
export const KINDS = [
	{ id: 'political', kind: 'POLITICAL', name: 'Political' },
	{ id: 'business', kind: 'BUSINESS', name: 'Business' },
	{ id: 'criminal', kind: 'CRIMINAL', name: 'Criminal' },
];

export const PLACE_BUTTON = 'Place connections';
export const PLACE_WHOLE_NUMBER = 'Enter a whole number from 0 to 10 for each connection.';
/** E12 edge case: putting everything into criminal is allowed, so the form says what it costs. */
export const CAP_NOTE = 'Factories are capped at political + business.';
export const RAISED_TEXT = "You've raised a connection this Investment.";

/** The viewer's public entry in `players[]`, or undefined for someone who isn't playing. */
function viewer(game) {
	return (game.players ?? []).find(player => player.id === game.you?.id);
}

/** The starting points the viewer still has to place (R82): 0 once placed, and for anyone not playing. */
export function pointsToPlace(game) {
	return viewer(game)?.connectionPoints ?? 0;
}

/** Whether the Deck shows the placement form: Bidding, with points still to place (E12-S01-3). */
export function isPlacing(game) {
	return isBidding(game) && pointsToPlace(game) > 0;
}

/** "You have 5 points to place: …", the placement form's hint. */
export function placementHint(points) {
	return `You have ${points} ${points === 1 ? 'point' : 'points'} to place, each connection from 0 to 10. The placement is final, ` +
		"and you can't signal done bidding until you've made it.";
}

/** A connection level typed, or null when it isn't a whole number from 0 to 10. Blank reads as 0. */
export function parseLevel(text) {
	const trimmed = String(text ?? '').trim();
	if (trimmed === '') {
		return 0;
	}
	if (!/^\d+$/.test(trimmed)) {
		return null;
	}
	const level = Number(trimmed);
	return level <= 10 ? level : null;
}

/** "3 of 5 points placed", as the levels are typed; "" while one isn't a level. */
export function placementTotalText(values, points) {
	const levels = KINDS.map(({ id }) => parseLevel(values[id]));
	if (levels.includes(null)) {
		return '';
	}
	return `${levels.reduce((sum, level) => sum + level, 0)} of ${points} points placed`;
}

/** The placement to send, or the error the server would give: a level that isn't 0–10, or a total other than [points]. */
export function validatePlacement(values, points) {
	const placement = Object.fromEntries(KINDS.map(({ id }) => [id, parseLevel(values[id])]));
	if (Object.values(placement).includes(null)) {
		return { error: PLACE_WHOLE_NUMBER };
	}
	if (Object.values(placement).reduce((sum, level) => sum + level, 0) !== points) {
		return { error: `Your connections must add up to ${points} points.` };
	}
	return { placement };
}

/** Whether Infrastructure shows the raise form: the viewer may buy and hasn't raised one this Investment (R83). */
export function canRaise(game) {
	return canBuy(game) && !game.you.invest.raisedConnection;
}

/**
 * The factory cap on Infrastructure (E12-S03-8): `{ text, room, refusal }`, the text "Factory units: 5 of 7 (political
 * 3 + business 4)", the units the viewer may still add, and the server's refusal for going over. Null outside Investment.
 */
export function factoryCap(game) {
	const invest = game.you?.invest;
	if (!invest) {
		return null;
	}
	const political = viewer(game)?.connections?.political ?? 0;
	const business = viewer(game)?.connections?.business ?? 0;
	return {
		text: `Factory units: ${invest.factoryUnits} of ${invest.factoryCap} (political ${political} + business ${business})`,
		room: Math.max(0, invest.factoryCap - invest.factoryUnits),
		refusal: `You can only own ${invest.factoryCap} units of factory (POL: ${political}, BUSS: ${business}).`,
	};
}

/** E12-S03-8: the most units the viewer can add of [factory]: the smaller of what they can afford and what the cap allows. */
export function maxFactoryUnits(game, factory) {
	const cap = factoryCap(game);
	if (!cap || !factory) {
		return 0;
	}
	return Math.max(0, Math.min(Math.floor((game.you.money ?? 0) / factory.unitCost), cap.room));
}

/**
 * The raise buttons on the viewer's own Corporations card (creator, 2026-11-19): null when they can't buy now, else
 * `{ raised, buttons }`. Once a connection was raised this Investment, `raised` is [RAISED_TEXT] and there are no
 * buttons. Otherwise one `{ kind, label, disabled, reason }` per connection, "Raise Political to 4 — 40 HT", disabled
 * with the reason at 10 or when the money doesn't cover it (R83).
 */
export function raiseButtons(game) {
	if (!canBuy(game)) {
		return null;
	}
	if (!canRaise(game)) {
		return { raised: RAISED_TEXT, buttons: [] };
	}
	const money = game.you.money ?? 0;
	const buttons = (game.you.invest.connections ?? []).map(entry => {
		const name = KINDS.find(kind => kind.kind === entry.kind)?.name ?? entry.name;
		if (entry.cost == null) {
			return { kind: entry.kind, label: `Raise ${name}`, disabled: true, reason: `${name} is at 10, the most there is.` };
		}
		const label = `Raise ${name} to ${entry.level + 1} — ${entry.cost} HT`;
		return entry.cost > money
			? { kind: entry.kind, label, disabled: true, reason: `You have ${money} HT.` }
			: { kind: entry.kind, label, disabled: false, reason: '' };
	});
	return { raised: '', buttons };
}
