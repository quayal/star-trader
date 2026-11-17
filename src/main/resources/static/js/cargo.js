/*
 * Pure helpers for moving goods between two of the viewer's holders in the same port (R58, E08-S02, revised
 * 2026-11-17): two of their ships in port at the same system, or a ship in port and their warehouse there. The form is
 * on the Fleet tab. They read the public `players[].ships` (where each ship is) and the viewer's own `you.ships` and
 * `you.warehouses` (what each holds, which only they get, R29) from GET /api/games/{id}; the move is
 * POST /api/games/{id}/cargo/moves with `{ from, to, goods, units }`, each end a ship's name or left out for the
 * warehouse (BACKWARD_COMPATIBILITY.md §6).
 */

export const MOVE_INTRO = 'Between two of your ships in the same port, or a ship in port and your warehouse there, at any time, in any phase. Nothing is lost, and only you see it.';
export const NO_SHIP_IN_PORT_TEXT = 'None of your ships is in port with another of your ships or your warehouse.';
export const MOVE_BUTTON = 'Move goods';
export const MOVE_WHOLE_NUMBER = 'Enter a whole number of units, 1 or more.';
export const INFRASTRUCTURE_MOVE_LINK = 'Move goods on the Fleet tab';

/** R42: POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. */
const TAKES_CARGO_SPACE = new Set(['POLY', 'ISO']);

/** Whether the Move goods panel shows (S02-7): the game is running and the viewer is in it. */
export function canMoveCargo(game) {
	return game.status === 'RUNNING' && Boolean(game.you);
}

/** Whether the viewer has a warehouse, so Infrastructure points them to the Fleet tab to move goods. */
export function hasWarehouse(game) {
	return game.status === 'RUNNING' && (game.you?.warehouses ?? []).length > 0;
}

function holdingText(cargo) {
	return Object.entries(cargo ?? {}).map(([goods, units]) => `${goods} ${units}`).join(', ') || 'empty';
}

/**
 * Every holder of [playerId]'s that goods can move out of or into (R58): their ships in port and their warehouse at
 * each such port, kept only where the port has at least two of them. Each is `{ key, ship, system, systemName, holder,
 * name, label }`: `ship` is the ship's name, or null for the warehouse; `holder` the viewer's own entry (cargo,
 * cargoUsed, cargoSpaces); `name` what a message calls it; `label` its option, "Wayfarer at Tau Ceti — ISO 3, CHIPS 5 ·
 * cargo 3 / 8 used" or "Warehouse at Tau Ceti — …".
 */
export function cargoHolders(game, playerId) {
	if (!canMoveCargo(game)) {
		return [];
	}
	const own = Object.fromEntries((game.you.ships ?? []).map(ship => [ship.name, ship]));
	const inPort = (game.players.find(player => player.id === playerId)?.ships ?? [])
		.filter(position => position.location === 'PORT' && own[position.name]);
	const holders = [];
	const seenPorts = new Set();
	for (const position of inPort) {
		const ship = own[position.name];
		holders.push({
			key: `ship:${ship.name}`,
			ship: ship.name,
			system: position.system,
			systemName: position.systemName,
			holder: ship,
			name: ship.name,
			label: `${ship.name} at ${position.systemName} — ${holdingText(ship.cargo)} · cargo ${ship.cargoUsed} / ${ship.cargoSpaces} used`,
		});
		const warehouse = (game.you.warehouses ?? []).find(candidate => candidate.system === position.system);
		if (warehouse && !seenPorts.has(position.system)) {
			holders.push({
				key: `warehouse:${position.system}`,
				ship: null,
				system: position.system,
				systemName: position.systemName,
				holder: warehouse,
				name: `Your warehouse at ${position.systemName}`,
				label: `Warehouse at ${position.systemName} — ${holdingText(warehouse.cargo)} · cargo ${warehouse.cargoUsed} / ${warehouse.cargoSpaces} used`,
			});
		}
		seenPorts.add(position.system);
	}
	return holders.filter(entry => holders.some(other => other !== entry && other.system === entry.system));
}

/** Where goods from [from] can go: the other holders in the same port. */
export function destinationsFor(holders, from) {
	return from ? holders.filter(entry => entry !== from && entry.system === from.system) : [];
}

/** The goods [from] holds, as the Goods picker lists them: `[{ goods, units }]`. */
export function movableGoods(from) {
	return Object.entries(from?.holder.cargo ?? {})
		.filter(([, units]) => units > 0)
		.map(([goods, units]) => ({ goods, units }));
}

function held(from, goods) {
	return from.holder.cargo?.[goods] ?? 0;
}

/** The free cargo space [goods] can use in [to] (R42): unlimited for CHIPS and FOOD. */
function room(to, goods) {
	return TAKES_CARGO_SPACE.has(goods) ? Math.max(0, to.holder.cargoSpaces - to.holder.cargoUsed) : Infinity;
}

/** The most units of [goods] that can move (R58): what [from] holds, capped by [to]'s free space. */
export function moveMax(from, to, goods) {
	return Math.min(held(from, goods), room(to, goods));
}

/** What the form says under the units (S02-7): how many can move, and why not more when space is what caps it. */
export function moveHint(from, to, goods) {
	if (!from || !to) {
		return '';
	}
	if (!goods || held(from, goods) === 0) {
		return `${from.name} holds nothing to move.`;
	}
	const max = moveMax(from, to, goods);
	if (max === 0) {
		return `${to.name} has no free cargo space for ${goods}.`;
	}
	return max < held(from, goods)
		? `Up to ${max} u: ${to.name} has ${max} free cargo ${max === 1 ? 'space' : 'spaces'}.`
		: `Up to ${max} u.`;
}

/**
 * Checks what was typed as the server will (S02-2): `{ error }`, or `{ body }` for POST /api/games/{id}/cargo/moves,
 * where a warehouse end is left out.
 */
export function validateMove(from, to, goods, text) {
	const trimmed = String(text ?? '').trim();
	const units = /^\d+$/.test(trimmed) ? Number(trimmed) : 0;
	if (units < 1) {
		return { error: MOVE_WHOLE_NUMBER };
	}
	if (units > held(from, goods)) {
		return { error: `${from.name} has only ${held(from, goods)} ${goods}.` };
	}
	if (units > room(to, goods)) {
		return { error: `${to.name} has room for only ${room(to, goods)} more ${goods}.` };
	}
	const body = { goods, units };
	if (from.ship) {
		body.from = from.ship;
	}
	if (to.ship) {
		body.to = to.ship;
	}
	return { body };
}

/** What a move says once made: "Moved 4 ISO from your warehouse at Tau Ceti to Wayfarer." */
export function movedText(from, to, goods, units) {
	const inSentence = name => name.replace(/^Your /, 'your ');
	return `Moved ${units} ${goods} from ${inSentence(from.name)} to ${inSentence(to.name)}.`;
}
