/*
 * Pure helpers for the Jumps screen (R139, `.ai/specs/implemented/2026-11-26-jumps-screen-and-phase-rail.md`): for each of the viewer's ships,
 * what it owes and where, and for every destination what waits there. They read the public board, players and turn, and
 * the viewer's own `you.ships` (cargo, `jumpChances`, `destinations`, obligations), `you.offers`, `you.passengerGroups`,
 * `you.warehouses`, `you.agents` and `you.expeditions` from GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6). Every
 * percentage comes from the server; nothing here computes odds.
 */

import { jumper } from './ship-jumps.js';
import { agentsAboard } from './fleet.js';

const LOCATION_NAMES = { PORT: 'Port', PLANET: 'Planet', SPACE: 'Space', SHIPYARD: 'Shipyard' };
/** POLY and ISO take cargo space; CHIPS and FOOD don't (R42). */
const SPACE_GOODS = ['POLY', 'ISO'];

export const JUMP_HERE = 'Jump here';
export const CONTRABAND_TEXT = 'Carries contraband: a forced landing confiscates it';

function me(game, viewerId) {
	return game.players.find(player => player.id === viewerId) ?? null;
}

function own(game, shipName) {
	return (game.you?.ships ?? []).find(ship => ship.name === shipName) ?? null;
}

function systemName(game, systemId) {
	return game.board.find(system => system.id === systemId)?.name ?? systemId;
}

function corporation(game, playerId) {
	return game.players.find(player => player.id === playerId)?.corporationName ?? playerId;
}

function groupsText(groups) {
	return `${groups} ${groups === 1 ? 'group' : 'groups'}`;
}

/**
 * What [shipName] owes and where (R139 criterion 3), the viewer's alone: `{ kind, system, text, due }`. Passengers are due
 * this turn (a booking falls due at the next Opportunity), so they're the one obligation with `due`; a delivery, goods
 * that sell in one place and contraband are listed without alarm. Contraband has no system.
 */
export function obligations(game, shipName) {
	const ship = own(game, shipName);
	const passengers = (game.you?.passengerGroups ?? []).filter(group => group.ship === shipName).map(group => ({
		kind: 'passengers',
		system: group.to,
		groups: group.groups,
		penalty: group.penalty,
		text: `${groupsText(group.groups)} → ${group.toName} Port, by the next Opportunity · ${group.penalty} HT penalty if missed`,
		due: true,
	}));
	if (!ship) {
		return passengers;
	}
	const delivery = ship.deliveryTo
		? [{ kind: 'delivery', system: ship.deliveryTo, text: `Delivery → ${systemName(game, ship.deliveryTo)} Port`, due: false }]
		: [];
	const lots = (ship.specialLots ?? []).map(lot => ({
		kind: 'goods',
		system: lot.sellAt,
		text: `${lot.units} ${lot.name.toLowerCase()} sell ${lot.illegal ? 'on' : 'at'} ${lot.sellAtName} ${lot.illegal ? 'Planet' : 'Port'}`,
		due: false,
	}));
	const contraband = ship.contraband ? [{ kind: 'contraband', system: null, text: CONTRABAND_TEXT, due: false }] : [];
	return [...passengers, ...delivery, ...lots, ...contraband];
}

/** Why [ship] (public `players[].ships` entry) can't jump this turn, or '' when it can. */
function blockedReason(game, ship) {
	const yours = own(game, ship.name);
	if ((game.you?.expeditions ?? []).some(expedition => expedition.ship === ship.name)) {
		const expedition = game.you.expeditions.find(e => e.ship === ship.name);
		return `On an expedition until turn ${expedition.returnTurn}`;
	}
	if (ship.location === 'SHIPYARD') {
		return yours?.leavesYardOnTurn ? `In the yard until turn ${yours.leavesYardOnTurn}` : 'In the yard';
	}
	if ((game.turn?.jumpedShips ?? []).includes(ship.name)) {
		return `Jumped this turn · now at ${ship.systemName}`;
	}
	return '';
}

/**
 * One card per ship of the viewer (criterion 2): those that can still jump first, then the rest with why not. Each card
 * has where it is, its cargo, seats, damage, the viewer's agents aboard and its obligations.
 */
