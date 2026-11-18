import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { NO_FACTORIES_TEXT, assetsView } from '../../main/resources/static/js/assets.js';

describe('assetsView (creator, 2026-11-18)', () => {
	const game = {
		you: {
			money: 120,
			assets: {
				shipsValue: 200,
				goodsValue: 12,
				factoryOutputValue: 0,
				ships: [{
					name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT', saleValue: 200, goodsValue: 12,
					goods: [{ goods: 'FOOD', units: 2, price: null, value: 0 }, { goods: 'ISO', units: 3, price: 4, value: 12 }],
				}],
				warehouses: [{ system: 'MU_HERCULIS', systemName: 'Mu Herculis', blocks: 1, goods: [], goodsValue: 0 }],
				factories: [],
			},
		},
	};

	it('sums up cash, ships at sale value and goods at today\'s prices', () => {
		assert.deepEqual(assetsView(game).summary.map(item => item.value), ['120 HT', '200 HT', '12 HT', '332 HT', '0 HT']);
	});

	it('shows each ship\'s goods at the price where it is, and says where a good isn\'t traded', () => {
		const [ship] = assetsView(game).ships;

		assert.equal(ship.where, 'Tau Ceti · Port');
		assert.deepEqual(ship.goods, [
			{ units: 2, goods: 'FOOD', detail: '(not traded here)' },
			{ units: 3, goods: 'ISO', detail: '× 4 HT = 12 HT' },
		]);
	});

	it('says when there is nothing of a kind, and is null when no assets were sent', () => {
		const view = assetsView(game);

		assert.equal(view.warehouses[0].blocks, '1 block');
		assert.equal(view.notes.factories, NO_FACTORIES_TEXT);
		assert.equal(view.notes.ships, '');
		assert.equal(assetsView({ you: null }), null);
	});
});
