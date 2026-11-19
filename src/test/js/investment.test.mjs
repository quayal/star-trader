import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	ALREADY_CLOSED_INVESTING,
	BLOCKS_WHOLE_NUMBER,
	UNITS_WHOLE_NUMBER,
	COLLECT_WHOLE_NUMBER,
	blocksCostText,
	blocksText,
	canBuildFactory,
	canBuy,
	collectDefaults,
	collectFactories,
	collectHeading,
	collectKey,
	collectLoss,
	collectWarning,
	destinationLabel,
	isCollecting,
	validateCollect,
	INVESTMENT_CLOSED_BY_YOU,
	doneInvestingAcceptedText,
	doneInvestingRefusedText,
	isDoneInvesting,
	infrastructureState,
	factoriesAt,
	factorySystems,
	goodsOptionLabel,
	investFactories,
	investSystems,
	infrastructureStatusText,
	isInvesting,
	isInvestmentClosedRefusal,
	ownFactoryRows,
	ownWarehouseRows,
	parseBlocks,
	parseUnits,
	systemOptionLabel,
	unitsCostText,
	unitsText,
	validateBlocks,
	validateUnits,
	warehouseText,
	waitingForInvesting,
} from '../../main/resources/static/js/investment.js';
import { phaseText } from '../../main/resources/static/js/bidding.js';

const ME = 'me';

function game({ phase = 'INVESTMENT', doneInvesting = [], status = 'RUNNING' } = {}) {
	return {
		status,
		turns: 20,
		turn: { number: 4, phase, doneBidding: [], doneInvesting },
		players: [
			{ id: 'vega', corporationName: 'Vega Lines' },
			{ id: ME, corporationName: 'Orion Freight' },
			{ id: 'kessler', corporationName: 'Kessler Cargo' },
		],
	};
}

describe('isInvesting', () => {
	it('is true only for a running game in Investment', () => {
		assert.equal(isInvesting(game()), true);
		assert.equal(isInvesting(game({ phase: 'TRADING' })), false);
		assert.equal(isInvesting(game({ status: 'FINISHED' })), false);
	});
});

describe('isDoneInvesting', () => {
	it('is true only for a player in the done list during Investment', () => {
		assert.equal(isDoneInvesting(game({ doneInvesting: [ME] }), ME), true);
		assert.equal(isDoneInvesting(game({ doneInvesting: ['vega'] }), ME), false);
		assert.equal(isDoneInvesting(game({ phase: 'BIDDING', doneInvesting: [ME] }), ME), false);
	});
});

describe('waitingForInvesting (E06-S01-3)', () => {
	it('names you first, then the others in join order, while nobody is done', () => {
		assert.deepEqual(waitingForInvesting(game(), ME), ['you', 'Vega Lines', 'Kessler Cargo']);
	});

	it('leaves out players who signalled done, you included', () => {
		assert.deepEqual(waitingForInvesting(game({ doneInvesting: ['vega'] }), ME), ['you', 'Kessler Cargo']);
		assert.deepEqual(waitingForInvesting(game({ doneInvesting: [ME] }), ME), ['Vega Lines', 'Kessler Cargo']);
	});

	it('waits for nobody outside Investment', () => {
		assert.deepEqual(waitingForInvesting(game({ phase: 'TRADING' }), ME), []);
	});
});

describe('the top bar during Investment', () => {
	it('names who the game is waiting for', () => {
		assert.equal(phaseText(game(), ME), 'Turn 4 of 20 · Investment — waiting for you, Vega Lines, Kessler Cargo');
		assert.equal(phaseText(game({ doneInvesting: [ME] }), ME), 'Turn 4 of 20 · Investment — waiting for Vega Lines, Kessler Cargo');
	});
});

