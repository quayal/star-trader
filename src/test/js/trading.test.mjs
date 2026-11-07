import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	YOUR_QUEUE_NOTE,
	awaitingDictator,
	decisionText,
	dictatorFormKey,
	dictatorLine,
	dictatorQuestion,
	disclosedOffers,
	goodLabel,
	moveGood,
	movedText,
	orderedBy,
	queueBody,
	queueNote,
	resolution,
	resolutionTitle,
	resolvedText,
	resolver,
	sameOrder,
	tradingJustStarted,
	waitingText,
} from '../../main/resources/static/js/trading.js';

const ME = 'me@example.com';
const VEGA = 'vega@example.com';

const ISO = { system: 'MU_HERCULIS', systemName: 'Mu Herculis', goods: 'ISO' };
const POLY = { system: 'TAU_CETI', systemName: 'Tau Ceti', goods: 'POLY' };
const CHIPS = { system: 'BETA_HYDRI', systemName: 'Beta Hydri', goods: 'CHIPS' };
const ALTAIR = 'altair@example.com';

function game({
	id = 'g1', phase = 'TRADING', status = 'RUNNING', number = 1, initiative = [VEGA, ME], queue = [ISO, POLY], done = [], resolution = null,
} = {}) {
	return {
		id,
		status,
		turn: { number, phase, initiative, tradingQueue: queue, resolvedGoods: done, resolution },
		players: [
			{ email: VEGA, corporationName: 'Vega Lines' },
			{ email: ME, corporationName: 'Orion Freight' },
			{ email: ALTAIR, corporationName: 'Altair Trade Co.' },
		],
	};
}

// E05-S02's worked example: Mu Herculis ISO, Vega buys at 11, Orion sells at 10, Altair is Dictator.
const VEGA_BUYS = { playerEmail: VEGA, type: 'BUY', price: 11 };
const ORION_SELLS = { playerEmail: ME, type: 'SELL', price: 10 };
const ALTAIR_SELLS = { playerEmail: ALTAIR, type: 'SELL', price: 9 };

function resolving({ offers = [VEGA_BUYS, ORION_SELLS], dictator = ALTAIR, awaiting = ALTAIR } = {}) {
	return { good: ISO, offers, dictator, awaitingDictator: awaiting, finished: awaiting === null };
}

describe('who orders and resolves (R25)', () => {
	it('is the first in initiative, during Trading only', () => {
		assert.equal(orderedBy(game()), VEGA);
		assert.equal(resolver(game()), VEGA);
		assert.equal(orderedBy(game({ phase: 'SHIP_JUMPS' })), null);
		assert.equal(orderedBy(game({ status: 'FINISHED' })), null);
	});

	it('leaves nobody to resolve once the queue is empty', () => {
		assert.equal(resolver(game({ queue: [], done: [ISO, POLY] })), null);
		assert.equal(orderedBy(game({ queue: [], done: [ISO, POLY] })), VEGA);
	});
});

describe('the Trading texts', () => {
	it('counts the resolved goods for the top bar (S01-6)', () => {
		assert.equal(resolvedText(game({ queue: [POLY], done: [ISO] })), '1 of 2 resolved');
		assert.equal(resolvedText(game({ phase: 'SHIP_JUMPS' })), '');
	});

	it('names whom the game waits for (S01-8, D09)', () => {
		assert.equal(waitingText(game(), ME), 'Waiting for Vega Lines to resolve the next good.');
		assert.equal(waitingText(game(), VEGA), 'You are first in initiative: resolve the next good.');
		assert.equal(waitingText(game({ queue: [], done: [ISO, POLY] }), ME), 'All 2 goods resolved.');
		assert.equal(waitingText(game({ phase: 'BIDDING' }), ME), '');
	});

	it('tells everyone else who decides the order (S01-3)', () => {
		assert.equal(queueNote(game(), ME), 'Order decided by Vega Lines (first in initiative); it can change until each one resolves.');
		assert.equal(queueNote(game(), VEGA), YOUR_QUEUE_NOTE);
	});

	it('labels a good', () => {
		assert.equal(goodLabel(ISO), 'Mu Herculis · ISO');
	});
});

