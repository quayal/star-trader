import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	CONTRABAND_TEXT,
	confirmText,
	defaultShip,
	destinationRows,
	inSpaceText,
	jumpOrder,
	jumpState,
	obligations,
	offersBySystem,
	shipCards,
	uncoveredText,
} from '../../main/resources/static/js/jumps.js';

const ME = 'me';
const VEGA = 'vega';

const BOARD = [
	{ id: 'GAMMA_LEPORIS', name: 'Gamma Leporis', hangar: false, shipyard: true },
	{ id: 'TAU_CETI', name: 'Tau Ceti', hangar: true, shipyard: false },
	{ id: 'SIGMA_DRACONIS', name: 'Sigma Draconis', hangar: false, shipyard: false },
];

function odds(lands, policePercent, portRefused = false) {
	return { landing: { lands, forced: 10, seized: 90 - lands, security: 7, bonus: 0 }, policePercent, portRefused };
}

function yourShip(name, extra = {}) {
	return {
		name,
		cargo: {},
		cargoUsed: 0,
		cargoSpaces: 8,
		modules: [],
		stats: { passengers: 0 },
		freeSeats: 0,
		hullDamage: 0,
		specialLots: [],
		deliveryTo: null,
		contraband: false,
		leavesYardOnTurn: null,
		jumpChances: { TAU_CETI: { target: 7, percent: 58 }, SIGMA_DRACONIS: { target: 9, percent: 83 } },
		destinations: { GAMMA_LEPORIS: odds(72, 28), TAU_CETI: odds(58, 42), SIGMA_DRACONIS: odds(17, 72, true) },
		...extra,
	};
}

