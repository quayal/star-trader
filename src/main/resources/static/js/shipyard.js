/*
 * Pure helpers for shipyards (E09): buying a ship on Infrastructure, refitting and selling one on Fleet. What a shipyard
 * sells comes from `you.invest.shipyard` (only while the viewer may invest); a ship's position from the public
 * `players[].ships`; its hull, modules, crew class and sale value from `you.ships` (R57).
 */

export const MAX_NAME_LENGTH = 20;
export const NAME_ERROR = `Choose a name of 1 to ${MAX_NAME_LENGTH} characters.`;
export const LATE_BUY_WARNING = 'This ship would leave the yard only after the game ends.';
export const BUY_BUTTON = 'Buy ship';
export const REFIT_NOTE = 'Refit at a shipyard: in the yard, or in port at Beta Hydri, Epsilon Eridani or Tau Ceti.';

/** The goods in the server's order, which a sold ship's cargo goes into the warehouse in (R66, `Goods` order). */
const GOODS_ORDER = ['CHIPS', 'FOOD', 'ISO', 'POLY'];
const TAKES_CARGO_SPACE = new Set(['POLY', 'ISO']);

/** What the shipyard sells, or null when the viewer can't buy now. */
export function shipyardOf(game) {
	return game.you?.invest?.shipyard ?? null;
}

/**
 * R61: what a ship of [hullId] with [counts] (module id → how many) and [crewClassId] costs, as its parts and a line:
 * "Hull 160 + modules 43 + crew 1 × 10 = 213 HT". Null for a hull or class the shipyard doesn't sell.
 */
export function shipCost(shipyard, hullId, counts, crewClassId) {
	const hull = shipyard.hulls.find(entry => entry.id === hullId);
	const crewClass = shipyard.crewClasses.find(entry => entry.id === crewClassId);
	if (!hull || !crewClass) {
		return null;
	}
	const fitted = fittedModules(shipyard, counts);
	const modules = fitted.reduce((sum, { module, count }) => sum + module.price * count, 0);
	const crewCount = hull.crew + fitted.reduce((sum, { module, count }) => sum + module.crew * count, 0);
	const crew = crewCount * crewClass.pricePerCrew;
	const total = hull.price + modules + crew;
	return { hull: hull.price, modules, crew, crewCount, total, text: `Hull ${hull.price} + modules ${modules} + crew ${crewCount} × ${crewClass.pricePerCrew} = ${total} HT` };
}

/** The modules [counts] names that the shipyard sells, with how many of each; unknown ids and zero counts are left out. */
function fittedModules(shipyard, counts) {
	return shipyard.modules
		.map(module => ({ module, count: Number(counts[module.id] ?? 0) }))
		.filter(({ count }) => Number.isInteger(count) && count > 0);
}

/**
 * R52: whether [counts] fit [hullId]: the modules that take capacity against the hull's, and one Safe jump at most.
 * `{ used, capacity, error }`; error is null when they fit.
 */
export function fitCheck(shipyard, hullId, counts) {
	const hull = shipyard.hulls.find(entry => entry.id === hullId);
	const capacity = hull?.moduleCapacity ?? 0;
	const fitted = fittedModules(shipyard, counts);
	const used = fitted.filter(({ module }) => module.takesModuleCapacity).reduce((sum, { count }) => sum + count, 0);
	const safeJumps = fitted.filter(({ module }) => !module.takesModuleCapacity).reduce((sum, { count }) => sum + count, 0);
	const badCount = Object.values(counts).some(value => value !== '' && value !== undefined && !(Number.isInteger(Number(value)) && Number(value) >= 0));
	let error = null;
	if (badCount) {
		error = 'Enter a whole number of each module, 0 or more.';
	} else if (used > capacity) {
		error = `A ${hull.name} holds ${capacity} modules, not ${used}.`;
	} else if (safeJumps > 1) {
		error = 'A ship has at most one Safe jump module.';
	}
	return { used, capacity, error };
}

/** The modules as the request sends them: one id per unit, in the shipyard's order. */
export function moduleList(shipyard, counts) {
	return fittedModules(shipyard, counts).flatMap(({ module, count }) => Array(count).fill(module.id));
}

/** R62: the trimmed name, or an error: 1 to 20 characters, and no ship in [game] by that name, ignoring case. */
export function validateShipName(game, name) {
	const trimmed = (name ?? '').trim();
	if (trimmed.length < 1 || trimmed.length > MAX_NAME_LENGTH) {
		return { error: NAME_ERROR };
	}
	const taken = game.players.flatMap(player => player.ships ?? []).some(ship => ship.name.toLowerCase() === trimmed.toLowerCase());
	if (taken) {
		return { error: `There's already a ship called ${trimmed} in this game.` };
	}
	return { name: trimmed };
}

