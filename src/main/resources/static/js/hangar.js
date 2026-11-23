/*
 * Pure helpers for the Parking Hangar dialog (R127): after a ship lands in a Port that has a hangar, or when a ship parked
 * last turn is still there as its owner ends their slot, the owner answers yes or no. They read `turn.hangarShip`, the public
 * `players[].ships[].hangar` and `rules.hangarFee` from GET /api/games/{id} and GET /api/rules (BACKWARD_COMPATIBILITY.md §6).
 */

import { jumper } from './ship-jumps.js';
/** Escape counts as No, as it counts as Stay in space for a landing. */
export const ESCAPE_STAYS = false;

import { HANGAR_TEXT } from './board.js';

/**
 * The question the viewer must answer now, `{ ship, system, renewal }`, or null. It stays non-null until they answer, so a
 * reload or a later visit opens the dialog again (D09). [renewal] is a ship already in the hangar, asked whether it stays.
 */
export function hangarFor(game, playerId) {
	const name = game.turn?.hangarShip;
	if (!name || jumper(game) !== playerId || game.turn.interception || game.turn.battle) {
		return null;
	}
	const ship = game.players.find(p => p.id === playerId)?.ships.find(s => s.name === name);
	const system = ship && game.board.find(s => s.id === ship.system);
	return ship && system ? { ship, system, renewal: Boolean(ship.hangar) } : null;
}

/** "Wayfarer: Parking Hangar at Tau Ceti". */
export function hangarHeading(hangar) {
	return `${hangar.ship.name}: Parking Hangar at ${hangar.system.name}`;
}

/** The question, with the fee: new parking and keeping a parked ship another turn read differently. */
export function hangarQuestion(hangar, fee) {
	const ask = hangar.renewal ? `Keep ${hangar.ship.name} in the hangar for another turn` : `Park ${hangar.ship.name} in the hangar this turn`;
	return `${ask}? It costs ${fee} HT. ${HANGAR_TEXT}`;
}

export function hangarYesLabel(fee) {
	return `Yes, pay ${fee} HT`;
}

export function hangarNoLabel(hangar) {
	return hangar.renewal ? 'No, take it out' : 'No, thanks';
}

/** What to announce once the question is answered: "Wayfarer is parked." */
export function hangarAnsweredText(shipName, renewal, stay) {
	if (stay) {
		return renewal ? `${shipName} stays in the hangar.` : `${shipName} is parked in the hangar.`;
	}
	return renewal ? `${shipName} left the hangar.` : '';
}
