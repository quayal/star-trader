/*
 * Pure helpers for interception and battle on the Deck (E10). They read the public `turn.interception` and
 * `turn.battle` (with every battle ship's hull, modules and damage, R78) and the viewer's own `you.interception`,
 * `you.battle` and `you.ships` (cargo, R29) from GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6).
 */

export const INTERCEPT_BUTTON = 'Decide';
export const ORDERS_BUTTON = 'Give orders';
export const HITS_BUTTON = 'Place hits';
export const LANDING_BUTTON = 'Confirm';
export const BREAK_AWAY = 'BREAK_AWAY';
export const LOCATIONS = [
	{ value: 'PORT', label: 'Port' },
	{ value: 'PLANET', label: 'Planet' },
	{ value: 'SPACE', label: 'Space' },
];

function corporation(game, playerId) {
	return game.players.find(p => p.id === playerId)?.corporationName ?? playerId;
}

/** Whether an interception is being decided or a battle lasts (R73): the jumps wait for it. */
export function isFighting(game) {
	return game.status === 'RUNNING' && Boolean(game.turn?.interception || game.turn?.battle);
}

/** The panel's heading: "Interception at Tau Ceti" or "Battle at Tau Ceti — round 2"; "" when nothing is under way. */
export function fightTitle(game) {
	const battle = game.turn?.battle;
	if (battle) {
		return battle.stage === 'LANDING' ? `Battle at ${battle.systemName} — over` : `Battle at ${battle.systemName} — round ${battle.round}`;
	}
	const interception = game.turn?.interception;
	return interception ? `Interception at ${interception.systemName}` : '';
}

/** R70: what's being decided, for everyone: "Meridian (Vega Lines) arrived in Space at Tau Ceti." */
export function interceptionText(game) {
	const interception = game.turn?.interception;
	if (!interception) {
		return '';
	}
	return `${interception.ship} (${corporation(game, interception.ownerId)}) arrived in Space at ${interception.systemName}.`;
}

/** Whom the interception or battle waits for, by corporation, other than [playerId]: "Waiting for Vega Lines." or "". */
export function waitingText(game, playerId) {
	const ids = game.turn?.battle?.waitingFor ?? game.turn?.interception?.awaiting ?? [];
	const others = ids.filter(id => id !== playerId).map(id => corporation(game, id));
	return others.length ? `Waiting for ${others.join(', ')}.` : '';
}

/** R70: the viewer's ships that may try to intercept (one of them may), while they still have to decide; [] otherwise. */
export function interceptors(game) {
	const mine = game.you?.interception;
	return mine && !mine.answered ? mine.ships : [];
}

/**
 * What the viewer must do in the battle now (R74–R77): 'ORDERS', 'HITS', 'LANDING', or null when nothing, including
 * once their orders for this round are in.
 */
export function battleStep(game, playerId) {
	const battle = game.turn?.battle;
	const mine = game.you?.battle;
	if (!battle || !mine) {
		return null;
	}
	if (battle.stage === 'ORDERS' && mine.fighting.length && battle.waitingFor.includes(playerId)) {
		return 'ORDERS';
	}
	if (battle.stage === 'HITS' && mine.hitsToPlace.length) {
		return 'HITS';
	}
	if (battle.stage === 'LANDING' && mine.toLand.length) {
		return 'LANDING';
	}
	return null;
}

/** The decision alert on other screens (S01-9) for an interception or battle step, or "". */
export function fightDecisionText(game, playerId) {
	if (interceptors(game).length) {
		return 'Your decision is needed: a ship arrived where yours wait in Space. Choose whether to intercept it.';
	}
	return {
		ORDERS: 'Your decision is needed: give your ships their battle orders.',
		HITS: 'Your decision is needed: place the hits your ships took.',
		LANDING: 'Your decision is needed: the battle is over. Choose where your ships end up.',
	}[battleStep(game, playerId)] ?? '';
}

/**
 * Every ship in the battle as the panel lists it (R78): name, owner, hull with damage, and what it fights with, plus
 * whether it's the intercepted one or has broken away.
 */
