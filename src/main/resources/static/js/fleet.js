/*
 * Pure helpers for the Fleet tab (E07-S03): the signed-in player's ships with their hull, modules, stats, position
 * and cargo. A ship's position comes from the public `players[].ships`; everything else from `you.ships`, the only place
 * a hull, a module or a stat is sent (R57).
 */

export const NO_SHIPS_TEXT = 'You have no ships';
export const NO_CARGO_SPACE_TEXT = 'No cargo space — fit Cargo modules to carry POLY or ISO';
export const MILITARY_LABEL = 'Military: illegal to possess';
export const AERODYNAMIC_LABEL = 'Aerodynamic: easier landing';
export const MODULE_COLUMNS = ['Module', 'Fitted', 'Weapons', 'Rockets', 'Crew', 'Cargo', 'Passengers'];

const LOCATION_NAMES = { PORT: 'Port', PLANET: 'Planet', SPACE: 'in Space' };
const CARGO_ORDER = ['POLY', 'ISO', 'CHIPS', 'FOOD'];

/** R56: the labels of a hull or module, each a sentence, so a flag is never a colour alone (S03-4). */
export function flagLabels(part) {
	return [part.military ? MILITARY_LABEL : null, part.aerodynamic ? AERODYNAMIC_LABEL : null].filter(Boolean);
}

/** S03-3: "Tau Ceti · Port", or "Tau Ceti · in Space" for a ship that hasn't landed. */
export function whereText(ship) {
	return `${ship.systemName} · ${LOCATION_NAMES[ship.location] ?? ship.location}`;
}

/** S03-5: what a ship carries, one entry per good, the goods that take cargo space first. */
export function cargoRows(cargo) {
	const rank = goods => (CARGO_ORDER.includes(goods) ? CARGO_ORDER.indexOf(goods) : CARGO_ORDER.length);
	return Object.entries(cargo ?? {})
		.filter(([, units]) => units > 0)
		.sort(([a], [b]) => rank(a) - rank(b))
		.map(([goods, units]) => ({ goods, units }));
}

/** The R53 stat block of a ship, in the order the Fleet tab shows it; cargo has its own meter. */
export function statRows(ship) {
	const stats = ship.stats;
	return [
		{ label: 'Weapons', value: String(stats.weapons) },
		{ label: 'Rockets', value: String(stats.rocketWeapons) },
		{ label: 'Shields', value: String(stats.shields) },
		{ label: 'Intercept', value: String(stats.intercept) },
		{ label: 'Crew needed', value: String(stats.crewNeeded) },
		{ label: 'Passengers', value: String(stats.passengers) },
		{ label: 'Modules', value: `${stats.modulesUsed} / ${stats.moduleCapacity}` },
	];
}

/** One row per different module (S03-2): its name, "× 4", and what one of them adds, as the cells under [MODULE_COLUMNS]. */
export function moduleRows(modules) {
	return (modules ?? []).map(module => ({
		name: module.name,
		flags: flagLabels(module),
		cells: [`× ${module.count}`, module.weapons, module.rocketWeapons, module.crew, module.cargoSpaces, module.passengers].map(String),
	}));
}

/**
 * S03-2 to S03-5: each of [myEmail]'s ships in [game] as the Fleet tab shows it. Empty for a player with no ships
 * (S03-8), and for anyone whose own ships weren't sent.
 */
export function fleetShips(game, myEmail) {
	const me = game.players.find(player => player.email === myEmail);
	const ownByName = Object.fromEntries((game.you?.ships ?? []).map(ship => [ship.name, ship]));
	return (me?.ships ?? []).filter(ship => ownByName[ship.name]?.hull).map(ship => {
		const own = ownByName[ship.name];
		return {
			name: ship.name,
			systemId: ship.system,
			where: whereText(ship),
			hull: { name: own.hull.name, flags: flagLabels(own.hull) },
			modules: moduleRows(own.modules),
			stats: statRows(own),
			cargo: {
				used: own.cargoUsed,
				spaces: own.cargoSpaces,
				text: `${own.cargoUsed} / ${own.cargoSpaces} spaces`,
				fill: own.cargoSpaces > 0 ? `${Math.min(100, Math.round((own.cargoUsed / own.cargoSpaces) * 100))}%` : '0%',
				goods: cargoRows(own.cargo),
				note: own.cargoSpaces === 0 ? NO_CARGO_SPACE_TEXT : null,
			},
		};
	});
}
