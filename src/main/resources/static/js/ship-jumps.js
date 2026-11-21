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

/** Whether players are jumping (or have all jumped) in this turn's Jumps phase; the order was set in Initiative. */
function orderIsSet(game) {
	return game.status === 'RUNNING' && game.turn?.phase === 'JUMPS' && places(game).length > 0;
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

/** S02-1, S02-9: the top bar's addition once the order is set, "" before and outside Jumps. */
export function jumpingText(game, playerId) {
	if (!orderIsSet(game)) {
		return '';
	}
	// E10: the jumps wait while an interception is decided or a battle lasts.
	if (game.turn.battle) {
		return `battle at ${game.turn.battle.systemName}`;
	}
	if (game.turn.interception) {
		return `interception at ${game.turn.interception.systemName}`;
	}
	const who = jumper(game);
	if (who === null) {
		return 'all jumps done';
	}
	return who === playerId ? 'your jumps' : `${corporation(game, who)} is jumping`;
}

export const GO_TO_DECK = 'Go to the Command deck';

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
	if (game.turn?.interception || game.turn?.battle) {
		return ''; // the jumps wait for the fight; its own alert says what's needed (E10)
	}
	return jumper(game) === playerId ? "Your decision is needed: it's your turn to jump ships." : '';
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
	// R73: while an interception is decided or a battle lasts, nobody jumps.
	if (jumper(game) !== playerId || game.turn.interception || game.turn.battle) {
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

/**
 * R68: the chance that [shipName], one of the viewer's own ships (`you.ships[].jumpChances`), jumps to [destination]:
 * "Chance of success: 58% (2d6 roll of 7 or less)", or "" with no ship, for its own system or for someone else's ship.
 */
export function jumpChanceText(game, shipName, destination) {
	const here = game.players?.flatMap(p => p.ships).find(ship => ship.name === shipName)?.system;
	if (here && here === destination && game.you?.ships?.some(ship => ship.name === shipName)) {
		return 'Moves into Space here: always succeeds.';
	}
	const chance = game.you?.ships?.find(ship => ship.name === shipName)?.jumpChances?.[destination];
	if (!chance) {
		return '';
	}
	return chance.percent >= 100
		? 'Chance of success: 100%'
		: `Chance of success: ${chance.percent}% (2d6 roll of ${chance.target} or less)`;
}

/**
 * R68: after jumping [shipName], what the game answered says whether the jump failed: the ship jumped but isn't waiting
 * to land. Then "Wayfarer's jump failed: it stays at Gamma Leporis."; "" when it made the jump.
 */
export function failedJumpText(game, shipName) {
	const turn = game.turn ?? {};
	if (turn.landingShip === shipName || !(turn.jumpedShips ?? []).includes(shipName)) {
		return '';
	}
	const ship = game.players.flatMap(p => p.ships).find(s => s.name === shipName);
	return ship ? `${shipName}'s jump failed: it stays at ${ship.systemName}.` : '';
}