export function battleShipRows(game) {
	const battle = game.turn?.battle;
	if (!battle) {
		return [];
	}
	const row = (ship, left) => ({
		name: ship.name,
		owner: corporation(game, ship.ownerId),
		ownerId: ship.ownerId,
		hull: `${ship.hull.name}, hull ${ship.hullDamage} / ${ship.stats.shields} hit`,
		stats: `weapons ${ship.stats.weapons}, rockets ${ship.stats.rocketWeapons}, shields ${ship.stats.shields}, intercept ${ship.stats.intercept}, crew ${ship.crewClass}`,
		modules: ship.modules.map(module => `${module.name} × ${module.count}${module.damaged ? ` (${module.damaged} damaged)` : ''}`).join(', ') || 'no modules',
		note: left ? 'broke away' : ship.interceptedBy?.length ? `intercepted by ${ship.interceptedBy.join(' and ')}` : '',
	});
	return [...battle.ships.map(ship => row(ship, false)), ...battle.leftShips.map(ship => row(ship, true))];
}

function working(module) {
	return module.count - module.damaged;
}

/** R74: the ships [playerId]'s ships may fire at: every other player's ship still fighting. */
export function targets(game, playerId) {
	return (game.turn?.battle?.ships ?? []).filter(ship => ship.ownerId !== playerId).map(ship => ({
		name: ship.name,
		label: `${ship.name} (${corporation(game, ship.ownerId)})`,
	}));
}

/** R75: whether [shipName] may fire rockets: only its working modules' rocket values count, so it needs one. */
export function canFireRockets(game, shipName) {
	return (game.turn?.battle?.ships.find(ship => ship.name === shipName)?.stats.rocketWeapons ?? 0) > 0;
}

/** R74: whether the viewer's [shipName] may break away: nothing that intercepted it still fights. */
export function canBreakAway(game, shipName) {
	return game.you?.battle?.mayBreakAway?.includes(shipName) ?? false;
}

/** R70: the interception body for the one ship chosen, or none. */
export function interceptionBody(ship) {
	return { ship: ship || null };
}

/** The orders body from each ship's choice: a target's name, or BREAK_AWAY, and whether to use rockets. */
export function ordersBody(choices) {
	return {
		orders: choices.map(choice => choice.target === BREAK_AWAY
			? { ship: choice.ship, action: BREAK_AWAY }
			: { ship: choice.ship, action: 'FIRE', target: choice.target, rockets: Boolean(choice.rockets) }),
	};
}

/**
 * R76: the parts of the ship called [shipName] that can take hits, one per hull and per fitted module, working or
 * damaged: `{ key, label, max, module, damaged, cargoSpaces }`. Keys are stable, so typed numbers survive a poll.
 */
export function hitParts(game, shipName) {
	const ship = game.turn?.battle?.ships.find(s => s.name === shipName);
	if (!ship) {
		return [];
	}
	const parts = [{ key: 'hull', label: `Hull (${ship.hullDamage} / ${ship.stats.shields} hits taken)`, max: ship.stats.shields + 1 - ship.hullDamage, module: null, damaged: false, cargoSpaces: 0 }];
	for (const module of ship.modules) {
		for (let i = 0; i < working(module); i += 1) {
			parts.push({ key: `${module.id}-${i}`, label: working(module) > 1 ? `${module.name} ${i + 1}` : module.name, max: 2, module: module.id, damaged: false, cargoSpaces: module.cargoSpaces });
		}
		for (let i = 0; i < module.damaged; i += 1) {
			parts.push({ key: `${module.id}-damaged-${i}`, label: `${module.name} (damaged)`, max: 1, module: module.id, damaged: true, cargoSpaces: 0 });
		}
	}
	return parts;
}

/**
 * R76: how many POLY and ISO units the ship must throw overboard after [hits] (by part key) land on [parts], given its
 * own cargo from `you.ships`: every working module hit loses its cargo spaces.
 */
