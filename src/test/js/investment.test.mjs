import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	ALREADY_CLOSED_INVESTING,
	COLLECT_WHOLE_NUMBER,
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
	isInvesting,
	isInvestmentClosedRefusal,
	waitingForInvesting,
} from '../../main/resources/static/js/investment.js';
import { phaseText } from '../../main/resources/static/js/bidding.js';

const ME = 'me@example.com';

function game({ phase = 'INVESTMENT', doneInvesting = [], status = 'RUNNING' } = {}) {
	return {
		status,
		turns: 20,
		turn: { number: 4, phase, doneBidding: [], doneInvesting },
		players: [
			{ email: 'vega@example.com', corporationName: 'Vega Lines' },
			{ email: ME, corporationName: 'Orion Freight' },
			{ email: 'kessler@example.com', corporationName: 'Kessler Cargo' },
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
		assert.equal(isDoneInvesting(game({ doneInvesting: ['vega@example.com'] }), ME), false);
		assert.equal(isDoneInvesting(game({ phase: 'BIDDING', doneInvesting: [ME] }), ME), false);
	});
});

describe('waitingForInvesting (E06-S01-3)', () => {
	it('names you first, then the others in join order, while nobody is done', () => {
		assert.deepEqual(waitingForInvesting(game(), ME), ['you', 'Vega Lines', 'Kessler Cargo']);
	});

	it('leaves out players who signalled done, you included', () => {
		assert.deepEqual(waitingForInvesting(game({ doneInvesting: ['vega@example.com'] }), ME), ['you', 'Kessler Cargo']);
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
