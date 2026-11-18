import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { NO_FACTORIES_TEXT, NO_GOODS_TEXT, NOT_TRADED_TEXT, assetsView } from '../../main/resources/static/js/assets.js';

describe('assetsView (creator, 2026-11-18)', () => {
	const game = {
		you: {
			money: 120,
			assets: {
				shipsValue: 200,
				goodsValue: 56,
				factoryOutputValue: 0,
				goods: [
					{ goods: 'FOOD', units: 2, averagePrice: 0, value: 0, lots: [{ units: 2, system: 'TAU_CETI', systemName: 'Tau Ceti', ship: 'Wayfarer' }] },
					{
						goods: 'ISO', units: 8, averagePrice: 7, value: 56, lots: [
							{ units: 3, system: 'TAU_CETI', systemName: 'Tau Ceti', ship: 'Wayfarer' },
							{ units: 5, system: 'MU_HERCULIS', systemName: 'Mu Herculis', ship: null },
						],
					},
				],
				warehouses: [{ system: 'MU_HERCULIS', systemName: 'Mu Herculis', blocks: 1 }],
				factories: [],
			},
		},
	};

	it('sums up cash, ships at sale value and goods at mean prices', () => {
		assert.deepEqual(assetsView(game).summary.map(item => item.value), ['120 HT', '200 HT', '56 HT', '376 HT', '0 HT']);
	});

	it('shows each good once, with its total, its mean price and a line per ship or warehouse holding it', () => {
		const [food, iso] = assetsView(game).goods;

		assert.deepEqual([iso.units, iso.price, iso.value], ['8 u', '7 HT avg', '56 HT']);
		assert.deepEqual(iso.lots.map(lot => [lot.units, lot.holder, lot.ship, lot.systemName]), [
			['3 u', 'Wayfarer', 'Wayfarer', 'Tau Ceti'],
			['5 u', 'Warehouse', null, 'Mu Herculis'],
		]);
		assert.equal(food.price, NOT_TRADED_TEXT);
	});

	it('says when there is nothing of a kind, and is null when no assets were sent', () => {
		const view = assetsView(game);

		assert.equal(view.warehouses[0].blocks, '1 block');
		assert.equal(view.notes.factories, NO_FACTORIES_TEXT);
		assert.equal(view.notes.goods, '');
		assert.equal(assetsView({ you: { money: 0, assets: { ...game.you.assets, goods: [] } } }).notes.goods, NO_GOODS_TEXT);
		assert.equal(assetsView({ you: null }), null);
	});
});