export function shipCards(game, viewerId) {
	const cards = (me(game, viewerId)?.ships ?? []).map(ship => {
		const yours = own(game, ship.name);
		const reason = blockedReason(game, ship);
		const seats = yours?.stats?.passengers ?? 0;
		const damaged = (yours?.modules ?? []).filter(module => module.damaged > 0).map(module => `${module.damaged} ${module.name}`);
		return {
			name: ship.name,
			system: ship.system,
			where: `${ship.systemName} · ${LOCATION_NAMES[ship.location] ?? ship.location}${ship.hangar ? ' (parked)' : ''}`,
			canJump: reason === '',
			reason,
			cargo: yours ? `${yours.cargoUsed} / ${yours.cargoSpaces} spaces` : '',
			goods: Object.entries(yours?.cargo ?? {}).map(([goods, units]) => `${goods} ${units}`),
			seats: seats > 0 ? `${yours.freeSeats} / ${seats} seats free` : '',
			damage: [...damaged, ...(yours?.hullDamage ? [`hull ${yours.hullDamage}`] : [])],
			agents: agentsAboard(game, ship.name).map(agent => agent.name),
			obligations: obligations(game, ship.name),
		};
	});
	return [...cards.filter(card => card.canJump), ...cards.filter(card => !card.canJump)];
}

/** The ship selected when the screen opens: the first that can jump and owes something, else the first that can jump. */
export function defaultShip(cards) {
	const jumpable = cards.filter(card => card.canJump);
	return (jumpable.find(card => card.obligations.some(o => o.system)) ?? jumpable[0] ?? cards[0])?.name ?? null;
}

/** Whether the viewer has a ship (wherever it is in the system) or a warehouse at [systemId]: presence for Trading (R23, R49). */
function presentAt(game, viewerId, systemId) {
	return (me(game, viewerId)?.ships ?? []).some(ship => ship.system === systemId)
		|| (game.you?.warehouses ?? []).some(warehouse => warehouse.system === systemId);
}

/**
 * The viewer's offers this turn by system (criterion 4), in board order: `{ system, systemName, offers, needs, uncovered }`.
 * A buy needs free space there (for POLY and ISO) and a sell the goods there; [uncovered] says no ship or warehouse of
 * the viewer is there (one that already jumped there counts), so goods won there would be lost (R23).
 */
export function offersBySystem(game, viewerId) {
	const offers = game.you?.offers ?? [];
	return game.board.filter(system => offers.some(offer => offer.system === system.id)).map(system => {
		const here = offers.filter(offer => offer.system === system.id);
		const needs = here.map(offer => (offer.type === 'BUY'
			? (SPACE_GOODS.includes(offer.goods) ? `free space for ${offer.goods}` : `a ship or warehouse for ${offer.goods}`)
			: `${offer.goods} aboard a ship or in a warehouse`));
		return {
			system: system.id,
			systemName: system.name,
			offers: here.map(offer => `${offer.type === 'BUY' ? 'buy' : 'sell'} ${offer.goods} at ${offer.price}`),
			needs: [...new Set(needs)],
			uncovered: !presentAt(game, viewerId, system.id),
		};
	});
}

/** "No ship at Tau Ceti: goods bought there are lost (R23)". */
export function uncoveredText(row) {
	return `No ship at ${row.systemName}: goods bought there are lost (R23)`;
}

/** The players after the one jumping now in the turn order who haven't jumped yet, other than [viewerId]. */
function stillToJump(game, viewerId) {
	const order = game.turn?.turnOrder ?? [];
	const done = game.turn?.doneJumping ?? [];
	return order.filter(id => id !== null && id !== viewerId && !done.includes(id));
}

/**
 * One row per system for the viewer's ship [shipName] (criterion 5), the rows where it owes something first, then the
 * board's order: the jump chance, ships in Space there, this ship's Planet and police odds and the Port's status, and the
 * viewer's business there; [hangarFee] (`rules.hangarFee`, R127) names a hangar's price. Empty when the ship isn't theirs.
 */