describe('done investing messages', () => {
	it('says nothing more while others still invest, or that your signal closed Investment', () => {
		assert.equal(doneInvestingAcceptedText(game()), '');
		assert.equal(doneInvestingAcceptedText(game({ phase: 'BIDDING' })), INVESTMENT_CLOSED_BY_YOU);
	});

	it('words a close that raced the click as already closed (A04)', () => {
		assert.equal(isInvestmentClosedRefusal(409, "Investment isn't open."), true);
		assert.equal(isInvestmentClosedRefusal(409, 'This game is over'), false);
		assert.equal(doneInvestingRefusedText(409, "Investment isn't open.", 'fallback'), ALREADY_CLOSED_INVESTING);
	});

	it('shows the server message otherwise, or the fallback', () => {
		assert.equal(doneInvestingRefusedText(409, 'This game is over', 'fallback'), 'This game is over');
		assert.equal(doneInvestingRefusedText(500, '', 'fallback'), 'fallback');
	});
});

// ── Collect (E06-S03) ──

const iso = {
	system: 'TAU_CETI',
	systemName: 'Tau Ceti',
	goods: 'ISO',
	units: 6,
	destinations: [{ ship: null, freeSpaces: 4 }, { ship: 'Wayfarer', freeSpaces: 10 }],
};
const chips = {
	system: 'MU_HERCULIS',
	systemName: 'Mu Herculis',
	goods: 'CHIPS',
	units: 8,
	destinations: [{ ship: 'Lantern', freeSpaces: null }],
};
const stranded = { system: 'SIGMA_DRACONIS', systemName: 'Sigma Draconis', goods: 'FOOD', units: 5, destinations: [] };

function collecting(factories = [iso, chips], overrides = {}) {
	return { ...game(), id: 'g1', you: { collect: { factories } }, ...overrides };
}

describe('isCollecting (S03-1, S03-5)', () => {
	it('is true in Investment while the viewer has output to place', () => {
		assert.equal(isCollecting(collecting()), true);
	});

	it('is false once they confirmed, with no factories, or outside Investment', () => {
		assert.equal(isCollecting({ ...collecting(), you: { collect: null } }), false);
		assert.equal(isCollecting({ ...game(), you: {} }), false);
		assert.equal(isCollecting({ ...collecting(), turn: { ...game().turn, phase: 'TRADING' } }), false);
		assert.equal(isCollecting({ ...collecting(), status: 'FINISHED' }), false);
	});
});

describe('collect screen state', () => {
	it('is keyed by game and turn, so a typed amount never reaches the next game (lesson 2026-11-04)', () => {
		assert.equal(collectKey(collecting()), 'g1/4');
		assert.notEqual(collectKey(collecting()), collectKey({ ...collecting(), id: 'g2' }));
	});

	it('lists the viewer\'s factories, or none', () => {
		assert.deepEqual(collectFactories(collecting()), [iso, chips]);
		assert.deepEqual(collectFactories(game()), []);
	});
});

describe('collect wording (S03-1, S03-2)', () => {
	it('heads a factory with its good, system and units', () => {
		assert.equal(collectHeading(iso), 'ISO at Tau Ceti \u2014 6 units');
		assert.equal(collectHeading({ ...iso, units: 1 }), 'ISO at Tau Ceti \u2014 1 unit');
	});

	it('shows free space for POLY and ISO and none for CHIPS and FOOD', () => {
		assert.equal(destinationLabel(iso.destinations[0]), 'Warehouse (4 free)');
		assert.equal(destinationLabel(iso.destinations[1]), 'Wayfarer (10 free)');
		assert.equal(destinationLabel(chips.destinations[0]), 'Lantern');
	});
});

describe('collectDefaults', () => {
	it('fills the destinations in order up to their free space', () => {
		assert.deepEqual(collectDefaults(iso), [4, 2]);
	});

	it('puts all CHIPS and FOOD in the first destination', () => {
		assert.deepEqual(collectDefaults(chips), [8]);
	});

	it('leaves what nothing can hold unplaced, and has no amounts with no destination', () => {
		assert.deepEqual(collectDefaults({ ...iso, units: 20 }), [4, 10]);
		assert.deepEqual(collectDefaults(stranded), []);
	});
});

