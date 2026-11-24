/*
 * Pure helpers for choosing the starting ship (R132): in turn 1's Bidding every player names their Clarinet and chooses the
 * Port it starts in, once, before they may signal done. They read `you.ships`, `you.invest.shipyard.freeNames` (the suggested
 * names nobody uses yet, R62) and `board` from GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6).
 */

import { isBidding } from './offers.js';

export const MAX_NAME_LENGTH = 20;
export const PLACE_SHIP_BUTTON = 'Start here';
export const PLACE_SHIP_HINT = 'Your corporation starts with one Clarinet: 3 Cargo, 1 Light weapons, 1 Passenger and 1 Safe jump module, ' +
	"crew class B already paid. Name it and choose the Port it starts in. It's final, and you can't signal done bidding until you have.";
export const NAME_LENGTH_TEXT = `Choose a name of 1 to ${MAX_NAME_LENGTH} characters.`;

/** Whether the Deck shows the starting ship form: turn 1's Bidding, for a player who has no ship yet. */
export function isChoosingShip(game) {
	return game.status === 'RUNNING' && game.turn?.number === 1 && isBidding(game) && Array.isArray(game.you?.ships) && game.you.ships.length === 0;
}

/** The first suggested name nobody uses yet, to start the name field with; '' when there is none. */
export function suggestedName(game) {
	return game.you?.invest?.shipyard?.freeNames?.[0] ?? '';
}

/** The Ports to choose from: every system, `{ id, name }`, in board order. */
export function portChoices(game) {
	return (game.board ?? []).map(system => ({ id: system.id, name: system.name }));
}

/** The ship to send, or the error the server would give for a name that is empty or too long. */
export function validateStartingShip(name, system) {
	const trimmed = String(name ?? '').trim();
	if (trimmed.length < 1 || trimmed.length > MAX_NAME_LENGTH) {
		return { error: NAME_LENGTH_TEXT };
	}
	if (!system) {
		return { error: 'Choose the system whose Port your ship starts in.' };
	}
	return { placement: { name: trimmed, system } };
}
