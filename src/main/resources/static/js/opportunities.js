/*
 * Pure helpers for the Opportunities card on Infrastructure (R111–R114): a form for each opportunity the viewer has learned
 * and nobody has taken, the sales of special goods they can make where their ships are, and their expeditions due back.
 * The server sends each learned opportunity's terms (`you.tipOffs[].terms`); ship positions come from the public players
 * list, special goods from `you.ships[].specialLots`. Only the server decides; this only offers what could work.
 */

const MODULE_NAMES = { ARSENAL: 'Arsenal', BATTLE_COMM: 'Battle comm', HEAVY_WEAPONS: 'Heavy weapons', DOGFIGHT: 'Dogfight' };
const HULL_NAMES = { DAGGER: 'Dagger', SWORD: 'Sword', SPEAR: 'Spear' };

function positions(game) {
	return game.players.find(p => p.id === game.you?.id)?.ships ?? [];
}

function systemName(game, id) {
	return game.board?.find(s => s.id === id)?.name ?? id;
}

/** The viewer's ships at [location] of [system] (any system when null), by name. */
function shipsAt(game, system, location) {
	return positions(game).filter(s => (!system || s.system === system) && s.location === location).map(s => s.name);
}

/**
 * For military modules alone (R113), each of [names]' room (creator, 2026-11-21): `{ [ship]: { free, removable } }`, its
 * free module slots and the modules that could come off to make room, one entry per module fitted, with what each pays back.
 */
function roomOf(game, names) {
	const ships = Object.fromEntries((game.you?.ships ?? []).map(s => [s.name, s]));
	return Object.fromEntries(names.filter(name => ships[name]).map(name => {
		const ship = ships[name];
		const removable = (ship.modules ?? []).filter(m => m.takesModuleCapacity !== false)
			.flatMap(m => Array.from({ length: m.count }, () => ({ id: m.id, name: m.name, resaleValue: m.resaleValue })));
		return [name, { free: Math.max(0, ship.stats.moduleCapacity - ship.stats.modulesUsed), removable }];
	}));
}

const HOLD_TEXT = { CARGO_SPACE: 'Each unit takes a cargo space.', PASSENGER_SEAT: 'Each unit takes a passenger seat.' };

/**
 * What a goods opportunity's form says besides its title (playtest, 2026-11-21: all a player needs): how many units and
 * what they cost against the viewer's money, what each takes aboard, and where and how they sell. Empty for the others.
 */
function goodsFacts(game, terms) {
	if (!terms.maxUnits) {
		return [];
	}
	const affordable = Math.min(terms.maxUnits, Math.floor((game.you?.money ?? 0) / terms.price));
	const sellAt = systemName(game, terms.sellAt);
	return [
		`Buy 1 to ${terms.maxUnits} units at ${terms.price} HT each: you can afford ${affordable} (${affordable * terms.price} HT).`,
		terms.kind === 'FURS' && terms.hold === 'NONE' ? 'Furs need no cargo space.' : HOLD_TEXT[terms.hold] ?? 'They need no cargo space.',
		terms.kind === 'FURS'
			? `Sell them for ${terms.salePrice} HT each in ${sellAt}'s Port, on your Opportunity turn there.`
			: `Sell them on ${sellAt}'s black market (on its Planet), at a price rolled on a d6.`,
	];
}

/** The units field's start: as many as the viewer can afford, at least 1 (the server refuses 0). */
function defaultUnits(game, terms) {
	return terms.maxUnits ? Math.max(1, Math.min(terms.maxUnits, Math.floor((game.you?.money ?? 0) / terms.price))) : 0;
}

/**
 * One form per learned, untaken opportunity: `{ slot, title, kind, ships, units, maxUnits, facts, hulls, modules,
 * maxModules, crewClasses, hullOptional, room, note, takeable }`; [room] is each ship's room for modules alone ([roomOf]). [ships] are those that can take it now; an empty list says why in [note].
 */
export function opportunityForms(game) {
	// Playtest 2026-11-21: [takeable] says a ship can take it now (a military ship's hull appears on the Planet by itself).
	return opportunityFormsWithoutTakeable(game).map(form => ({ ...form, takeable: form.kind === 'MILITARY_SHIP' || form.ships.length > 0 }));
}

function opportunityFormsWithoutTakeable(game) {
	return (game.you?.tipOffs ?? []).filter(t => t.learned && t.terms && !t.taken).map(tipOff => {
		const terms = tipOff.terms;
		const at = systemName(game, terms.buyAt);
		const base = {
			slot: tipOff.slot, title: tipOff.details.replace(/^Opportunity: /, ''), kind: terms.kind, units: false, maxUnits: terms.maxUnits ?? 0,
			facts: goodsFacts(game, terms), defaultUnits: defaultUnits(game, terms), hulls: [], modules: [], maxModules: terms.maxModules ?? 0, hullOptional: terms.hullOptional,
			room: {},
		};
		switch (terms.kind) {
		case 'ILLEGAL_GOODS':
			return { ...base, units: true, ships: shipsAt(game, terms.buyAt, 'PLANET'), note: `Needs a ship on ${at}'s Planet.` };
		case 'FURS':
			return { ...base, units: true, ships: shipsAt(game, terms.buyAt, 'PORT'), note: `Needs a ship in ${at}'s Port.` };
		case 'MILITARY_SHIP':
			return {
				...base,
				hulls: terms.hulls.map(id => ({ id, name: HULL_NAMES[id] ?? id })),
				modules: terms.modules.map(id => ({ id, name: MODULE_NAMES[id] ?? id })),
				ships: terms.hullOptional ? shipsAt(game, terms.buyAt, 'PLANET') : [],
				room: terms.hullOptional ? roomOf(game, shipsAt(game, terms.buyAt, 'PLANET')) : {},
				note: `The ship appears on ${at}'s Planet.`,
			};
		case 'MILITARY_MODULES': {
			const ships = shipsAt(game, terms.buyAt, 'PLANET');
			return {
				...base,
				modules: terms.modules.map(id => ({ id, name: MODULE_NAMES[id] ?? id })),
				ships,
				room: roomOf(game, ships),
				note: `Needs a ship on ${at}'s Planet. With no room, take modules off to make it (half their price back).`,
			};
		}
		case 'DELIVERY':
			return { ...base, ships: shipsAt(game, terms.buyAt, 'PORT'), note: `Needs a ship in ${at}'s Port with a free ${terms.space === 'PASSENGER' ? 'Passenger' : 'Cargo'} module.` };
		default:
			return { ...base, ships: positions(game).filter(s => s.location !== 'SHIPYARD').map(s => s.name), note: 'Only a ship with no military parts can go.' };
		}
	});
}

