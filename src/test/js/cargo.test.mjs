import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	MOVE_WHOLE_NUMBER,
	canMoveCargo,
	cargoPlaces,
	movableGoods,
	moveHint,
	moveMax,
	movedText,
	validateMove,
} from '../../main/resources/static/js/cargo.js';

const ME = 'me';

// Wayfarer is in port at Tau Ceti with 3 ISO and 5 CHIPS (8 spaces, 3 used); the warehouse there holds 6 ISO and 2 FOOD.
function game({
	status = 'RUNNING',
	location = 'PORT',
	shipCargo = { ISO: 3, CHIPS: 5 },
	shipUsed = 3,
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
				],
			},
			{ id: 'vega', ships: [{ name: 'Meridian', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT' }] },
		],
		you: {
			ships: [
				{ name: 'Wayfarer', cargo: shipCargo, cargoUsed: shipUsed, cargoSpaces: 8 },
				{ name: 'Lantern', cargo: {}, cargoUsed: 0, cargoSpaces: 8 },
			],
			warehouses,
		},
	};
}

const place = (options) => cargoPlaces(game(options), ME)[0];

describe('moving goods between a ship in port and a warehouse (R58, E08-S02)', () => {
	describe('canMoveCargo', () => {
		it('is true in a running game for a player with a warehouse', () => {
			assert.equal(canMoveCargo(game()), true);
		});

		it('is false with no warehouse, once the game is over, and for a viewer with nothing of their own', () => {
			assert.equal(canMoveCargo(game({ warehouses: [] })), false);
			assert.equal(canMoveCargo(game({ status: 'FINISHED' })), false);
			assert.equal(canMoveCargo({ ...game(), you: null }), false);
		});
	});

	describe('cargoPlaces (S02-7)', () => {
		it("lists the viewer's ships in port at a system where they have a warehouse", () => {
			const places = cargoPlaces(game(), ME);
			assert.equal(places.length, 1); // Lantern is in port at Mu Herculis, where there is no warehouse
			assert.equal(places[0].ship.name, 'Wayfarer');
			assert.equal(places[0].system, 'TAU_CETI');
			assert.equal(places[0].label, 'Wayfarer at Tau Ceti \u2014 ISO 3, CHIPS 5 \u00b7 cargo 3 / 8 used');
			assert.equal(places[0].warehouseLabel, 'Warehouse at Tau Ceti \u2014 ISO 6, FOOD 2 \u00b7 cargo 6 / 10 used');
		});

		it('leaves out a ship in Space or on the Planet', () => {
			assert.deepEqual(cargoPlaces(game({ location: 'SPACE' }), ME), []);
			assert.deepEqual(cargoPlaces(game({ location: 'PLANET' }), ME), []);
		});

		it("never lists another player's ship, and nothing without a warehouse", () => {
			assert.equal(cargoPlaces(game(), ME).some(entry => entry.ship.name === 'Meridian'), false);
			assert.deepEqual(cargoPlaces(game({ warehouses: [] }), ME), []);
		});

		it('says "empty" for a ship or warehouse holding nothing', () => {
			const empty = place({ shipCargo: {}, shipUsed: 0 });
			assert.equal(empty.label, 'Wayfarer at Tau Ceti \u2014 empty \u00b7 cargo 0 / 8 used');
		});
	});

	describe('movableGoods', () => {
		it('lists what the source holds: the warehouse for a move to the ship, the ship for a move to the warehouse', () => {
			assert.deepEqual(movableGoods(place(), 'SHIP'), [{ goods: 'ISO', units: 6 }, { goods: 'FOOD', units: 2 }]);
			assert.deepEqual(movableGoods(place(), 'WAREHOUSE'), [{ goods: 'ISO', units: 3 }, { goods: 'CHIPS', units: 5 }]);
		});

		it('is empty for a source holding nothing', () => {
			assert.deepEqual(movableGoods(place({ shipCargo: {}, shipUsed: 0 }), 'WAREHOUSE'), []);
		});
	});

	describe('moveMax and moveHint', () => {
		it("caps POLY and ISO by the destination's free cargo space (R42)", () => {
			// the warehouse holds 6 ISO, the ship has 5 free spaces
			assert.equal(moveMax(place(), 'ISO', 'SHIP'), 5);
			assert.equal(moveHint(place(), 'ISO', 'SHIP'), 'Up to 5 u: Wayfarer has 5 free cargo spaces.');
		});

		it('is everything the source holds when it all fits', () => {
			// the ship holds 3 ISO, the warehouse has 4 free spaces
			assert.equal(moveMax(place(), 'ISO', 'WAREHOUSE'), 3);
			assert.equal(moveHint(place(), 'ISO', 'WAREHOUSE'), 'Up to 3 u.');
		});

		it('never caps CHIPS and FOOD, which take no space', () => {
			const full = place({ shipCargo: { ISO: 8 }, shipUsed: 8 });
			assert.equal(moveMax(full, 'FOOD', 'SHIP'), 2);
			assert.equal(moveHint(full, 'FOOD', 'SHIP'), 'Up to 2 u.');
		});

		it('says when the destination is full or the source has nothing', () => {
			const full = place({ shipCargo: { ISO: 8 }, shipUsed: 8 });
			assert.equal(moveHint(full, 'ISO', 'SHIP'), 'Wayfarer has no free cargo space for ISO.');
			assert.equal(moveHint(place({ shipCargo: {}, shipUsed: 0 }), '', 'WAREHOUSE'), 'Wayfarer holds nothing to move.');
			assert.equal(moveHint(place(), 'POLY', 'SHIP'), 'Your warehouse at Tau Ceti holds nothing to move.');
		});

		it('uses the singular for one free space', () => {
			const nearlyFull = place({ shipCargo: { ISO: 7 }, shipUsed: 7 });
			assert.equal(moveHint(nearlyFull, 'ISO', 'SHIP'), 'Up to 1 u: Wayfarer has 1 free cargo space.');
		});
	});

	describe('validateMove (S02-2)', () => {
		it('builds the request for a move that can be made', () => {
			assert.deepEqual(validateMove(place(), 'ISO', 'SHIP', ' 4 '), { body: { ship: 'Wayfarer', goods: 'ISO', units: 4, to: 'SHIP' } });
			assert.deepEqual(validateMove(place(), 'CHIPS', 'WAREHOUSE', '5'), { body: { ship: 'Wayfarer', goods: 'CHIPS', units: 5, to: 'WAREHOUSE' } });
		});

		it('refuses anything but a whole number, 1 or more', () => {
			for (const text of ['', '0', '-1', '2.5', 'two']) {
				assert.deepEqual(validateMove(place(), 'ISO', 'SHIP', text), { error: MOVE_WHOLE_NUMBER });
			}
		});

		it('refuses more than the source holds, with the message the server gives', () => {
			assert.deepEqual(validateMove(place(), 'ISO', 'WAREHOUSE', '4'), { error: 'Wayfarer has only 3 ISO.' });
			assert.deepEqual(validateMove(place(), 'FOOD', 'SHIP', '3'), { error: 'Your warehouse at Tau Ceti has only 2 FOOD.' });
		});

		it('refuses more than fits rather than losing goods', () => {
			assert.deepEqual(validateMove(place(), 'ISO', 'SHIP', '6'), { error: 'Wayfarer has room for only 5 more ISO.' });
		});
	});

	it('says what was moved, from where to where', () => {
		assert.equal(movedText(place(), 'ISO', 4, 'SHIP'), 'Moved 4 ISO from your warehouse at Tau Ceti to Wayfarer.');
		assert.equal(movedText(place(), 'CHIPS', 5, 'WAREHOUSE'), 'Moved 5 CHIPS from Wayfarer to your warehouse at Tau Ceti.');
	});
});
