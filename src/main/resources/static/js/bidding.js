/*
 * Pure helpers for signalling done during Bidding (E03-S04) in the top bar of the Deck (s01) and System details
 * (s02). They read the game shape from GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6): `turn.doneBidding` is
 * the public list of players' ids who signalled done (D09); nothing private is needed.
 */

import { isNews, waitingForNews } from './news.js';
import { isBidding, isClosedRefusal } from './offers.js';
import { turnText } from './game.js';
import { pickingText } from './turn-order.js';
import { jumpingText } from './ship-jumps.js';
import { resolvedText } from './trading.js';
import { waitingForInvesting } from './investment.js';

export const DONE_BUTTON = "I'm done bidding";
/** S04-1: shown in place of the button once the player has signalled done. */
export const DONE_TEXT = 'You signalled done — you can still add offers';
export const CLOSED_BY_YOU = 'Everyone is done. Bidding has closed.';
export const ALREADY_CLOSED = 'Bidding has already closed.';

/** Whether the player with [playerId] has signalled done this Bidding. */
export function isDone(game, playerId) {
	return isBidding(game) && game.turn.doneBidding.includes(playerId);
}

/**
 * Who the game is still waiting for during Bidding (S04-3, D09): "you" first while the viewer isn't done, then the
 * other players' corporation names in join order. Empty outside Bidding.
 */
export function waitingFor(game, playerId) {
	if (!isBidding(game)) {
		return [];
	}
	const pending = game.players.filter(p => !game.turn.doneBidding.includes(p.id));
	const you = pending.some(p => p.id === playerId) ? ['you'] : [];
	return [...you, ...pending.filter(p => p.id !== playerId).map(p => p.corporationName)];
}

/**
 * The top bar heading: "Turn 4 of 20 · Bidding — waiting for you, Vega Lines" during Bidding, "… · Ship-jumps — Vega
 * Lines is choosing their place in the turn order" while players pick (E04-S01), "… — Vega Lines is jumping" / "— your
 * jumps" once the order is set (E04-S02), "… · Trading — 3 of 18 resolved" during Trading (E05-S01), "… · Investment —
 * waiting for you, Vega Lines" during Investment (E06-S01), else turnText.
 */
export function phaseText(game, playerId) {
	const waiting = isBidding(game) ? waitingFor(game, playerId) : isNews(game) ? waitingForNews(game, playerId) : waitingForInvesting(game, playerId);
	if (waiting.length) {
		return `${turnText(game)} — waiting for ${waiting.join(', ')}`;
	}
	const step = pickingText(game, playerId) || jumpingText(game, playerId) || resolvedText(game);
	return step ? `${turnText(game)} — ${step}` : turnText(game);
}

/**
 * What to say once "done" was accepted: nothing while others still bid (the done text says it), or that this
 * player's signal closed Bidding (S04-4).
 */
export function doneAcceptedText(game) {
	return isBidding(game) ? '' : CLOSED_BY_YOU;
}

/** What to say when "done" was refused: a close that raced the click isn't the player's error (A04). */
export function doneRefusedText(status, message, fallback) {
	return isClosedRefusal(status, message) ? ALREADY_CLOSED : message || fallback;
}
