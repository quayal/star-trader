import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	battleShipRows,
	battleStep,
	canBreakAway,
	fightDecisionText,
	fightTitle,
	hitParts,
	interceptionBody,
	interceptionText,
	interceptors,
	isFighting,
	jettisonable,
	ordersBody,
	overflow,
	shipHitsBody,
	targets,
	waitingText,
} from '../../main/resources/static/js/battle.js';
import { jumpingText, shipsToJump } from '../../main/resources/static/js/ship-jumps.js';
import { landingFor } from '../../main/resources/static/js/landing.js';

const ME = 'me';
const VEGA = 'vega';

const CARGO = { id: 'CARGO', name: 'Cargo', count: 4, damaged: 1, weapons: 0, rocketWeapons: 0, cargoSpaces: 2 };
const LIGHT = { id: 'LIGHT_WEAPONS', name: 'Light weapons', count: 1, damaged: 0, weapons: 3, rocketWeapons: 5, cargoSpaces: 0 };

function battleShip(name, ownerId, extra = {}) {
	return {
		name,
		ownerId,
		hull: { name: 'Clarinet' },
		hullDamage: 0,
		modules: [CARGO, LIGHT],
		stats: { weapons: 6, shields: 2, intercept: 2 },
		cargoSpaces: 6,
		crewClass: 'B',
		...extra,
	};
}

function game({ interception = null, battle = null, you = {} } = {}) {
	return {
		status: 'RUNNING',
		turn: {
			number: 2,
			phase: 'SHIP_JUMPS',
			turnOrder: [VEGA, ME],
			doneJumping: [],
			jumpedShips: ['Meridian'],
			landingShip: 'Meridian',
			interception,
			battle,
		},
		players: [
			{ id: ME, corporationName: 'Orion Freight', ships: [{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'SPACE' }] },
			{ id: VEGA, corporationName: 'Vega Lines', ships: [{ name: 'Meridian', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'SPACE' }, { name: 'Halcyon', system: 'MU_HERCULIS', systemName: 'Mu Herculis', location: 'PORT' }] },
		],
		board: [{ id: 'TAU_CETI', name: 'Tau Ceti' }],
		you: { id: ME, ships: [{ name: 'Wayfarer', cargo: { POLY: 4, FOOD: 2 }, cargoUsed: 4, cargoSpaces: 6 }], interception: null, battle: null, ...you },
	};
}

const INTERCEPTION = { system: 'TAU_CETI', systemName: 'Tau Ceti', ship: 'Meridian', ownerId: VEGA, asked: [ME], awaiting: [ME] };

function battle(extra = {}) {
	return {
		system: 'TAU_CETI',
		systemName: 'Tau Ceti',
		round: 2,
		stage: 'ORDERS',
		intercepted: 'Meridian',
		ships: [battleShip('Wayfarer', ME, { hullDamage: 1, interceptedBy: [] }), battleShip('Meridian', VEGA, { interceptedBy: ['Wayfarer'] })],
		leftShips: [],
		waitingFor: [ME, VEGA],
		pendingHits: [],
		...extra,
	};
}

const MY_BATTLE = { fighting: ['Wayfarer'], mayBreakAway: ['Wayfarer'], orders: [], hitsToPlace: [], toLand: [] };

describe('an interception (E10-S02)', () => {
	it('stops the jumps and the landing dialog while it is decided', () => {
		const deciding = game({ interception: INTERCEPTION });

		assert.equal(isFighting(deciding), true);
		assert.deepEqual(shipsToJump(deciding, VEGA), []);
		assert.equal(landingFor(deciding, VEGA), null);
		assert.equal(fightTitle(deciding), 'Interception at Tau Ceti');
		assert.equal(interceptionText(deciding), 'Meridian (Vega Lines) arrived in Space at Tau Ceti.');
	});

	it('asks the viewer only while they have not answered', () => {
		const asked = game({ interception: INTERCEPTION, you: { interception: { ships: ['Wayfarer'], answered: false, chosen: null } } });
		const answered = game({ interception: INTERCEPTION, you: { interception: { ships: ['Wayfarer'], answered: true, chosen: null } } });

		assert.deepEqual(interceptors(asked), ['Wayfarer']);
		assert.match(fightDecisionText(asked, ME), /intercept/);
		assert.deepEqual(interceptors(answered), []);
		assert.equal(fightDecisionText(answered, ME), '');
	});

	it('names who the game waits for, other than you', () => {
		assert.equal(waitingText(game({ interception: { ...INTERCEPTION, awaiting: [ME, VEGA] } }), ME), 'Waiting for Vega Lines.');
	});
});

