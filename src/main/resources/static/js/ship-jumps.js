/*
 * Pure helpers for jumping ships in turn order (E04-S02) on the Deck (s01) and in the top bar. They read
 * `turn.turnOrder`, `turn.jumpedShips` and `turn.doneJumping` and the public `players[].ships` from GET /api/games/{id}
 * (BACKWARD_COMPATIBILITY.md §6). Everything here is public (R26); no cargo is needed or shown (R29).
 */

import { picker } from './turn-order.js';

export const JUMP_TITLE = 'Your next jump';
export const JUMP_BUTTON = 'Jump';
export const FINISH_BUTTON = 'Finish jumping';
export const JUMPS_DONE_TEXT = 'Your jumps are done.';
export const JUMPING_NOW = 'jumping now';
export const SLOT_DONE = 'done';

function places(game) {
	return game.turn?.turnOrder ?? [];
}

/** Whether the turn order is set and players are jumping (or have all jumped) in this Ship-jumps phase. */
function orderIsSet(game) {
	return game.status === 'RUNNING' && game.turn?.phase === 'SHIP_JUMPS' && places(game).length > 0 && picker(game) === null;
}

/** Whose jump slot it is (R26): the first player in turn order whose slot hasn't ended, or null. */
export function jumper(game) {
	if (!orderIsSet(game)) {
		return null;
	}
	const done = game.turn.doneJumping ?? [];
	return places(game).find(email => email !== null && !done.includes(email)) ?? null;
}

function corporation(game, email) {
	return game.players.find(p => p.email === email)?.corporationName ?? email;
}

/** S02-1, S02-9: the top bar's addition once the order is set, "" before and outside Ship-jumps. */
export function jumpingText(game, email) {
	if (!orderIsSet(game)) {
		return '';
	}
	const who = jumper(game);
	if (who === null) {
		return 'all jumps done';
	}
	return who === email ? 'your jumps' : `${corporation(game, who)} is jumping`;
}

/** S02-1: per turn-order place, "jumping now", "done" or "" (also "" for every place before the order is set). */
export function slotStatuses(game) {
	if (!orderIsSet(game)) {
		return places(game).map(() => '');
	}
	const who = jumper(game);
	const done = game.turn.doneJumping ?? [];
	return places(game).map(email => (email === who ? JUMPING_NOW : done.includes(email) ? SLOT_DONE : ''));
}

/**
 * S02-2: the ships the player with [email] may still jump during their slot, with where each is now; [] when it
 * isn't their slot.
 */
export function shipsToJump(game, email) {
	if (jumper(game) !== email) {
		return [];
	}
	const jumped = game.turn.jumpedShips ?? [];
	const ships = game.players.find(p => p.email === email)?.ships ?? [];
	return ships.filter(ship => !jumped.includes(ship.name));
}

/** S02-2: "Wayfarer (at Tau Ceti)" in the ship list. */
export function shipOptionText(ship) {
	return `${ship.name} (at ${ship.systemName})`;
}

/** S02-4: the inline error for a jump to the system the ship is already at, or "" when the jump may go. */
export function validateJump(ship, destination) {
	return ship && ship.system === destination ? `${ship.name} is already at ${ship.systemName}. Pick another destination.` : '';
}
