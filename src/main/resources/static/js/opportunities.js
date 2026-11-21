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
 * One form per learned, untaken opportunity: `{ slot, title, kind, ships, units, hulls, modules, maxModules, crewClasses,
 * hullOptional, note }`. [ships] are those that can take it now; an empty list says why in [note].
 */
export function opportunityForms(game) {
	return (game.you?.tipOffs ?? []).filter(t => t.learned && t.terms && !t.taken).map(tipOff => {
		const terms = tipOff.terms;
		const at = systemName(game, terms.buyAt);
		const base = { slot: tipOff.slot, title: tipOff.details.replace(/^Opportunity: /, ''), kind: terms.kind, units: false, hulls: [], modules: [], maxModules: terms.maxModules ?? 0, hullOptional: terms.hullOptional };
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
				note: `The ship appears on ${at}'s Planet.`,
			};
		case 'MILITARY_MODULES':
			return { ...base, modules: terms.modules.map(id => ({ id, name: MODULE_NAMES[id] ?? id })), ships: shipsAt(game, terms.buyAt, 'PLANET'), note: `Needs a ship on ${at}'s Planet with room.` };
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

/** The viewer's expeditions back this turn (R114), each to be brought to a Port of their choice. */
export function expeditionsDue(game) {
	return (game.you?.expeditions ?? []).filter(e => e.due);
}
