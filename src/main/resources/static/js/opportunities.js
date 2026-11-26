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

/**
 * What an opportunity's form says besides its title (playtest, 2026-11-21; creator, 2026-11-25: all a player needs to decide):
 * every term as the server writes it out, after, for goods, how many units the viewer's money buys.
 */
function formFacts(game, terms) {
	const facts = terms.facts ?? [];
	if (!terms.maxUnits) {
		return facts;
	}
	const affordable = Math.min(terms.maxUnits, Math.floor((game.you?.money ?? 0) / terms.price));
	return [`You can afford ${affordable} ${affordable === 1 ? 'unit' : 'units'} (${affordable * terms.price} HT).`, ...facts];
}

/** The units field's start: as many as the viewer can afford, at least 1 (the server refuses 0). */
function defaultUnits(game, terms) {
	return terms.maxUnits ? Math.max(1, Math.min(terms.maxUnits, Math.floor((game.you?.money ?? 0) / terms.price))) : 0;
}

/**
 * What taking [form] costs with what the viewer has picked, [choice] (`{ units, hull, crew, ship, modules, removed }`: ids as
 * the form's controls hold them), worked out live as for a ship bought in a shipyard (R61): `{ total, refund, affordable, text }`,
 * where [affordable] counts the refund of modules taken off (the server pays it back first). Null when nothing is priced yet: a
 * free opportunity, or a choice still incomplete. [rules] is GET /api/rules, for the hulls', modules' and crews' prices (R113).
 */
export function opportunityCost(game, rules, form, choice) {
	const money = game.you?.money ?? 0;
	const priced = (total, refund, text) => ({ total, refund, affordable: total - refund <= money, text: refund ? `${text}, ${refund} HT back` : text });
	const moduleOf = id => rules?.modules?.find(module => module.id === id);
	const crewPrice = id => rules?.crewClasses?.find(crew => crew.id === id)?.pricePerCrew;
	if (form.kind === 'ILLEGAL_GOODS' || form.kind === 'FURS') {
		const units = Number(choice.units);
		return Number.isInteger(units) && units >= 1 ? priced(units * form.price, 0, `${units} × ${form.price} = ${units * form.price} HT`) : null;
	}
	if (form.kind !== 'MILITARY_SHIP' && form.kind !== 'MILITARY_MODULES') {
		return null;
	}
	const chosen = (choice.modules ?? []).map(moduleOf);
	if (chosen.includes(undefined)) {
		return null;
	}
	if (form.kind === 'MILITARY_SHIP' && choice.hull) {
		const hull = rules?.hulls?.find(entry => entry.id === choice.hull);
		const perCrew = crewPrice(choice.crew);
		if (!hull || perCrew === undefined) {
			return null;
		}
		const modules = chosen.reduce((sum, module) => sum + module.price, 0);
		const crewCount = hull.crew + chosen.reduce((sum, module) => sum + module.crew, 0);
		const total = hull.price + modules + crewCount * perCrew;
		return priced(total, 0, `Hull ${hull.price} + modules ${modules} + crew ${crewCount} × ${perCrew} = ${total} HT`);
	}
	// Modules alone, on a ship of the viewer's on the Planet: each costs its price and its crew at that ship's class (R113).
	const own = (game.you?.ships ?? []).find(ship => ship.name === choice.ship);
	const perCrew = own && crewPrice(own.crewClass);
	if (perCrew === undefined || chosen.length === 0) {
		return null;
	}
	const modules = chosen.reduce((sum, module) => sum + module.price, 0);
	const crew = chosen.reduce((sum, module) => sum + module.crew, 0) * perCrew;
	const refund = (choice.removed ?? []).map(moduleOf).filter(Boolean).reduce((sum, module) => sum + Math.floor(module.price / 2), 0);
	return priced(modules + crew, refund, `Modules ${modules} + crew ${crew} = ${modules + crew} HT`);
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
			slot: tipOff.slot, title: terms.summary, kind: terms.kind, units: false, maxUnits: terms.maxUnits ?? 0, price: terms.price ?? 0,
			facts: formFacts(game, terms), defaultUnits: defaultUnits(game, terms), hulls: [], modules: [], maxModules: terms.maxModules ?? 0, hullOptional: terms.hullOptional,
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
 * `{ ship, what, next }` with where the ship is now. Empty when nothing is under way. Passengers aboard are [passengersAboard]'s,
 * in a box of their own (creator, 2026-11-25).
 */
export function opportunitiesInAction(game) {
	const at = shipPlace(game);
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

/** Where each of the viewer's ships is now, as "Tau Ceti Port"; '' for a ship that's gone. */
function shipPlace(game) {
	const where = Object.fromEntries(positions(game).map(s => [s.name, s]));
	return name => {
		const place = where[name];
		return place ? `${systemName(game, place.system)} ${place.location === 'PLANET' ? 'Planet' : place.location === 'SPACE' ? 'Space' : 'Port'}` : '';
	};
}

/**
 * The viewer's passenger groups aboard (R125), for the Passengers box in every phase (creator, 2026-11-25): `{ ship, where,
 * what, next }`, as [opportunitiesInAction]'s rows. Empty when none are aboard.
 */
export function passengersAboard(game) {
	const at = shipPlace(game);
	return (game.you?.passengerGroups ?? []).map(group => ({
		ship: group.ship,
		where: at(group.ship) || 'ship lost',
		what: `${group.groups} passenger ${group.groups === 1 ? 'group' : 'groups'} for ${group.toName}`,
		next: `The ship must be in ${group.toName}'s Port, seats in working order, when the next Opportunity opens, or you pay ${group.groups * 10} HT`,
	}));
}