describe('collectLoss and collectWarning (S03-3, S03-4)', () => {
	it('loses nothing when everything fits', () => {
		assert.deepEqual(collectLoss(iso, [4, 2]), { unplaced: 0, overflow: 0 });
		assert.equal(collectWarning([iso, chips], [[4, 2], [8]]), '');
	});

	it('counts units over a destination\'s free space as lost', () => {
		assert.deepEqual(collectLoss(iso, [6, 0]), { unplaced: 0, overflow: 2 });
		assert.equal(collectWarning([iso], [[6, 0]]), "2 ISO at Tau Ceti will be lost (2 won't fit).");
	});

	it('counts units left out as lost', () => {
		assert.deepEqual(collectLoss(iso, [1, 0]), { unplaced: 5, overflow: 0 });
		assert.equal(collectWarning([iso], [[1, 0]]), '5 ISO at Tau Ceti will be lost (5 not placed).');
	});

	it('names both reasons and every factory that loses something', () => {
		assert.equal(collectWarning([iso, chips], [[6, 0], [3]]),
			"2 ISO at Tau Ceti will be lost (2 won't fit). 5 CHIPS at Mu Herculis will be lost (5 not placed).");
		// 5 in a warehouse with 4 free: 1 doesn't fit, and 1 of the 6 isn't placed at all
		assert.equal(collectWarning([iso], [[5, 0]]), "2 ISO at Tau Ceti will be lost (1 won't fit, 1 not placed).");
	});

	it('says all of it is lost when the player has nowhere there to keep CHIPS or FOOD', () => {
		assert.equal(collectWarning([stranded], [[]]), 'All 5 FOOD at Sigma Draconis will be lost: you have no warehouse or ship there to keep it.');
	});
});

describe('validateCollect (S03-5)', () => {
	it('builds the request from the typed amounts, leaving out zeros', () => {
		assert.deepEqual(validateCollect([iso, chips], [['4', '2'], ['0']]).body, {
			placements: [
				{ system: 'TAU_CETI', goods: 'ISO', ship: null, units: 4 },
				{ system: 'TAU_CETI', goods: 'ISO', ship: 'Wayfarer', units: 2 },
			],
		});
	});

	it('treats an empty input as 0, and allows placing nothing (all lost)', () => {
		assert.deepEqual(validateCollect([iso], [['', '']]).body, { placements: [] });
	});

	it('refuses an amount that isn\'t a whole number, 0 or more, pointing at it', () => {
		assert.deepEqual(validateCollect([iso, chips], [['4', '2'], ['2.5']]), { error: COLLECT_WHOLE_NUMBER, factory: 1, destination: 0 });
		assert.equal(validateCollect([iso], [['-1', '0']]).error, COLLECT_WHOLE_NUMBER);
		assert.equal(validateCollect([iso], [['abc', '0']]).error, COLLECT_WHOLE_NUMBER);
	});

	it('refuses more than the factory makes', () => {
		assert.deepEqual(validateCollect([iso], [['4', '3']]), { error: 'ISO at Tau Ceti: 7 placed, but the factory makes 6.', factory: 0, destination: 0 });
	});

	it('accepts a factory with no destination, so its goods are lost', () => {
		assert.deepEqual(validateCollect([stranded], [[]]).body, { placements: [] });
	});
});

