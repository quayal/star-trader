/*
 * Pure helpers for moving goods between a ship in port and the viewer's warehouse at the same system (R58, E08-S02), on
 * the Infrastructure screen (s10). They read the public `players[].ships` (where each ship is) and the viewer's own
 * `you.ships` and `you.warehouses` (what each holds, which only they get, R29) from GET /api/games/{id}; the move is
 * POST /api/games/{id}/cargo/moves (BACKWARD_COMPATIBILITY.md §6).
 */

export const MOVE_INTRO = 'Between one of your ships in port and your warehouse at the same system, at any time, in any phase. Nothing is lost, and only you see it.';
export const NO_SHIP_IN_PORT_TEXT = 'None of your ships is in port at a system where you have a warehouse.';
export const MOVE_BUTTON = 'Move goods';
export const MOVE_WHOLE_NUMBER = 'Enter a whole number of units, 1 or more.';
export const FLEET_MOVE_LINK = 'Move goods between a ship in port and your warehouse';

/** R42: POLY and ISO take one cargo space per unit; CHIPS and FOOD take none. */
const TAKES_CARGO_SPACE = new Set(['POLY', 'ISO']);

/** Whether the viewer can ever move goods now (S02-7): the game is running and they own a warehouse. */
export function canMoveCargo(game) {
	return game.status === 'RUNNING' && (game.you?.warehouses ?? []).length > 0;
}

function holdingText(cargo) {
	return Object.entries(cargo ?? {}).map(([goods, units]) => `${goods} ${units}`).join(', ') || 'empty';
}

/**
 * Where [playerId] can move goods (R58): each of their ships in port at a system where they have a warehouse, with that
 * warehouse. `ship` and `warehouse` are the viewer's own entries (cargo, cargoUsed, cargoSpaces); `label` is the ship's
 * option, "Wayfarer at Tau Ceti — ISO 3 · cargo 3 / 8 used", and `warehouseLabel` the line under it.
 */
export function cargoPlaces(game, playerId) {
	if (!canMoveCargo(game)) {
		return [];
	}
	const own = Object.fromEntries((game.you.ships ?? []).map(ship => [ship.name, ship]));
	const mine = game.players.find(player => player.id === playerId)?.ships ?? [];
	return mine.flatMap(position => {
		const ship = own[position.name];
		const warehouse = game.you.warehouses.find(candidate => candidate.system === position.system);
		if (position.location !== 'PORT' || !ship || !warehouse) {
			return [];
		}
		return [{
			ship,
			warehouse,
			system: position.system,
			systemName: position.systemName,
			label: `${ship.name} at ${position.systemName} — ${holdingText(ship.cargo)} · cargo ${ship.cargoUsed} / ${ship.cargoSpaces} used`,
			warehouseLabel: `Warehouse at ${position.systemName} — ${holdingText(warehouse.cargo)} · cargo ${warehouse.cargoUsed} / ${warehouse.cargoSpaces} used`,
		}];
	});
}

/** The holder the goods leave and the one they go to, for a move [to] `SHIP` or `WAREHOUSE`, each with the name a message gives it. */
function ends(place, to) {
	const ship = { holder: place.ship, name: place.ship.name };
	const warehouse = { holder: place.warehouse, name: `Your warehouse at ${place.systemName}` };
	return to === 'SHIP' ? { source: warehouse, destination: ship } : { source: ship, destination: warehouse };
}

/** The goods the source holds, as the Goods picker lists them: `[{ goods, units }]`. */
export function movableGoods(place, to) {
	return Object.entries(ends(place, to).source.holder.cargo ?? {})
		.filter(([, units]) => units > 0)
		.map(([goods, units]) => ({ goods, units }));
}

function held(place, goods, to) {
	return ends(place, to).source.holder.cargo?.[goods] ?? 0;
}

/** The free cargo space [goods] can use in the destination (R42): unlimited for CHIPS and FOOD. */
function room(place, goods, to) {
	const { holder } = ends(place, to).destination;
	return TAKES_CARGO_SPACE.has(goods) ? Math.max(0, holder.cargoSpaces - holder.cargoUsed) : Infinity;
}

/** The most units of [goods] that can move (R58): what the source holds, capped by the destination's free space. */
export function moveMax(place, goods, to) {
	return Math.min(held(place, goods, to), room(place, goods, to));
}

/** What the form says under the units (S02-7): how many can move, and why not more when space is what caps it. */
export function moveHint(place, goods, to) {
	const { source, destination } = ends(place, to);
	if (!goods || held(place, goods, to) === 0) {
		return `${source.name} holds nothing to move.`;
	}
	const max = moveMax(place, goods, to);
	if (max === 0) {
		return `${destination.name} has no free cargo space for ${goods}.`;
	}
	return max < held(place, goods, to)
		? `Up to ${max} u: ${destination.name} has ${max} free cargo ${max === 1 ? 'space' : 'spaces'}.`
		: `Up to ${max} u.`;
}

/**
 * Checks what was typed as the server will (S02-2): `{ error }`, or `{ body }` for POST /api/games/{id}/cargo/moves.
 */
export function validateMove(place, goods, to, text) {
	const trimmed = String(text ?? '').trim();
	const units = /^\d+$/.test(trimmed) ? Number(trimmed) : 0;
	if (units < 1) {
		return { error: MOVE_WHOLE_NUMBER };
	}
	const { source, destination } = ends(place, to);
	if (units > held(place, goods, to)) {
		return { error: `${source.name} has only ${held(place, goods, to)} ${goods}.` };
	}
	if (units > room(place, goods, to)) {
		return { error: `${destination.name} has room for only ${room(place, goods, to)} more ${goods}.` };
	}
	return { body: { ship: place.ship.name, goods, units, to } };
}

/** What a move says once made: "Moved 4 ISO from your warehouse at Tau Ceti to Wayfarer." */
export function movedText(place, goods, units, to) {
	const { source, destination } = ends(place, to);
	const inSentence = name => name.replace(/^Your /, 'your ');
	return `Moved ${units} ${goods} from ${inSentence(source.name)} to ${inSentence(destination.name)}.`;
}
