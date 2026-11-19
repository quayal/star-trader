import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	MOVE_WHOLE_NUMBER,
	canMoveCargo,
	cargoHolders,
	destinationsFor,
	hasWarehouse,
	movableGoods,
	moveHint,
	moveMax,
	moveSlider,
	movedText,
	sliderText,
	validateMove,
} from '../../main/resources/static/js/cargo.js';

const ME = 'me';

// Wayfarer is in port at Tau Ceti with 3 ISO and 5 CHIPS (8 spaces, 3 used); the warehouse there holds 6 ISO and 2 FOOD.
// Lantern is in port at Mu Herculis, alone. With `comet`, Comet is in port at Tau Ceti too, empty.
function game({
	status = 'RUNNING',
	location = 'PORT',
	shipCargo = { ISO: 3, CHIPS: 5 },
	shipUsed = 3,
	comet = false,
	cometLocation = 'PORT',
	warehouses = [{ system: 'TAU_CETI', systemName: 'Tau Ceti', blocks: 1, cargoSpaces: 10, cargo: { ISO: 6, FOOD: 2 }, cargoUsed: 6 }],
} = {}) {
	return {
		id: 'g-1',
		status,
		players: [
			{
				id: ME,
				ships: [
					{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location },
					{ name: 'Lantern', system: 'MU_HERCULIS', systemName: 'Mu Herculis', location: 'PORT' },
					...(comet ? [{ name: 'Comet', system: 'TAU_CETI', systemName: 'Tau Ceti', location: cometLocation }] : []),
				],
			},
			{ id: 'vega', ships: [{ name: 'Meridian', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT' }] },
		],
		you: {
			ships: [
				{ name: 'Wayfarer', cargo: shipCargo, cargoUsed: shipUsed, cargoSpaces: 8 },
				{ name: 'Lantern', cargo: {}, cargoUsed: 0, cargoSpaces: 8 },
				...(comet ? [{ name: 'Comet', cargo: {}, cargoUsed: 0, cargoSpaces: 8 }] : []),
			],
			warehouses,
		},
	};
}

const holders = (options, system = 'TAU_CETI') => cargoHolders(game(options), ME, system);
const holder = (key, options) => holders(options).find(entry => entry.key === key);
const ship = options => holder('ship:Wayfarer', options);
const warehouse = options => holder('warehouse:TAU_CETI', options);
const comet = options => holder('ship:Comet', { comet: true, ...options });

describe('moving goods between two holders in the same port (R58, E08-S02, revised 2026-11-17)', () => {
	describe('canMoveCargo and hasWarehouse', () => {
		it('shows the panel in a running game for a player in it, warehouse or not', () => {
			assert.equal(canMoveCargo(game()), true);
			assert.equal(canMoveCargo(game({ warehouses: [] })), true);
			assert.equal(canMoveCargo(game({ status: 'FINISHED' })), false);
			assert.equal(canMoveCargo({ ...game(), you: null }), false);
		});

		it("points Infrastructure to a system's page only for a player with a warehouse", () => {
			assert.equal(hasWarehouse(game()), true);
			assert.equal(hasWarehouse(game({ warehouses: [] })), false);
		});
	});

	describe('cargoHolders (S02-7)', () => {
		it('lists a ship in port with the warehouse at its system', () => {
			const listed = holders();
			assert.deepEqual(listed.map(entry => entry.key), ['ship:Wayfarer', 'warehouse:TAU_CETI']);
			assert.equal(listed[0].label, 'Wayfarer at Tau Ceti — ISO 3, CHIPS 5 · cargo 3 / 8 used');
			assert.equal(listed[1].label, 'Warehouse at Tau Ceti — ISO 6, FOOD 2 · cargo 6 / 10 used');
			assert.equal(listed[1].name, 'Your warehouse at Tau Ceti');
		});

		it('lists two ships in the same port, with no warehouse there', () => {
			assert.deepEqual(holders({ comet: true, warehouses: [] }).map(entry => entry.key), ['ship:Wayfarer', 'ship:Comet']);
		});

		it('lists the warehouse once however many ships are in its port', () => {
			assert.deepEqual(holders({ comet: true }).map(entry => entry.key), ['ship:Wayfarer', 'warehouse:TAU_CETI', 'ship:Comet']);
		});

		it('lists only the holders at the system whose page it is (creator, 2026-11-19)', () => {
			assert.equal(holders().some(entry => entry.ship === 'Lantern'), false);
			assert.deepEqual(holders({ comet: true }, 'MU_HERCULIS'), []);
		});

		it('leaves out a ship alone in its port, and one in Space or on the Planet', () => {
			// Lantern is alone at Mu Herculis
			assert.deepEqual(holders({}, 'MU_HERCULIS'), []);
			assert.deepEqual(holders({ location: 'SPACE' }), []);
			assert.deepEqual(holders({ location: 'PLANET' }), []);
			assert.deepEqual(holders({ comet: true, cometLocation: 'SPACE', warehouses: [] }), []);
		});

		it("never lists another player's ship", () => {
			assert.equal(holders({ comet: true }).some(entry => entry.ship === 'Meridian'), false);
		});

		it('says "empty" for a holder with nothing in it', () => {
			assert.equal(ship({ shipCargo: {}, shipUsed: 0 }).label, 'Wayfarer at Tau Ceti — empty · cargo 0 / 8 used');
		});
	});

	describe('destinationsFor', () => {
		it('is every other holder in the same port', () => {
			const listed = holders({ comet: true });
			assert.deepEqual(destinationsFor(listed, listed[0]).map(entry => entry.key), ['warehouse:TAU_CETI', 'ship:Comet']);
			assert.deepEqual(destinationsFor(listed, listed[1]).map(entry => entry.key), ['ship:Wayfarer', 'ship:Comet']);
			assert.deepEqual(destinationsFor(listed, undefined), []);
		});
	});

	describe('movableGoods', () => {
		it('lists what the source holds', () => {
			assert.deepEqual(movableGoods(warehouse()), [{ goods: 'ISO', units: 6 }, { goods: 'FOOD', units: 2 }]);
			assert.deepEqual(movableGoods(ship()), [{ goods: 'ISO', units: 3 }, { goods: 'CHIPS', units: 5 }]);
		});

		it('is empty for a source holding nothing, or none', () => {
			assert.deepEqual(movableGoods(ship({ shipCargo: {}, shipUsed: 0 })), []);
			assert.deepEqual(movableGoods(undefined), []);
		});
	});

	describe('moveSlider and sliderText (creator, 2026-11-19)', () => {
		it('runs from 1 to the most that can move, starting at the most', () => {
			assert.deepEqual(moveSlider(warehouse(), ship(), 'ISO', ''), { max: 5, value: 5 });
			assert.equal(sliderText(5, 5), '5 of 5 u');
		});

		it('keeps a pick that still fits, and drops one that no longer does', () => {
			assert.deepEqual(moveSlider(warehouse(), ship(), 'ISO', '2'), { max: 5, value: 2 });
			assert.deepEqual(moveSlider(ship(), warehouse(), 'ISO', '5'), { max: 3, value: 3 });
		});

		it('is empty when nothing can move', () => {
			assert.deepEqual(moveSlider(warehouse(), ship({ shipUsed: 8 }), 'ISO', '3'), { max: 0, value: 0 });
			assert.deepEqual(moveSlider(undefined, ship(), 'ISO', '3'), { max: 0, value: 0 });
			assert.equal(sliderText(0, 0), '0 u');
		});
	});

	describe('moveMax and moveHint', () => {
		it("caps POLY and ISO by the destination's free cargo space (R42)", () => {
			// the warehouse holds 6 ISO, the ship has 5 free spaces
			assert.equal(moveMax(warehouse(), ship(), 'ISO'), 5);
			assert.equal(moveHint(warehouse(), ship(), 'ISO'), 'Up to 5 u: Wayfarer has 5 free cargo spaces.');
		});

		it('is everything the source holds when it all fits', () => {
			assert.equal(moveMax(ship(), warehouse(), 'ISO'), 3);
			assert.equal(moveHint(ship(), warehouse(), 'ISO'), 'Up to 3 u.');
			assert.equal(moveHint(ship({ comet: true }), comet(), 'ISO'), 'Up to 3 u.');
		});

		it('never caps CHIPS and FOOD, which take no space', () => {
			const full = { shipCargo: { ISO: 8 }, shipUsed: 8 };
			assert.equal(moveMax(warehouse(full), ship(full), 'FOOD'), 2);
		});

		it('says when the destination is full or the source has nothing', () => {
			const full = { shipCargo: { ISO: 8 }, shipUsed: 8 };
			assert.equal(moveHint(warehouse(full), ship(full), 'ISO'), 'Wayfarer has no free cargo space for ISO.');
			assert.equal(moveHint(ship({ shipCargo: {}, shipUsed: 0 }), warehouse(), ''), 'Wayfarer holds nothing to move.');
			assert.equal(moveHint(warehouse(), ship(), 'POLY'), 'Your warehouse at Tau Ceti holds nothing to move.');
			assert.equal(moveHint(undefined, ship(), 'ISO'), '');
		});

		it('uses the singular for one free space', () => {
			const nearlyFull = { shipCargo: { ISO: 7 }, shipUsed: 7 };
			assert.equal(moveHint(warehouse(nearlyFull), ship(nearlyFull), 'ISO'), 'Up to 1 u: Wayfarer has 1 free cargo space.');
		});
	});

	describe('validateMove (S02-2)', () => {
		it('builds the request, leaving the warehouse end out', () => {
			assert.deepEqual(validateMove(warehouse(), ship(), 'ISO', ' 4 '), { body: { goods: 'ISO', units: 4, to: 'Wayfarer' } });
			assert.deepEqual(validateMove(ship(), warehouse(), 'CHIPS', '5'), { body: { goods: 'CHIPS', units: 5, from: 'Wayfarer' } });
			assert.deepEqual(validateMove(ship({ comet: true }), comet(), 'ISO', '2'), { body: { goods: 'ISO', units: 2, from: 'Wayfarer', to: 'Comet' } });
		});

		it('refuses anything but a whole number, 1 or more', () => {
			for (const text of ['', '0', '-1', '2.5', 'two']) {
				assert.deepEqual(validateMove(warehouse(), ship(), 'ISO', text), { error: MOVE_WHOLE_NUMBER });
			}
		});

		it('refuses more than the source holds, with the message the server gives', () => {
			assert.deepEqual(validateMove(ship(), warehouse(), 'ISO', '4'), { error: 'Wayfarer has only 3 ISO.' });
			assert.deepEqual(validateMove(warehouse(), ship(), 'FOOD', '3'), { error: 'Your warehouse at Tau Ceti has only 2 FOOD.' });
		});

		it('refuses more than fits rather than losing goods', () => {
			assert.deepEqual(validateMove(warehouse(), ship(), 'ISO', '6'), { error: 'Wayfarer has room for only 5 more ISO.' });
		});
	});

	it('says what was moved, from where to where', () => {
		assert.equal(movedText(warehouse(), ship(), 'ISO', 4), 'Moved 4 ISO from your warehouse at Tau Ceti to Wayfarer.');
		assert.equal(movedText(ship(), warehouse(), 'CHIPS', 5), 'Moved 5 CHIPS from Wayfarer to your warehouse at Tau Ceti.');
		assert.equal(movedText(ship({ comet: true }), comet(), 'ISO', 2), 'Moved 2 ISO from Wayfarer to Comet.');
	});
});
