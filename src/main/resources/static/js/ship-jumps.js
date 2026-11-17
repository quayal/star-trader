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
	return places(game).find(playerId => playerId !== null && !done.includes(playerId)) ?? null;
}

function corporation(game, playerId) {
	return game.players.find(p => p.id === playerId)?.corporationName ?? playerId;
}

/** S02-1, S02-9: the top bar's addition once the order is set, "" before and outside Ship-jumps. */
export function jumpingText(game, playerId) {
	if (!orderIsSet(game)) {
		return '';
	}
	const who = jumper(game);
	if (who === null) {
		return 'all jumps done';
	}
	return who === playerId ? 'your jumps' : `${corporation(game, who)} is jumping`;
}

export const GO_TO_DECK = 'Go to the Deck';

/**
 * The step on the Deck the game waits on [playerId] for, as the decision alert's text on other screens, or "" when there's
 * none: their pick of a place in the turn order (E04-S01), or their jump slot (E04-S02). A ship waiting to land needs
 * no alert: its dialog opens on every screen (E04-S03).
 */
export function deckDecisionText(game, playerId) {
	if (!playerId) {
		return '';
	}
	if (picker(game) === playerId) {
		return 'Your decision is needed: choose your place in the turn order.';
	}
	return jumper(game) === playerId ? "Your decision is needed: it's your turn to jump your ships, or to finish jumping." : '';
}

/** S02-1: per turn-order place, "jumping now", "done" or "" (also "" for every place before the order is set). */
export function slotStatuses(game) {
	if (!orderIsSet(game)) {
		return places(game).map(() => '');
	}
	const who = jumper(game);
	const done = game.turn.doneJumping ?? [];
	return places(game).map(playerId => (playerId === who ? JUMPING_NOW : done.includes(playerId) ? SLOT_DONE : ''));
}

/**
 * S02-2: the ships the player with [playerId] may still jump during their slot, with where each is now; [] when it
 * isn't their slot.
 */
export function shipsToJump(game, playerId) {
	if (jumper(game) !== playerId) {
		return [];
	}
	const jumped = game.turn.jumpedShips ?? [];
	const ships = game.players.find(p => p.id === playerId)?.ships ?? [];
	// R64: a ship still in the shipyard doesn't jump.
	return ships.filter(ship => !jumped.includes(ship.name) && ship.location !== 'SHIPYARD');
}

/** S02-2: "Wayfarer (at Tau Ceti)" in the ship list. */
export function shipOptionText(ship) {
	return `${ship.name} (at ${ship.systemName})`;
}

/** S02-4: the inline error for a jump to the system the ship is already at, or "" when the jump may go. */
export function validateJump(ship, destination) {
	return ship && ship.system === destination ? `${ship.name} is already at ${ship.systemName}. Pick another destination.` : '';
}