describe('the Infrastructure screen (E06-S08)', () => {
	const withYou = (extra, options) => ({ id: 'g-1', ...game(options), ...extra });

	describe('infrastructureState (S08-3, S08-4, S08-6)', () => {
		it('is closed outside Investment, whatever the viewer has', () => {
			assert.equal(infrastructureState(withYou({ you: { collect: null } }, { phase: 'TRADING' }), ME), 'closed');
			assert.equal(infrastructureState(withYou({}, { status: 'FINISHED' }), ME), 'closed');
		});

		it('is collect while the viewer still has output to place', () => {
			assert.equal(infrastructureState(withYou({ you: { collect: { factories: [] } } }), ME), 'collect');
		});

		it('is invest once collected (or with no factories), then done after signalling', () => {
			assert.equal(infrastructureState(withYou({ you: { collect: null } }), ME), 'invest');
			assert.equal(infrastructureState(withYou({ you: { collect: null } }, { doneInvesting: [ME] }), ME), 'done');
		});
	});

	describe('infrastructureStatusText (S08-4)', () => {
		it('says who the game still waits for once the viewer is done', () => {
			const done = withYou({ you: { collect: null } }, { doneInvesting: [ME, 'vega'] });
			assert.equal(infrastructureStatusText(done, ME), 'You signalled done investing. Waiting for Kessler Cargo.');
		});

		it('says only that the viewer is done when nobody else is left', () => {
			const all = withYou({ you: { collect: null } }, { doneInvesting: [ME, 'vega', 'kessler'] });
			assert.equal(infrastructureStatusText(all, ME), 'You signalled done investing');
		});

		it('is empty in every other state', () => {
			assert.equal(infrastructureStatusText(withYou({ you: { collect: null } }), ME), '');
			assert.equal(infrastructureStatusText(withYou({ you: { collect: { factories: [] } } }), ME), '');
			assert.equal(infrastructureStatusText(withYou({}, { phase: 'TRADING' }), ME), '');
		});
	});

	describe('ownWarehouseRows (S08-6)', () => {
		it('lists the viewer\'s warehouses with their space and what they hold', () => {
			const rows = ownWarehouseRows(withYou({
				you: {
					warehouses: [
						{ system: 'TAU_CETI', systemName: 'Tau Ceti', blocks: 2, cargoSpaces: 20, cargo: { ISO: 4, CHIPS: 9 }, cargoUsed: 4 },
						{ system: 'MU_HERCULIS', systemName: 'Mu Herculis', blocks: 1, cargoSpaces: 10, cargo: {}, cargoUsed: 0 },
					],
				},
			}));
			assert.deepEqual(rows, [
				{ system: 'TAU_CETI', systemName: 'Tau Ceti', blocks: 2, cargoUsed: 4, cargoSpaces: 20, held: '4 ISO, 9 CHIPS' },
				{ system: 'MU_HERCULIS', systemName: 'Mu Herculis', blocks: 1, cargoUsed: 0, cargoSpaces: 10, held: 'Empty' },
			]);
		});

		it('is empty with none, or without a `you`', () => {
			assert.deepEqual(ownWarehouseRows(withYou({ you: { warehouses: [] } })), []);
			assert.deepEqual(ownWarehouseRows(withYou({ you: null })), []);
		});
	});

	describe('ownFactoryRows (S08-6)', () => {
		const board = [{ id: 'TAU_CETI', name: 'Tau Ceti' }, { id: 'MU_HERCULIS', name: 'Mu Herculis' }];

		it('lists only the viewer\'s factories, named by system', () => {
			const rows = ownFactoryRows({
				...game(),
				board,
				players: [
					{ id: 'vega', factories: [{ system: 'MU_HERCULIS', goods: 'CHIPS', size: 3 }] },
					{ id: ME, factories: [{ system: 'TAU_CETI', goods: 'ISO', size: 6 }] },
				],
			}, ME);
			assert.deepEqual(rows, [{ systemName: 'Tau Ceti', goods: 'ISO', size: 6 }]);
		});

		it('is empty for a viewer with none', () => {
			assert.deepEqual(ownFactoryRows({ ...game(), board, players: [{ id: ME, factories: [] }] }, ME), []);
		});
	});
});

