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
 * The ship the player with [playerId] must land now, with its system from the board, or null. It stays non-null until
 * they confirm, so a reload or a later visit opens the dialog again (S03-6, D09).
 */
export function landingFor(game, playerId) {
	const name = game.turn?.landingShip;
	// E10: a ship being intercepted or in a battle lands when the fight is over, with the battle's own form.
	if (!name || jumper(game) !== playerId || game.turn.interception || game.turn.battle) {
		return null;
	}
	const ship = game.players.find(p => p.id === playerId)?.ships.find(s => s.name === name);
	const system = ship && game.board.find(s => s.id === ship.system);
	return ship && system ? { ship, system } : null;
}

/** S03-1: "Wayfarer arrived at Tau Ceti". */
export function landingHeading(landing) {
	return `${landing.ship.name} arrived at ${landing.system.name}`;
}

/**
 * S03-1: the three places with their R31 statistics, Port first. With the viewer's own ship ([own], from `you.ships`) the
 * Planet shows its landing odds (R118) and a warning for contraband; [conditions] (`game.conditions`) close the Port during
 * epidemics (E10) and add this turn's security change.
 */
export function landingOptions(system, own = null, conditions = null) {
	const closed = Boolean(conditions?.portsClosed);
	const refused = Boolean(own?.portRefused);
	const odds = own?.landing;
	const planet = odds
		? `Land on the planet: ${odds.lands}% lands · ${odds.forced}% forced into Port · ${odds.seized}% seized (security ${odds.security}, your +${odds.bonus})`
		: `Land on the planet (security ${system.security})`;
	return [
		{
			value: 'PORT',
			label: closed ? 'Land at the port: closed this turn (epidemics)' : refused ? `Land at the port: refused, military parts aboard (legal system ${system.legalSystem})` : `Land at the port (class ${system.portClass}, legal system ${system.legalSystem})`,
			disabled: closed || refused,
		},
		{ value: 'PLANET', label: planet, warning: own?.contraband ? 'Illegal goods or military parts aboard: if security stops you, they’re confiscated.' : '' },
		{ value: 'SPACE', label: `Stay in space (police ${system.police + (conditions?.policeChange ?? 0)})` },
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