describe('a good resolving (E05-S02)', () => {
	const asking = game({ queue: [POLY], resolution: resolving() });

	it('discloses every offer to everyone, the Dictator marked (S02-1, S02-3)', () => {
		const offered = game({ queue: [POLY], done: [ISO], resolution: resolving({ offers: [VEGA_BUYS, ORION_SELLS, ALTAIR_SELLS], awaiting: null }) });

		assert.deepEqual(disclosedOffers(asking), [
			{ corporation: 'Vega Lines', dictator: false, type: 'BUY', typeName: 'Buy', price: 11 },
			{ corporation: 'Orion Freight', dictator: false, type: 'SELL', typeName: 'Sell', price: 10 },
		]);
		assert.deepEqual(disclosedOffers(offered).at(-1), { corporation: 'Altair Trade Co.', dictator: true, type: 'SELL', typeName: 'Sell', price: 9 });
		assert.deepEqual(disclosedOffers(game()), []);
	});

	it('heads the panel with the good, resolving or resolved', () => {
		assert.equal(resolutionTitle(asking), 'Now resolving: Mu Herculis · ISO');
		assert.equal(resolutionTitle(game({ queue: [POLY], done: [ISO], resolution: resolving({ awaiting: null }) })), 'Resolved: Mu Herculis · ISO');
		assert.equal(resolutionTitle(game()), '');
		assert.equal(resolution(game({ phase: 'BIDDING', resolution: resolving() })), null);
	});

	it("says what the Dictator is doing or did (S02-2)", () => {
		assert.equal(dictatorLine(asking), 'Dictator: Altair Trade Co. \u2014 deciding before the roll');
		assert.equal(dictatorLine(game({ resolution: resolving({ offers: [ALTAIR_SELLS], awaiting: null }) })), 'Dictator: Altair Trade Co. \u2014 offered Sell at 9 HT');
		assert.equal(dictatorLine(game({ resolution: resolving({ awaiting: null }) })), 'Dictator: Altair Trade Co. \u2014 passed');
		assert.equal(dictatorLine(game({ resolution: resolving({ dictator: null, awaiting: null }) })), '');
	});

	it('waits for the Dictator before the next good, and names them (S01-4, S01-8)', () => {
		assert.equal(awaitingDictator(asking), ALTAIR);
		assert.equal(resolver(asking), null);
		assert.equal(waitingText(asking, VEGA), 'Waiting for Altair Trade Co. (Dictator) to decide.');
		assert.equal(waitingText(asking, ALTAIR), "You're Dictator of ISO here: place an offer before the roll, or pass.");
		assert.equal(waitingText(game({ queue: [], resolution: resolving() }), ME), 'Waiting for Altair Trade Co. (Dictator) to decide.');
		assert.equal(resolver(game({ queue: [POLY], done: [ISO], resolution: resolving({ awaiting: null }) })), VEGA);
	});

	it('counts the good resolving in the total, not as resolved (S01-6)', () => {
		assert.equal(resolvedText(asking), '0 of 2 resolved');
		assert.equal(resolvedText(game({ queue: [POLY], done: [ISO], resolution: resolving({ awaiting: null }) })), '1 of 2 resolved');
	});

	it('asks only the Dictator the form question', () => {
		assert.equal(dictatorQuestion(asking, ALTAIR), "You're Dictator of ISO here. Place an offer before the roll?");
		assert.equal(dictatorQuestion(asking, VEGA), '');
	});

	it('keys the form by game, turn and good', () => {
		assert.equal(dictatorFormKey(asking), 'g1/1/MU_HERCULIS/ISO');
		assert.equal(dictatorFormKey(game()), null);
	});
});

describe('reordering (S01-3)', () => {
	it('moves a good up or down, and nowhere past the ends', () => {
		assert.deepEqual(moveGood([ISO, POLY, CHIPS], 2, 0), [CHIPS, ISO, POLY]);
		assert.deepEqual(moveGood([ISO, POLY, CHIPS], 0, 1), [POLY, ISO, CHIPS]);
		assert.deepEqual(moveGood([ISO, POLY], 0, -1), [ISO, POLY]);
		assert.deepEqual(moveGood([ISO, POLY], 1, 2), [ISO, POLY]);
	});

	it('announces where the good went', () => {
		assert.equal(movedText([POLY, ISO, CHIPS], 1), 'Mu Herculis · ISO moved to position 2 of 3.');
	});

	it('sends only the system and good of each', () => {
		assert.deepEqual(queueBody([ISO, POLY]), { queue: [{ system: 'MU_HERCULIS', goods: 'ISO' }, { system: 'TAU_CETI', goods: 'POLY' }] });
	});

	it('compares two orders', () => {
		assert.equal(sameOrder([ISO, POLY], [{ ...ISO }, { ...POLY }]), true);
		assert.equal(sameOrder([ISO, POLY], [POLY, ISO]), false);
		assert.equal(sameOrder([ISO], [ISO, POLY]), false);
	});
});

describe('the decision alert (S01-9)', () => {
	it('asks the first in initiative to resolve the next good, and nobody else', () => {
		assert.equal(decisionText(game(), VEGA), 'Your decision is needed: resolve the next good.');
		assert.equal(decisionText(game(), ME), '');
		assert.equal(decisionText(game({ queue: [] }), VEGA), '');
	});

	it('asks the Dictator for their offer instead, while they decide (E05-S02)', () => {
		const asking = game({ queue: [POLY], resolution: resolving() });

		assert.equal(decisionText(asking, ALTAIR), "Your decision is needed: you're Dictator of ISO at Mu Herculis. Place an offer or pass.");
		assert.equal(decisionText(asking, VEGA), '');
	});
});

describe('opening Trading when it starts (S01-1)', () => {
	it('only on the step into Trading, for the same game', () => {
		assert.equal(tradingJustStarted(game({ phase: 'SHIP_JUMPS' }), game()), true);
		assert.equal(tradingJustStarted(game({ number: 1 }), game({ number: 2 })), true);
		assert.equal(tradingJustStarted(game(), game()), false);
		assert.equal(tradingJustStarted(null, game()), false);
		assert.equal(tradingJustStarted(game({ id: 'other', phase: 'SHIP_JUMPS' }), game()), false);
		assert.equal(tradingJustStarted(game({ phase: 'SHIP_JUMPS' }), game({ phase: 'SHIP_JUMPS' })), false);
	});
});