/**
 * The sales the viewer can make now (R112): `{ kind: 'BLACK_MARKET' | 'FURS', system, goods, label }`, one per good and
 * place where a ship of theirs holds goods bound there, on the Planet for illegal goods and in the Port for furs.
 */
export function specialSales(game) {
	const where = Object.fromEntries(positions(game).map(s => [s.name, s]));
	const sales = new Map();
	for (const ship of game.you?.ships ?? []) {
		for (const lot of ship.specialLots ?? []) {
			const place = where[ship.name];
			const location = lot.illegal ? 'PLANET' : 'PORT';
			if (place?.system === lot.sellAt && place.location === location) {
				const key = `${lot.goods}/${lot.sellAt}`;
				const units = (sales.get(key)?.units ?? 0) + lot.units;
				sales.set(key, {
					kind: lot.illegal ? 'BLACK_MARKET' : 'FURS',
					system: lot.sellAt,
					goods: lot.goods,
					units,
					label: lot.illegal ? `Sell ${units} ${lot.name} on the black market at ${lot.sellAtName}` : `Sell ${units} furs at ${lot.sellAtName}`,
				});
			}
		}
	}
	return [...sales.values()];
}

/**
 * R125: a form for each of the viewer's ships in a Port with a free passenger seat: `{ ship, system, systemName, seats,
 * destinations: [{ id, name, left }] }`, the routes from that Port with groups left (`game.passengerRoutes`, public).
 */
export function passengerForms(game) {
	const ships = Object.fromEntries((game.you?.ships ?? []).map(s => [s.name, s]));
	return positions(game).filter(p => p.location === 'PORT' && (ships[p.name]?.freeSeats ?? 0) > 0).map(p => ({
		ship: p.name,
		system: p.system,
		systemName: systemName(game, p.system),
		seats: ships[p.name].freeSeats,
		destinations: (game.passengerRoutes ?? []).filter(r => r.from === p.system && r.left > 0)
			.map(r => ({ id: r.to, name: systemName(game, r.to), left: r.left })),
	}));
}

/** R121: the viewer's ships in their delivery's destination Port, each with a Deliver button on their Opportunity turn. */
export function deliveriesDue(game) {
	return (game.you?.ships ?? []).filter(ship => ship.canDeliver).map(ship => ({ ship: ship.name, label: `Deliver with ${ship.name}: ${ship.deliveryJob}` }));
}

/** The viewer's expeditions back this turn (R114), each to be brought to a Port of their choice. */
export function expeditionsDue(game) {
	return (game.you?.expeditions ?? []).filter(e => e.due);
}

/**
 * The viewer's opportunities in action (creator, 2026-11-23), shown on the Opportunity screen in every phase: goods bought
 * through one and still aboard (R112), delivery jobs under way (R114) and ships away on the expedition (R114), each
 * `{ ship, what, next }` with where the ship is now. Empty when nothing is under way.
 */
export function opportunitiesInAction(game) {
	const where = Object.fromEntries(positions(game).map(s => [s.name, s]));
	const at = name => {
		const place = where[name];
		return place ? `${systemName(game, place.system)} ${place.location === 'PLANET' ? 'Planet' : place.location === 'SPACE' ? 'Space' : 'Port'}` : '';
	};
	const rows = [];
	for (const ship of game.you?.ships ?? []) {
		for (const lot of ship.specialLots ?? []) {
			rows.push({
				ship: ship.name,
				where: at(ship.name),
				what: `${lot.units} ${lot.name}${lot.illegal ? ' (illegal)' : ''}`,
				next: lot.illegal ? `Sell on ${lot.sellAtName}'s Planet, on the black market` : `Sell in ${lot.sellAtName}'s Port`,
			});
		}
		if (ship.deliveryJob) {
			rows.push({ ship: ship.name, where: at(ship.name), what: `Delivery: ${ship.deliveryJob}`, next: 'Deliver in the destination Port' });
		}
	}
	for (const group of game.you?.passengerGroups ?? []) {
		rows.push({
			ship: group.ship,
			where: at(group.ship) || 'ship lost',
			what: `${group.groups} passenger ${group.groups === 1 ? 'group' : 'groups'} for ${group.toName}`,
			next: `The ship must be in ${group.toName}'s Port, seats in working order, when the next Opportunity opens, or you pay ${group.groups * 10} HT`,
		});
	}
	for (const expedition of game.you?.expeditions ?? []) {
		rows.push({
			ship: expedition.ship,
			where: 'on the expedition',
			what: 'Scientific expedition',
			next: expedition.due ? 'Back this turn: choose its Port' : `Back in turn ${expedition.returnTurn}`,
		});
	}
	return rows;
}