describe('a battle (E10-S03, S04)', () => {
	it('lists every ship with its hull, damage and what it fights with (R78)', () => {
		const rows = battleShipRows(game({ battle: battle() }));

		assert.equal(rows.length, 2);
		assert.equal(rows[0].hull, 'Clarinet, hull 1 / 2 hit');
		assert.equal(rows[0].stats, 'weapons 6 (rockets 8), shields 2, intercept 2, crew B');
		assert.equal(rows[0].modules, 'Cargo × 4 (1 damaged), Light weapons × 1');
		assert.equal(rows[0].note, '');
		assert.equal(rows[1].note, 'intercepted by Wayfarer');
		assert.equal(fightTitle(game({ battle: battle() })), 'Battle at Tau Ceti — round 2');
	});

	it('asks for orders only while yours are missing', () => {
		assert.equal(battleStep(game({ battle: battle(), you: { battle: MY_BATTLE } }), ME), 'ORDERS');
		assert.equal(battleStep(game({ battle: battle({ waitingFor: [VEGA] }), you: { battle: MY_BATTLE } }), ME), null);
		assert.equal(battleStep(game({ battle: battle({ stage: 'HITS' }), you: { battle: { ...MY_BATTLE, hitsToPlace: [{ ship: 'Wayfarer', hits: 2 }] } } }), ME), 'HITS');
		assert.equal(battleStep(game({ battle: battle({ stage: 'LANDING' }), you: { battle: { ...MY_BATTLE, toLand: ['Wayfarer'] } } }), ME), 'LANDING');
	});

	it('fires only at other players’ ships, and lets a ship break away only when nothing holds it (R74)', () => {
		const fighting = game({ battle: battle(), you: { battle: MY_BATTLE } });
		const held = game({ battle: battle(), you: { battle: { ...MY_BATTLE, mayBreakAway: [] } } });

		assert.deepEqual(targets(fighting, ME), [{ name: 'Meridian', label: 'Meridian (Vega Lines)' }]);
		assert.equal(canBreakAway(fighting, 'Wayfarer'), true);
		assert.equal(canBreakAway(held, 'Wayfarer'), false);
	});

	it('sends one interceptor, or none (R70)', () => {
		assert.deepEqual(interceptionBody('Wayfarer'), { ship: 'Wayfarer' });
		assert.deepEqual(interceptionBody(''), { ship: null });
	});

	it('sends a target as a fire order and break away without one', () => {
		assert.deepEqual(ordersBody([{ ship: 'Wayfarer', target: 'Meridian', rockets: true }, { ship: 'Lantern', target: 'BREAK_AWAY', rockets: true }]), {
			orders: [{ ship: 'Wayfarer', action: 'FIRE', target: 'Meridian', rockets: true }, { ship: 'Lantern', action: 'BREAK_AWAY' }],
		});
	});
});

describe('placing hits (R76)', () => {
	const hit = game({ battle: battle({ stage: 'HITS' }) });

	it('offers the hull and every fitted module, working or damaged, with the most each takes', () => {
		const parts = hitParts(hit, 'Wayfarer');

		assert.deepEqual(parts.map(part => [part.key, part.max]), [
			['hull', 2], ['CARGO-0', 2], ['CARGO-1', 2], ['CARGO-2', 2], ['CARGO-damaged-0', 1], ['LIGHT_WEAPONS-0', 2],
		]);
	});

	it('works out the cargo thrown overboard when cargo modules are hit', () => {
		const parts = hitParts(hit, 'Wayfarer');

		assert.equal(overflow(hit, 'Wayfarer', parts, { hull: 1 }), 0);
		assert.equal(overflow(hit, 'Wayfarer', parts, { 'CARGO-0': 1, 'CARGO-damaged-0': 1 }), 0);
		assert.equal(overflow(hit, 'Wayfarer', parts, { 'CARGO-0': 1, 'CARGO-1': 2 }), 2);
		assert.deepEqual(jettisonable(hit, 'Wayfarer'), [{ goods: 'POLY', units: 4 }]);
	});

	it('sends the hull, each module hit and what goes overboard', () => {
		const parts = hitParts(hit, 'Wayfarer');

		assert.deepEqual(shipHitsBody('Wayfarer', parts, { hull: 1, 'CARGO-1': 2, 'CARGO-damaged-0': 1 }, { POLY: 2, ISO: 0 }), {
			ship: 'Wayfarer',
			hull: 1,
			modules: [{ module: 'CARGO', damaged: false, hits: 2 }, { module: 'CARGO', damaged: true, hits: 1 }],
			jettison: { POLY: 2 },
		});
	});
});

describe('the top bar during a fight (E10)', () => {
	it('names the battle or interception instead of the jumper', () => {
		assert.equal(jumpingText(game({ battle: battle() }), ME), 'battle at Tau Ceti');
		assert.equal(jumpingText(game({ interception: INTERCEPTION }), ME), 'interception at Tau Ceti');
	});
});