describe('buying warehouse blocks (E06-S04)', () => {
	const invest = { blockCost: 20, systems: [{ system: 'TAU_CETI', systemName: 'Tau Ceti', blocks: 2 }, { system: 'MU_HERCULIS', systemName: 'Mu Herculis', blocks: 0 }] };
	const withInvest = (value, options) => ({ id: 'g-1', ...game(options), you: { money: 300, invest: value } });

	describe('canBuy and investSystems (S04-1, S04-2)', () => {
		it('is true in Investment once the viewer has something to buy from', () => {
			assert.equal(canBuy(withInvest(invest)), true);
			assert.deepEqual(investSystems(withInvest(invest)).map(s => s.system), ['TAU_CETI', 'MU_HERCULIS']);
		});

		it('is false while they still have to collect, outside Investment and for a finished game', () => {
			assert.equal(canBuy(withInvest(null)), false);
			assert.equal(canBuy(withInvest(invest, { phase: 'TRADING' })), false);
			assert.equal(canBuy(withInvest(invest, { status: 'FINISHED' })), false);
			assert.deepEqual(investSystems(withInvest(null)), []);
		});
	});

	describe('what they own (S04-3)', () => {
		it('says "no warehouse yet", or the blocks with 10 cargo spaces each', () => {
			assert.equal(warehouseText(0), 'no warehouse yet');
			assert.equal(warehouseText(1), '1 block, 10 cargo spaces');
			assert.equal(warehouseText(3), '3 blocks, 30 cargo spaces');
			assert.equal(blocksText(1), '1 block');
			assert.equal(blocksText(2), '2 blocks');
		});

		it('names a system in the picker with what is there', () => {
			assert.equal(systemOptionLabel(invest.systems[0]), 'Tau Ceti \u2014 2 blocks, 20 cargo spaces');
			assert.equal(systemOptionLabel(invest.systems[1]), 'Mu Herculis \u2014 no warehouse yet');
		});
	});

	describe('parseBlocks', () => {
		it('accepts a whole number of 1 or more, trimmed', () => {
			assert.equal(parseBlocks('3'), 3);
			assert.equal(parseBlocks(' 12 '), 12);
		});

		it('refuses anything else', () => {
			for (const text of ['', '0', '-1', '1.5', 'abc', '2x', null, undefined]) {
				assert.equal(parseBlocks(text), null, String(text));
			}
		});
	});

	describe('blocksCostText (S04-1)', () => {
		it('shows the total at the block price, in the singular for one', () => {
			assert.equal(blocksCostText('3', 20), '3 blocks cost 60 HT');
			assert.equal(blocksCostText('1', 20), '1 block costs 20 HT');
		});

		it('is empty while the number is not valid', () => {
			assert.equal(blocksCostText('', 20), '');
			assert.equal(blocksCostText('x', 20), '');
		});
	});

	describe('validateBlocks (S04-2)', () => {
		it('returns the blocks when they are whole and affordable, up to all the money', () => {
			assert.deepEqual(validateBlocks('2', 20, 300), { blocks: 2 });
			assert.deepEqual(validateBlocks('15', 20, 300), { blocks: 15 });
		});

		it('gives the server\'s own message for a number that is not whole, 1 or more', () => {
			assert.deepEqual(validateBlocks('0', 20, 300), { error: BLOCKS_WHOLE_NUMBER });
			assert.deepEqual(validateBlocks('1.5', 20, 300), { error: BLOCKS_WHOLE_NUMBER });
		});

		it('says what it costs and what they have when it is too much', () => {
			assert.deepEqual(validateBlocks('16', 20, 300), { error: '16 blocks cost 320 HT; you have 300 HT.' });
			assert.deepEqual(validateBlocks('1', 20, 10), { error: '1 block costs 20 HT; you have 10 HT.' });
		});
	});
});

