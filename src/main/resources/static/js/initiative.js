/*
 * Pure helpers for the initiative bid (E03-S05) on the Deck (s01) and the initiative order in the top bar. They read
 * the game shape from GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6): the caller's own bid is `you.bid` and the
 * order is `turn.initiative` (emails); nobody else's bid or money is ever sent (R29).
 */

import { isBidding } from './offers.js';

export const BID_WHOLE = 'Enter a whole number of HT, 0 or more.';
export const BID_NEGATIVE = "A bid can't be negative. Enter 0 to bid nothing.";
/** S03-7's wording, for a bid that reached the server after Bidding closed. */
export const BID_CLOSED_TEXT = "Bidding has closed — your bid wasn't placed.";

/** S05-1's error when the bid is more than the player has. */
export function overMoneyText(money) {
	return `You have ${money} HT. Enter ${money} or less.`;
}

/** S05-1: a whole number of HT from 0 up to [money], or the error to show next to the field (the server's words). */
export function validateBid(raw, money) {
	const text = String(raw ?? '').trim();
	if (/^-\d+$/.test(text)) {
		return { error: BID_NEGATIVE };
	}
	if (!/^\d+$/.test(text)) {
		return { error: BID_WHOLE };
	}
	const amount = Number(text);
	return amount <= money ? { amount } : { error: overMoneyText(money) };
}

/** S05-2: the line in "Your offers this turn"; once Bidding has closed, the bid has been paid. */
export function bidLine(game) {
	const bid = game.you?.bid;
	if (bid === null || bid === undefined) {
		return null;
	}
	return isBidding(game)
		? `Initiative bid: ${bid} HT (paid when Bidding closes, win or lose)`
		: `Initiative bid: ${bid} HT (paid)`;
}

/** S05-2: the button reads "Change bid" once a bid is placed. */
export function bidButtonLabel(game) {
	return game.you?.bid === null || game.you?.bid === undefined ? 'Place bid' : 'Change bid';
}

/** What the status line says after a bid is accepted. */
export function bidPlacedText(amount) {
	return `Initiative bid placed: ${amount} HT.`;
}

/** S05-5: "Initiative: Vega Lines → Orion Freight" from Ship-jumps on; null while Bidding is open or before it. */
export function initiativeText(game) {
	const order = game.turn?.initiative ?? [];
	if (isBidding(game) || order.length === 0) {
		return null;
	}
	const names = order.map(email => game.players.find(p => p.email === email)?.corporationName ?? email);
	return `Initiative: ${names.join(' → ')}`;
}