export function destinationRows(game, viewerId, shipName, hangarFee = null) {
	const ship = (me(game, viewerId)?.ships ?? []).find(s => s.name === shipName);
	const yours = own(game, shipName);
	if (!ship || !yours) {
		return [];
	}
	const owed = obligations(game, shipName);
	const offers = offersBySystem(game, viewerId);
	const pending = stillToJump(game, viewerId);
	const rows = game.board.map(system => {
		const here = system.id === ship.system;
		const chance = yours.jumpChances?.[system.id];
		const odds = yours.destinations?.[system.id];
		const others = game.players.filter(player => player.id !== viewerId).flatMap(player => player.ships
			.filter(s => s.system === system.id && s.location === 'SPACE')
			.map(s => ({ name: s.name, corporation: player.corporationName, playerId: player.id })));
		const mine = (me(game, viewerId)?.ships ?? []).filter(s => s.system === system.id && s.location === 'SPACE' && s.name !== shipName).map(s => s.name);
		const shipsToCome = pending.filter(id => game.players.find(p => p.id === id)?.ships.length).length;
		const port = game.conditions?.portsClosed ? 'Closed this turn' : odds?.portRefused ? 'Refused: military parts' : 'Open';
		const facilities = [...(system.hangar ? [hangarFee ? `Hangar ${hangarFee} HT` : 'Hangar'] : []), ...(system.shipyard ? ['Shipyard'] : [])];
		const obligationsHere = owed.filter(o => o.system === system.id);
		const offersHere = offers.find(row => row.system === system.id);
		const business = [
			...obligationsHere.map(o => ({ text: o.text, due: o.due })),
			...(offersHere ? [{ text: `Offers: ${offersHere.offers.join(', ')}`, due: false }] : []),
		];
		return {
			system: system.id,
			name: system.name,
			here,
			chance: here ? '100%, no roll' : chance ? `${chance.percent}%` : '—',
			percent: here ? 100 : chance?.percent ?? null,
			inSpace: others.map(s => `${s.name} (${s.corporation})`),
			stillToJump: shipsToCome,
			ownInSpace: mine,
			planet: odds ? `lands ${odds.landing.lands}% · forced ${odds.landing.forced}% · seized ${odds.landing.seized}%` : '',
			port,
			facilities,
			police: odds ? `${odds.policePercent}%` : '',
			business,
			owes: obligationsHere.length > 0,
		};
	});
	return [...rows.filter(row => row.owes), ...rows.filter(row => !row.owes)];
}

/** "2 ships · 1 player still to jump", "none", or "1 player still to jump" (criterion 5). */
export function inSpaceText(row) {
	const ships = row.inSpace.length ? `${row.inSpace.length} ${row.inSpace.length === 1 ? 'ship' : 'ships'}` : 'none';
	return row.stillToJump ? `${ships} · ${row.stillToJump} ${row.stillToJump === 1 ? 'player' : 'players'} still to jump` : ships;
}

/**
 * The confirmation (criterion 6): "Wayfarer jumps to Sigma Draconis (58%)." and, for each obligation elsewhere this jump
 * leaves undone, what it costs.
 */
export function confirmText(game, viewerId, shipName, destination) {
	const row = destinationRows(game, viewerId, shipName).find(r => r.system === destination);
	if (!row) {
		return '';
	}
	const lead = row.here ? `${shipName} moves into Space at ${row.name} (always succeeds).` : `${shipName} jumps to ${row.name} (${row.chance}).`;
	const left = obligations(game, shipName).filter(o => o.system && o.system !== destination && (o.kind === 'passengers' || o.kind === 'delivery'))
		.map(o => (o.kind === 'passengers'
			? `${o.groups} passenger ${o.groups === 1 ? 'group' : 'groups'} for ${systemName(game, o.system)} stay aboard: ${o.penalty} HT penalty if no other way gets them there.`
			: `The delivery for ${systemName(game, o.system)} stays aboard.`));
	return [lead, ...left].join(' ');
}

/**
 * Whether the viewer may jump now, and if not why (criterion 8): `{ canAct, text }`. Others' slots say who the game waits
 * for; once the viewer's slot is over, that their jumps are done.
 */
export function jumpState(game, viewerId) {
	if (game.status !== 'RUNNING' || game.turn?.phase !== 'JUMPS') {
		return { canAct: false, text: 'Jumps happen after News. This screen shows what each destination holds.' };
	}
	if (game.turn.interception || game.turn.battle) {
		return { canAct: false, text: 'The jumps wait for the fight to end.' };
	}
	const who = jumper(game);
	if (who === viewerId) {
		const left = shipCards(game, viewerId).some(card => card.canJump);
		return { canAct: true, text: left ? '' : 'Every ship that can jump has jumped: finish jumping to end your slot.' };
	}
	if ((game.turn.doneJumping ?? []).includes(viewerId)) {
		return { canAct: false, text: 'Your jumps are done.' };
	}
	return { canAct: false, text: who ? `Waiting for ${corporation(game, who)}` : 'All jumps are done.' };
}

/**
 * The turn-order strip (criterion 7): each player in Jumps order with 'done', 'now' or 'to come', and where their ships
 * jumped this turn (public, as the log says).
 */
export function jumpOrder(game) {
	const order = (game.turn?.turnOrder ?? []).filter(id => id !== null);
	const who = jumper(game);
	const done = game.turn?.doneJumping ?? [];
	const jumped = game.turn?.jumpedShips ?? [];
	return order.map(id => {
		const player = game.players.find(p => p.id === id);
		return {
			corporation: player?.corporationName ?? id,
			state: id === who ? 'now' : done.includes(id) ? 'done' : 'to come',
			jumps: (player?.ships ?? []).filter(ship => jumped.includes(ship.name)).map(ship => `${ship.name} → ${ship.systemName}`),
		};
	});
}