describe('building or extending a factory (E06-S05)', () => {
	const iso = { system: 'TAU_CETI', systemName: 'Tau Ceti', goods: 'ISO', size: 6, price: 12, unitCost: 70 };
	const poly = { system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis', goods: 'POLY', size: 0, price: 2, unitCost: 20 };
	const withInvest = (factories, options) => ({
		id: 'g-1', ...game(options),
		you: { money: 300, invest: factories === null ? null : { blockCost: 20, systems: [], factories } },
	});

	describe('canBuildFactory and investFactories (S05-1, S05-5)', () => {
		it('is true in Investment with a good to build somewhere', () => {
			assert.equal(canBuildFactory(withInvest([iso, poly])), true);
			assert.deepEqual(investFactories(withInvest([iso, poly])).map(f => f.goods), ['ISO', 'POLY']);
		});

		it('is false with nothing to build (ships only at Beta Hydri or Epsilon Eridani), while collecting and outside Investment', () => {
			assert.equal(canBuildFactory(withInvest([])), false);
			assert.equal(canBuildFactory(withInvest(null)), false);
			assert.equal(canBuildFactory(withInvest([iso], { phase: 'TRADING' })), false);
			assert.deepEqual(investFactories(withInvest(null)), []);
		});
	});

	describe('the System and Goods dropdowns (S05-1)', () => {
		const sigmaIso = { system: 'SIGMA_DRACONIS', systemName: 'Sigma Draconis', goods: 'ISO', size: 0, price: 10, unitCost: 60 };
		const sigmaFood = { system: 'SIGMA_DRACONIS', systemName: 'Sigma Draconis', goods: 'FOOD', size: 2, price: 15, unitCost: 85 };
		const all = withInvest([poly, iso, sigmaFood, sigmaIso]);

		it('lists each system that can produce something once, in the order given', () => {
			assert.deepEqual(factorySystems(all), [
				{ system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis' },
				{ system: 'TAU_CETI', systemName: 'Tau Ceti' },
				{ system: 'SIGMA_DRACONIS', systemName: 'Sigma Draconis' },
			]);
			assert.deepEqual(factorySystems(withInvest([])), []);
			assert.deepEqual(factorySystems(withInvest(null)), []);
		});

		it('lists what a system can produce', () => {
			assert.deepEqual(factoriesAt(all, 'SIGMA_DRACONIS').map(f => f.goods), ['FOOD', 'ISO']);
			assert.deepEqual(factoriesAt(all, 'TAU_CETI').map(f => f.goods), ['ISO']);
			assert.deepEqual(factoriesAt(all, 'BETA_HYDRI'), []);
		});

		it('says what the viewer makes of a good now, or that there is no factory yet', () => {
			assert.equal(goodsOptionLabel(iso), 'ISO \u2014 makes 6 a turn');
			assert.equal(goodsOptionLabel(poly), 'POLY \u2014 no factory yet');
		});
	});

	describe('parseUnits and unitsText', () => {
		it('accepts a whole number of 1 or more and refuses anything else', () => {
			assert.equal(parseUnits(' 4 '), 4);
			for (const text of ['', '0', '-2', '1.5', 'x', null]) {
				assert.equal(parseUnits(text), null, String(text));
			}
		});

		it('says unit in the singular', () => {
			assert.equal(unitsText(1), '1 unit');
			assert.equal(unitsText(3), '3 units');
		});
	});

	describe('unitsCostText (S05-1, S05-2)', () => {
		it('shows the total, the unit cost and the price it comes from', () => {
			assert.equal(unitsCostText('3', iso), '3 units cost 210 HT (70 HT a unit; ISO is at 12 HT)');
			assert.equal(unitsCostText('1', poly), '1 unit costs 20 HT (20 HT a unit; POLY is at 2 HT)');
		});

		it('is empty while the number is not valid', () => {
			assert.equal(unitsCostText('', iso), '');
			assert.equal(unitsCostText('0', iso), '');
		});
	});

	describe('validateUnits (S05-4)', () => {
		it('returns the units when whole and affordable, up to all the money', () => {
			assert.deepEqual(validateUnits('2', iso, 300), { units: 2 });
			assert.deepEqual(validateUnits('4', iso, 300), { units: 4 });
		});

		it('gives the whole-number message for anything else', () => {
			assert.deepEqual(validateUnits('0', iso, 300), { error: UNITS_WHOLE_NUMBER });
			assert.deepEqual(validateUnits('2.5', iso, 300), { error: UNITS_WHOLE_NUMBER });
		});

		it('says what it costs and what they have when it is too much, as the server does', () => {
			assert.deepEqual(validateUnits('5', iso, 300), { error: '5 units cost 350 HT; you have 300 HT.' });
			assert.deepEqual(validateUnits('1', iso, 10), { error: '1 unit costs 70 HT; you have 10 HT.' });
		});
	});
});