export function overflow(game, shipName, parts, hits) {
	const own = game.you?.ships.find(ship => ship.name === shipName);
	if (!own) {
		return 0;
	}
	const lost = parts.filter(part => part.module && !part.damaged && (hits[part.key] ?? 0) > 0).reduce((sum, part) => sum + part.cargoSpaces, 0);
	return Math.max(0, own.cargoUsed - (own.cargoSpaces - lost));
}

/** The POLY and ISO the ship carries, which are the only goods that can be thrown overboard (R42, R76). */
export function jettisonable(game, shipName) {
	const own = game.you?.ships.find(ship => ship.name === shipName);
	return ['POLY', 'ISO'].filter(goods => (own?.cargo?.[goods] ?? 0) > 0).map(goods => ({ goods, units: own.cargo[goods] }));
}

/** The hits body for one ship: [hits] by part key over [parts], and the units thrown overboard by good. */
export function shipHitsBody(shipName, parts, hits, jettison) {
	return {
		ship: shipName,
		hull: hits.hull ?? 0,
		modules: parts.filter(part => part.module && (hits[part.key] ?? 0) > 0).map(part => ({ module: part.module, damaged: part.damaged, hits: hits[part.key] })),
		jettison: Object.fromEntries(Object.entries(jettison).filter(([, units]) => units > 0)),
	};
}

/** R76, said once per ship above its fields. */
export const HIT_RULES = 'A working module: 1 hit damages it, 2 destroy it. A damaged one: 1 destroys it. The hull takes up to its shields; one more destroys the ship.';

/** "3 hits to place" for a ship, or "1 hit to place". */
export function hitsText(hits) {
	return hits === 1 ? '1 hit to place' : `${hits} hits to place`;
}

export const REPORT_BUTTON = 'OK';

/** The battle report dialog's heading: "Battle at Tau Ceti — round 1 results", or "" with no report. */
export function reportTitle(game) {
	const report = game.you?.battleReport;
	return report ? `Battle at ${report.systemName} — round ${report.round} results` : '';
}

/**
 * R75–R78: what the last round's salvos did to and from the viewer's ships, one line each, worded from the outcome the
 * server decided: `{ key, text }`. The key identifies the line for "already seen"; a line about the viewer's own ship
 * reads the same whether or not they've placed its hits yet, so placing them doesn't bring it back.
 */
export function reportLines(game, playerId) {
	const report = game.you?.battleReport;
	if (!report) {
		return [];
	}
	const firers = new Set(report.results.filter(result => result.firerOwnerId === playerId).map(result => result.firer));
	return report.results.map(result => {
		const text = result.firerOwnerId === playerId
			? `${firers.size > 1 ? `${result.firer}: ` : ''}${firedText(game, result)}`
			: takenText(game, result);
		return { key: `${game.id}/${game.turn?.number}/${report.system}/${report.round}/${text}`, text };
	});
}

function damage(result) {
	return `${result.rockets ? ' with rockets' : ''} for ${result.hits} damage`;
}

function firedText(game, result) {
	const owner = corporation(game, result.targetOwnerId);
	switch (result.outcome) {
	case 'BROKE_AWAY':
		return `You fired at ${result.target}, but it broke away.`;
	case 'NO_DAMAGE':
		return `You fired${result.rockets ? ' rockets' : ''} at ${result.target}: no damage.`;
	case 'AWAITING_PLACEMENT':
		return `You hit ${result.target}${damage(result)}, wait for ${owner} to apply damage to ship.`;
	case 'PLACED':
		return `You hit ${result.target}${damage(result)}, ${owner} applied the damage.`;
	default:
		return `You hit ${result.target}${damage(result)}, ship destroyed.`;
	}
}

function takenText(game, result) {
	const firer = `${result.firer} (${corporation(game, result.firerOwnerId)})`;
	switch (result.outcome) {
	case 'BROKE_AWAY':
		return `${firer} fired at your ${result.target}, but it broke away.`;
	case 'NO_DAMAGE':
		return `${firer} fired${result.rockets ? ' rockets' : ''} at your ${result.target}: no damage.`;
	case 'DESTROYED':
		return `${firer} hit your ${result.target}${damage(result)}, ship destroyed.`;
	default:
		return `${firer} hit your ${result.target}${damage(result)}.`;
	}
}