function game({ phase = 'JUMPS', jumpedShips = [], doneJumping = [], offers = [], passengerGroups = [], wayfarer = {}, lantern = {}, myShips } = {}) {
	return {
		status: 'RUNNING',
		board: BOARD,
		conditions: null,
		turn: { number: 4, phase, turnOrder: [VEGA, ME], doneJumping, jumpedShips },
		players: [
			{ id: VEGA, corporationName: 'Vega Lines', ships: [{ name: 'Meridian', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'SPACE' }] },
			{
				id: ME,
				corporationName: 'Orion Freight',
				ships: myShips ?? [
					{ name: 'Wayfarer', system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis', location: 'PORT' },
					{ name: 'Lantern', system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis', location: 'SHIPYARD' },
				],
			},
		],
		you: {
			id: ME,
			ships: [yourShip('Wayfarer', wayfarer), yourShip('Lantern', { leavesYardOnTurn: 6, ...lantern })],
			offers,
			passengerGroups,
			warehouses: [],
			agents: [],
			expeditions: [],
		},
	};
}

const PASSENGERS = [{ ship: 'Wayfarer', to: 'TAU_CETI', toName: 'Tau Ceti', groups: 2, penalty: 20 }];

describe('Jumps screen: obligations (R139 criterion 3)', () => {
	it('lists passengers as due this turn, with the penalty', () => {
		const owed = obligations(game({ passengerGroups: PASSENGERS }), 'Wayfarer');
		assert.deepEqual(owed.map(o => [o.text, o.due]), [['2 groups → Tau Ceti Port, by the next Opportunity · 20 HT penalty if missed', true]]);
	});

	it('lists a delivery, goods that sell in one place and contraband, none of them due', () => {
		const owed = obligations(game({
			wayfarer: {
				deliveryTo: 'SIGMA_DRACONIS',
				contraband: true,
				specialLots: [
					{ goods: 'DRUGS', name: 'Drugs', units: 4, sellAt: 'SIGMA_DRACONIS', sellAtName: 'Sigma Draconis', illegal: true },
					{ goods: 'FURS', name: 'Furs', units: 3, sellAt: 'TAU_CETI', sellAtName: 'Tau Ceti', illegal: false },
				],
			},
		}), 'Wayfarer');
		assert.deepEqual(owed.map(o => o.text), [
			'Delivery → Sigma Draconis Port',
			'4 drugs sell on Sigma Draconis Planet',
			'3 furs sell at Tau Ceti Port',
			CONTRABAND_TEXT,
		]);
		assert.ok(owed.every(o => !o.due));
	});
});

describe('Jumps screen: ship cards (criterion 2)', () => {
	it('puts ships that can jump first and says why the others can\'t', () => {
		const cards = shipCards(game({ myShips: [
			{ name: 'Lantern', system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis', location: 'SHIPYARD' },
			{ name: 'Wayfarer', system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis', location: 'PORT' },
		] }), ME);
		assert.deepEqual(cards.map(card => [card.name, card.canJump, card.reason]), [['Wayfarer', true, ''], ['Lantern', false, 'In the yard until turn 6']]);
	});

	it('says a ship already jumped, and where it is now', () => {
		const cards = shipCards(game({ jumpedShips: ['Wayfarer'] }), ME);
		assert.equal(cards.find(card => card.name === 'Wayfarer').reason, 'Jumped this turn · now at Gamma Leporis');
	});

	it('selects the first ship that can jump and owes something', () => {
		const g = game({ passengerGroups: [{ ...PASSENGERS[0], ship: 'Lantern' }], myShips: [
			{ name: 'Wayfarer', system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis', location: 'PORT' },
			{ name: 'Lantern', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT' },
		] });
		assert.equal(defaultShip(shipCards(g, ME)), 'Lantern');
		assert.equal(defaultShip(shipCards(game(), ME)), 'Wayfarer');
	});
});

describe('Jumps screen: offers with no ship (criterion 4)', () => {
	const offers = [
		{ system: 'TAU_CETI', systemName: 'Tau Ceti', goods: 'FOOD', type: 'BUY', price: 7 },
		{ system: 'TAU_CETI', systemName: 'Tau Ceti', goods: 'ISO', type: 'SELL', price: 9 },
		{ system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis', goods: 'POLY', type: 'BUY', price: 5 },
	];

	it('flags a system with offers and nothing of the viewer\'s there', () => {
		const rows = offersBySystem(game({ offers }), ME);
		assert.deepEqual(rows.map(row => [row.systemName, row.offers, row.uncovered]), [
			['Gamma Leporis', ['buy POLY at 5'], false],
			['Tau Ceti', ['buy FOOD at 7', 'sell ISO at 9'], true],
		]);
		assert.equal(uncoveredText(rows[1]), 'No ship at Tau Ceti: goods bought there are lost (R23)');
		assert.deepEqual(rows[0].needs, ['free space for POLY']);
	});

	it('clears the flag once a ship has jumped there', () => {
		const rows = offersBySystem(game({ offers, myShips: [{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'SPACE' }] }), ME);
		assert.equal(rows.find(row => row.system === 'TAU_CETI').uncovered, false);
	});
});

describe('Jumps screen: destination table (criterion 5)', () => {
	it('shows every system, the rows where the ship owes something first', () => {
		const rows = destinationRows(game({ passengerGroups: PASSENGERS }), ME, 'Wayfarer', 5);
		assert.deepEqual(rows.map(row => row.system), ['TAU_CETI', 'GAMMA_LEPORIS', 'SIGMA_DRACONIS']);
		assert.equal(rows[0].owes, true);
		assert.deepEqual(rows[0].business.map(item => item.due), [true]);
	});

	it('gives the server\'s odds, ships in Space and the Port', () => {
		const rows = destinationRows(game(), ME, 'Wayfarer', 5);
		const tau = rows.find(row => row.system === 'TAU_CETI');
		assert.equal(tau.chance, '58%');
		assert.equal(tau.planet, 'lands 58% · forced 10% · seized 32%');
		assert.equal(tau.police, '42%');
		assert.deepEqual(tau.inSpace, ['Meridian (Vega Lines)']);
		assert.equal(inSpaceText(tau), '1 ship · 1 player still to jump');
		assert.deepEqual(tau.facilities, ['Hangar 5 HT']);
		const own = rows.find(row => row.system === 'GAMMA_LEPORIS');
		assert.equal(own.chance, '100%, no roll');
		assert.equal(rows.find(row => row.system === 'SIGMA_DRACONIS').port, 'Refused: military parts');
	});

	it('closes every Port on a turn of epidemics', () => {
		const g = { ...game(), conditions: { portsClosed: true } };
		assert.ok(destinationRows(g, ME, 'Wayfarer').every(row => row.port === 'Closed this turn'));
	});

	it('is empty for a ship that isn\'t the viewer\'s', () => {
		assert.deepEqual(destinationRows(game(), ME, 'Meridian'), []);
	});
});

describe('Jumps screen: choosing and waiting (criteria 6–8)', () => {
	it('names what a jump leaves undone', () => {
		assert.equal(
			confirmText(game({ passengerGroups: PASSENGERS }), ME, 'Wayfarer', 'SIGMA_DRACONIS'),
			'Wayfarer jumps to Sigma Draconis (83%). 2 passenger groups for Tau Ceti stay aboard: 20 HT penalty if no other way gets them there.',
		);
		assert.equal(confirmText(game({ passengerGroups: PASSENGERS }), ME, 'Wayfarer', 'TAU_CETI'), 'Wayfarer jumps to Tau Ceti (58%).');
	});

	it('waits for the player whose slot it is', () => {
		assert.deepEqual(jumpState(game(), ME), { canAct: false, text: 'Waiting for Vega Lines' });
		assert.deepEqual(jumpState(game({ doneJumping: [VEGA] }), ME), { canAct: true, text: '' });
		assert.equal(jumpState(game({ doneJumping: [VEGA, ME] }), ME).text, 'Your jumps are done.');
		assert.equal(jumpState(game({ phase: 'TRADING' }), ME).canAct, false);
		assert.deepEqual(jumpState(game({ doneJumping: [VEGA], jumpedShips: ['Wayfarer'] }), ME), {
			canAct: true,
			text: 'Every ship that can jump has jumped: finish jumping to end your slot.',
		});
	});

	it('shows the Jumps order and where others jumped', () => {
		const order = jumpOrder(game({ doneJumping: [VEGA], jumpedShips: ['Meridian'] }));
		assert.deepEqual(order, [
			{ corporation: 'Vega Lines', state: 'done', jumps: ['Meridian → Tau Ceti'] },
			{ corporation: 'Orion Freight', state: 'now', jumps: [] },
		]);
	});
});