/** R62: a random free suggestion other than [current], or null when none is left; [random] is Math.random in the page. */
export function suggestName(shipyard, current, random = Math.random) {
	const names = (shipyard?.freeNames ?? []).filter(name => name !== current);
	return names.length ? names[Math.floor(random() * names.length)] : null;
}

/** R39, R63: a ship bought now leaves the yard next turn and first jumps the turn after, so in the last two turns it never sails. */
export function lateBuyWarning(game) {
	return game.turn && game.turn.number + 2 > game.turns ? LATE_BUY_WARNING : '';
}

/**
 * R66: what selling the viewer's ship [shipName] does with its cargo, as the server does it: each good in goods order goes
 * into their warehouse at the ship's system as far as it fits (POLY and ISO take a cargo space a unit, CHIPS and FOOD
 * none), the rest is lost. `{ stored: [{ goods, units }], lost: [{ goods, units }] }`.
 */
export function saleOutcome(game, myId, shipName) {
	const position = game.players.find(player => player.id === myId)?.ships.find(ship => ship.name === shipName);
	const own = game.you?.ships.find(ship => ship.name === shipName);
	const warehouse = (game.you?.warehouses ?? []).find(entry => entry.system === position?.system);
	let free = warehouse ? Math.max(0, warehouse.cargoSpaces - warehouse.cargoUsed) : 0;
	const stored = [];
	const lost = [];
	for (const goods of GOODS_ORDER) {
		const units = own?.cargo?.[goods] ?? 0;
		if (units === 0) {
			continue;
		}
		const fits = !warehouse ? 0 : TAKES_CARGO_SPACE.has(goods) ? Math.min(units, free) : units;
		if (TAKES_CARGO_SPACE.has(goods)) {
			free -= fits;
		}
		if (fits > 0) {
			stored.push({ goods, units: fits });
		}
		if (units - fits > 0) {
			lost.push({ goods, units: units - fits });
		}
	}
	return { stored, lost, systemName: position?.systemName ?? '' };
}

function goodsList(entries) {
	return entries.map(({ goods, units }) => `${units} ${goods}`).join(', ');
}

/**
 * R66: the confirm step's line: "Sell Wayfarer for 101 HT? 3 ISO go to your warehouse at Tau Ceti; 2 POLY are lost." It
 * names every unit that moves or is lost, so nothing goes silently.
 */
export function saleConfirmText(game, myId, ship) {
	const { stored, lost, systemName } = saleOutcome(game, myId, ship.name);
	const parts = [`Sell ${ship.name} for ${ship.saleValue} HT?`];
	if (stored.length) {
		parts.push(`${goodsList(stored)} ${stored.length === 1 && stored[0].units === 1 ? 'goes' : 'go'} to your warehouse at ${systemName}.`);
	}
	if (lost.length) {
		parts.push(`${goodsList(lost)} ${lost.length === 1 && lost[0].units === 1 ? 'is' : 'are'} lost${stored.length ? '' : ' with it'}.`);
	}
	return parts.join(' ');
}

/** R66: the line after the sale, from what the confirm step said: "Sold Wayfarer for 101 HT. 3 ISO went to …; 2 POLY were lost." */
export function soldText(outcome, shipName, saleValue) {
	const parts = [`Sold ${shipName} for ${saleValue} HT.`];
	if (outcome.stored.length) {
		parts.push(`${goodsList(outcome.stored)} went to your warehouse at ${outcome.systemName}.`);
	}
	if (outcome.lost.length) {
		parts.push(`${goodsList(outcome.lost)} ${outcome.lost.length === 1 && outcome.lost[0].units === 1 ? 'was' : 'were'} lost.`);
	}
	return parts.join(' ');
}

/**
 * R65: what the viewer can do to [ship] at a shipyard: the modules they can add (those for sale that still fit, Safe jump
 * only without one), each with its cost at the ship's crew class, and those they can remove, each with what it pays
 * back. Empty when the ship isn't at a shipyard or the shipyard isn't open to them.
 */
export function refitOptions(shipyard, ship) {
	if (!shipyard || !ship.atShipyard) {
		return { add: [], remove: [] };
	}
	const pricePerCrew = shipyard.crewClasses.find(entry => entry.id === ship.crewClass)?.pricePerCrew ?? 0;
	const roomLeft = ship.stats.moduleCapacity - ship.stats.modulesUsed;
	const hasSafeJump = ship.modules.some(module => module.id === 'SAFE_JUMP');
	const add = shipyard.modules
		.filter(module => (module.takesModuleCapacity ? roomLeft > 0 : !hasSafeJump))
		.map(module => ({ id: module.id, name: module.name, cost: module.price + module.crew * pricePerCrew }));
	const remove = ship.modules.map(fitted => {
		const price = shipyard.modules.find(module => module.id === fitted.id)?.price ?? 0;
		return { id: fitted.id, name: fitted.name, refund: Math.floor(price / 2) };
	});
	return { add, remove };
}
