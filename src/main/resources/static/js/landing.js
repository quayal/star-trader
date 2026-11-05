/*
 * Pure helpers for the landing dialog (s05, E04-S03): after each jump the player chooses where the ship ends up (R30).
 * They read `turn.landingShip`, the public `players[].ships` and the board's R31 location statistics from
 * GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6).
 */

import { jumper } from './ship-jumps.js';

export const LANDING_QUESTION = 'Where do you want to be?';
export const LANDING_BUTTON = 'Confirm';
/** S03-2, proto-A21: Port is pre-selected; Escape counts as Stay in space. */
export const DEFAULT_LOCATION = 'PORT';
export const ESCAPE_LOCATION = 'SPACE';

/**
 * The ship the player with [email] must land now, with its system from the board, or null. It stays non-null until
 * they confirm, so a reload or a later visit opens the dialog again (S03-6, D09).
 */
export function landingFor(game, email) {
	const name = game.turn?.landingShip;
	if (!name || jumper(game) !== email) {
		return null;
	}
	const ship = game.players.find(p => p.email === email)?.ships.find(s => s.name === name);
	const system = ship && game.board.find(s => s.id === ship.system);
	return ship && system ? { ship, system } : null;
}

/** S03-1: "Wayfarer arrived at Tau Ceti". */
export function landingHeading(landing) {
	return `${landing.ship.name} arrived at ${landing.system.name}`;
}

/** S03-1: the three places with their R31 statistics, Port first. */
export function landingOptions(system) {
	return [
		{ value: 'PORT', label: `Land at the port (class ${system.portClass}, legal system ${system.legalSystem})` },
		{ value: 'PLANET', label: `Land on the planet (security ${system.security})` },
		{ value: 'SPACE', label: `Stay in space (police ${system.police})` },
	];
}

/** What to announce once the landing is confirmed: "Wayfarer landed at the port." */
export function landedText(shipName, location) {
	return {
		PORT: `${shipName} landed at the port.`,
		PLANET: `${shipName} landed on the planet.`,
		SPACE: `${shipName} stays in space.`,
	}[location] ?? '';
}
