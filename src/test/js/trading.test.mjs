import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	YOUR_QUEUE_NOTE,
	awaitingDictator,
	awaitingTrade,
	awaitingWholesaler,
	wholesaleForm,
	defaultSplit,
	lossWarning,
	lostUnits,
	shipLabel,
	tradeForm,
	validateTrade,
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
	counterLines,
	resolvedText,
	resolver,
	sameOrder,
	tradingJustStarted,
	tradingStatus,
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

function resolving({ offers = [VEGA_BUYS, ORION_SELLS], dictator = ALTAIR, awaiting = ALTAIR, startingCounter = null, trades = [] } = {}) {
	return { good: ISO, offers, dictator, awaitingDictator: awaiting, finished: awaiting === null, startingCounter, trades };
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

describe('s03 status line', () => {
	it('names whom the game waits for during Trading (S01-8)', () => {
		assert.equal(tradingStatus(game(), ME), 'Waiting for Vega Lines to resolve the next good.');
	});

	it('says when Trading starts, before the first one (S01-7)', () => {
		assert.equal(tradingStatus(game({ phase: 'BIDDING' }), ME), 'Trading starts after Ship-jumps. Results will appear here.');
		assert.equal(tradingStatus(game({ phase: 'SHIP_JUMPS', number: 3 }), ME), 'Trading starts after Ship-jumps. Results will appear here.');
	});

	it('says the game is over once it is (E05-S08)', () => {
		assert.equal(tradingStatus(game({ status: 'FINISHED', number: 20, queue: [] }), ME), 'All goods resolved. The game is over.');
	});

	it("says every good resolved once the next turn's Bidding has started (E05-S07-1)", () => {
		assert.equal(tradingStatus(game({ phase: 'BIDDING', number: 2, queue: [] }), ME), 'All goods resolved. The next turn starts with Bidding.');
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

describe('the counter (E05-S03)', () => {
	// The spec's worked example: counter 3; Vega buys 4, then Orion sells 5.
	const traded = game({
		resolution: resolving({
			dictator: null,
			awaiting: null,
			startingCounter: 3,
			trades: [{ playerEmail: VEGA, type: 'BUY', price: 11, marketMax: 4, units: 4 }, { playerEmail: ME, type: 'SELL', price: 10, marketMax: 5, units: 5 }],
		}),
	});

	it('shows the market, each trade and the end in plain terms (S03-6)', () => {
		assert.deepEqual(counterLines(traded), [
			'Market +3 \u2192 buyers first',
			'Vega Lines could buy up to 4 u: bought 4 u at 11 HT each, paid 44 HT',
			'Orion Freight could sell up to 5 u: sold 5 u at 10 HT each, received 50 HT',
			'No one else can trade at this market.',
		]);
	});

	it("shows each trade's position roll right after it, with the turn a change counts from (E05-S06-4, S06-5)", () => {
		const rolled = game({
			number: 4,
			resolution: resolving({
				dictator: null,
				awaiting: null,
				startingCounter: 3,
				trades: [
					{ playerEmail: VEGA, type: 'BUY', price: 11, marketMax: 9, units: 7, positionRoll: { roll: 5, promotedTo: 'WHOLESALER', demoted: [] } },
					{ playerEmail: ME, type: 'SELL', price: 10, marketMax: 9, units: 6, positionRoll: { roll: 9, promotedTo: null, demoted: [] } },
					{ playerEmail: ALTAIR, type: 'SELL', price: 9, marketMax: 8, units: 8, positionRoll: { roll: 4, promotedTo: 'DICTATOR', demoted: [VEGA] } },
					{ playerEmail: VEGA, type: 'BUY', price: 12, marketMax: 2, units: 2, positionRoll: null },
				],
			}),
		});

		assert.deepEqual(counterLines(rolled).filter(line => line.includes('rolled')), [
			'Vega Lines traded 7 u \u2014 rolled 5: now Wholesaler of ISO at Mu Herculis',
			'Orion Freight traded 6 u \u2014 rolled 9: no change',
			'Altair Trade Co. traded 8 u \u2014 rolled 4: now Dictator of ISO at Mu Herculis; Vega Lines is demoted to Wholesaler',
		]);
		assert.equal(counterLines(rolled)[2], 'Vega Lines traded 7 u \u2014 rolled 5: now Wholesaler of ISO at Mu Herculis');
	});

	it('favours sellers below 0 and buyers at 0, and says when nobody could trade (S03-7)', () => {
		assert.deepEqual(counterLines(game({ resolution: resolving({ awaiting: null, startingCounter: -2 }) })), [
			'Market \u22122 \u2192 sellers first',
			'No one can trade at this market.',
		]);
		assert.equal(counterLines(game({ resolution: resolving({ awaiting: null, startingCounter: 0 }) }))[0], 'Market 0 \u2192 buyers first');
	});

	it('shows nothing before the counter starts, so not while the Dictator decides (R38)', () => {
		assert.deepEqual(counterLines(game({ resolution: resolving() })), []);
		assert.deepEqual(counterLines(game()), []);
		assert.deepEqual(counterLines(game({ resolution: { good: ISO, offers: [], dictator: null, awaitingDictator: null, finished: true } })), []);
	});
});

// E05-S04: the counter reached Orion's (my) sell at 10, or my buy; Vega waits.
function awaitingMe({ type = 'SELL', price = 10, maxUnits = 5, trades = [] } = {}) {
	return {
		...resolving({ dictator: null, awaiting: null, startingCounter: 3, trades }),
		finished: false,
		awaitingTrade: { playerEmail: ME, type, price, maxUnits },
	};
}

/** [game] seen by me, with my ships Wayfarer and Lantern at Mu Herculis and Meridian elsewhere. */
function mine(base, { tradeMax = 5, wayfarer = { ISO: 3, CHIPS: 12 }, lantern = { ISO: 8 } } = {}) {
	const used = cargo => (cargo.ISO ?? 0) + (cargo.POLY ?? 0);
	return {
		...base,
		players: base.players.map(p => p.email !== ME ? p : {
			...p,
			ships: [
				{ name: 'Wayfarer', system: 'MU_HERCULIS', location: 'PORT' },
				{ name: 'Meridian', system: 'TAU_CETI', location: 'PORT' },
				{ name: 'Lantern', system: 'MU_HERCULIS', location: 'PORT' },
				{ name: 'Halcyon', system: 'MU_HERCULIS', location: 'PLANET' },
			],
		}),
		you: {
			money: 200,
			tradeMax,
			ships: [
				{ name: 'Wayfarer', cargo: wayfarer, cargoUsed: used(wayfarer), cargoSpaces: 10 },
				{ name: 'Meridian', cargo: { ISO: 9 }, cargoUsed: 9, cargoSpaces: 10 },
				{ name: 'Lantern', cargo: lantern, cargoUsed: used(lantern), cargoSpaces: 10 },
			],
		},
	};
}

describe('a trader choosing an amount (E05-S04)', () => {
	it('names whom the game waits for, with the counter\'s max, and holds Resolve next (S01-8, S01-4)', () => {
		const waiting = game({ resolution: { ...awaitingMe(), awaitingTrade: { playerEmail: VEGA, type: 'BUY', price: 11, maxUnits: 4 } } });

		assert.equal(waitingText(waiting, ME), 'Vega Lines can buy up to 4 u \u2014 waiting for their decision.');
		assert.equal(waitingText(game({ resolution: awaitingMe() }), ME), 'The market has reached your sell at 10 HT: your decision.');
		assert.equal(resolver(waiting), null);
		assert.equal(awaitingTrade(waiting).playerEmail, VEGA);
		assert.equal(decisionText(waiting, VEGA), 'Your decision is needed: the market has reached your buy of ISO at Mu Herculis.');
		assert.equal(decisionText(waiting, ME), '');
	});

	it('lists my ships at that system with what they carry (S04-3)', () => {
		const form = tradeForm(mine(game({ resolution: awaitingMe() })), ME);

		assert.deepEqual(form.ships.map(ship => ship.label), [
			'Wayfarer \u2014 ISO 3, CHIPS 12 \u00b7 cargo 3 / 10 used',
			'Lantern \u2014 ISO 8 \u00b7 cargo 8 / 10 used',
		]);
		assert.equal(form.max, 5);
		assert.equal(form.heading, 'Your sell at 10 HT can trade now');
		assert.equal(form.amountLabel, 'Amount to sell (u)');
		assert.equal(form.help, 'You get your offer price, 10 HT each. Trading more leaves less for others.');
		assert.equal(shipLabel({ name: 'Wayfarer', cargo: {}, cargoUsed: 0, cargoSpaces: 10 }), 'Wayfarer \u2014 empty \u00b7 cargo 0 / 10 used');
	});

	it('is only for the trader the game waits for', () => {
		assert.equal(tradeForm(mine(game({ resolution: awaitingMe() })), VEGA), null);
		assert.equal(tradeForm(mine(game({ resolution: resolving() })), ME), null);
	});

	it('splits a sell from what each ship holds, and a buy into free space with the rest on the first ship (S04-3)', () => {
		const sell = tradeForm(mine(game({ resolution: awaitingMe() })), ME);
		const buy = tradeForm(mine(game({ resolution: awaitingMe({ type: 'BUY', price: 11 }) }), { tradeMax: 12 }), ME);

		assert.deepEqual(defaultSplit(sell, 5), [3, 2]);
		assert.equal(buy.help, 'You pay your offer price, 11 HT each. Trading more leaves less for others.');
		assert.deepEqual(defaultSplit(buy, 4), [4, 0]);
		assert.deepEqual(defaultSplit(buy, 12), [10, 2]);
	});

	it('warns before confirming when some of a buy will be lost (S04-4)', () => {
		const buy = tradeForm(mine(game({ resolution: awaitingMe({ type: 'BUY', price: 11 }) }), { tradeMax: 12 }), ME);

		// Wayfarer has 7 free spaces and Lantern 2: 12 u, 10 on Wayfarer, lose 3.
		assert.equal(lostUnits(buy, 12, [10, 2]), 3);
		assert.equal(lossWarning(buy, 12, [10, 2]), 'Only 9 u fit in the ships you chose; the rest will be lost.');
		assert.equal(lossWarning(buy, 9, [7, 2]), '');
		const nowhere = { ...buy, ships: [] };
		assert.equal(lossWarning(nowhere, 3, []), 'You have no ship in port at Mu Herculis, so everything you buy will be lost.');
		assert.equal(lossWarning(tradeForm(mine(game({ resolution: awaitingMe() })), ME), 5, [3, 2]), '');
	});

	it('loses nothing of CHIPS or FOOD, which take no space (R42)', () => {
		const chips = { ...game({ resolution: { ...awaitingMe({ type: 'BUY', price: 3 }), good: CHIPS } }) };
		const seen = mine(chips, { tradeMax: 30 });
		seen.players = seen.players.map(p => p.email !== ME ? p : { ...p, ships: [{ name: 'Wayfarer', system: 'BETA_HYDRI', location: 'PORT' }] });
		const form = tradeForm(seen, ME);

		assert.deepEqual(defaultSplit(form, 30), [30]);
		assert.equal(lostUnits(form, 30, [30]), 0);
	});

	it('checks the amount and the split as the server does (S04-1, S04-3)', () => {
		const sell = tradeForm(mine(game({ resolution: awaitingMe() })), ME);

		for (const amount of ['', '0', '6', '2.5', 'abc']) {
			assert.deepEqual(validateTrade(sell, amount, ['3', '2']), { error: 'Enter a whole number from 1 to 5.', field: 'amount' });
		}
		assert.deepEqual(validateTrade(sell, '5', ['3', '-1']), { error: 'Enter a whole number of units, 0 or more, for each ship.', field: 1 });
		assert.deepEqual(validateTrade(sell, '5', ['4', '1']), { error: 'Wayfarer has only 3 ISO.', field: 0 });
		assert.deepEqual(validateTrade(sell, '5', ['3', '1']), { error: "Your ships' units must add up to 5.", field: 0 });
		assert.deepEqual(validateTrade(sell, ' 5 ', ['3', '2']), {
			body: { amount: 5, ships: [{ name: 'Wayfarer', units: 3 }, { name: 'Lantern', units: 2 }] },
		});
		assert.deepEqual(validateTrade({ ...sell, type: 'BUY', ships: [] }, '2', []), { body: { amount: 2, ships: [] } });
	});

	it('says what the market allowed and what took place, never why (S04-5, S04-6, R29)', () => {
		const nothing = game({
			resolution: {
				...resolving({ dictator: null, awaiting: null, startingCounter: 3 }),
				trades: [
					{ playerEmail: ME, type: 'SELL', price: 10, marketMax: 2, units: 0 },
					{ playerEmail: VEGA, type: 'BUY', price: 11, marketMax: 3, units: 0 },
				],
			},
		});

		assert.deepEqual(counterLines(nothing), [
			'Market +3 \u2192 buyers first',
			'Orion Freight could sell up to 2 u: sold nothing',
			'Vega Lines could buy up to 3 u: bought nothing',
			'No one can trade at this market.',
		]);
	});

	it('lists only ships in port, where trading happens (R23, R42)', () => {
		const form = tradeForm(mine(game({ resolution: awaitingMe() })), ME);

		assert.deepEqual(form.ships.map(ship => ship.name), ['Wayfarer', 'Lantern']);
		assert.equal(form.shipsLegend, 'Your ships in port at Mu Herculis');
	});

	it('lets a trader with nothing to trade only acknowledge it, saying why to them alone (S04-5)', () => {
		const broke = tradeForm(mine(game({ resolution: awaitingMe({ type: 'BUY', price: 20 }) }), { tradeMax: 0 }), ME);
		const empty = tradeForm(mine(game({ resolution: awaitingMe() }), { tradeMax: 0 }), ME);

		assert.equal(broke.max, 0);
		assert.equal(broke.cannot, "You can't afford any units at 20 HT.");
		assert.equal(broke.heading, 'The market has reached your buy at 20 HT');
		assert.equal(empty.cannot, "You have no ISO in your ships in port at Mu Herculis, so you can't sell any.");
		assert.equal(tradeForm(mine(game({ resolution: awaitingMe() })), ME).cannot, '');
	});
});

// E05-S05: the worked example after its trades: counter 4, price 11 → 10. Altair (Dictator) and I (Wholesaler) are asked.
function priceChanged({ wholesales = [], awaitingWholesaler = ME } = {}) {
	return {
		...resolving({
			dictator: ALTAIR,
			awaiting: null,
			startingCounter: 3,
			trades: [{ playerEmail: VEGA, type: 'BUY', price: 11, marketMax: 4, units: 4 }],
		}),
		finished: awaitingWholesaler === null,
		awaitingTrade: null,
		startPrice: 11,
		newPrice: 10,
		wholesalers: [ME, ALTAIR],
		awaitingWholesaler,
		wholesales,
	};
}

describe('the price change and the wholesale step (E05-S05)', () => {
	it('shows the price change after the end line, then each wholesale answer (S05-2, S05-5)', () => {
		const answered = game({
			resolution: priceChanged({
				awaitingWholesaler: null,
				wholesales: [
					{ playerEmail: ME, role: 'Wholesaler', type: 'BUY', units: 3, price: 10 },
					{ playerEmail: ALTAIR, role: 'Dictator', type: null, units: 0, price: 10 },
				],
			}),
		});

		assert.deepEqual(counterLines(answered).slice(2), [
			'No one else can trade at this market.',
			'ISO price at Mu Herculis: 11 \u2192 10 HT',
			'Orion Freight (Wholesaler) bought 3 u at 10 HT each, paid 30 HT',
			'Altair Trade Co. (Dictator) passed on wholesale',
		]);
	});

	it('shows the price change even when nobody traded (S05-2)', () => {
		const none = game({ resolution: { ...resolving({ awaiting: null, startingCounter: 13 }), startPrice: 11, newPrice: 7, wholesales: [] } });

		assert.deepEqual(counterLines(none), ['Market +13 \u2192 buyers first', 'No one can trade at this market.', 'ISO price at Mu Herculis: 11 \u2192 7 HT']);
	});

	it('names whom the wholesale step waits for, holds Resolve next, and alerts only them (S01-8, S01-9, S05-4)', () => {
		const waiting = game({ resolution: priceChanged() });

		assert.equal(awaitingWholesaler(waiting), ME);
		assert.equal(resolver(waiting), null);
		assert.equal(waitingText(waiting, VEGA), 'Waiting for Orion Freight (Wholesaler) to trade up to 5 u at 10 HT, or pass.');
		assert.equal(waitingText(waiting, ME), 'As Wholesaler, you may buy or sell up to 5 u at the new price, 10 HT, or pass: your decision.');
		assert.equal(decisionText(waiting, ME), 'Your decision is needed: as Wholesaler, buy or sell up to 5 u of ISO at Mu Herculis at 10 HT, or pass.');
		assert.equal(decisionText(waiting, VEGA), '');
		const dictatorAsked = game({ resolution: priceChanged({ awaitingWholesaler: ALTAIR }) });
		assert.equal(waitingText(dictatorAsked, VEGA), 'Waiting for Altair Trade Co. (Dictator) to trade up to 5 u at 10 HT, or pass.');
	});

	it('gives the one asked a form like the amount form, Buy first, with their own max for each (S05-4, R37)', () => {
		const seen = { ...mine(game({ resolution: priceChanged() })), you: { ...mine(game()).you, wholesaleMax: { buy: 5, sell: 4 } } };

		const buy = wholesaleForm(seen, ME, null);
		const sell = wholesaleForm(seen, ME, 'SELL');

		assert.equal(buy.type, 'BUY');
		assert.equal(buy.max, 5);
		assert.equal(buy.heading, 'As Wholesaler, buy or sell up to 5 u of ISO at the new price, 10 HT?');
		assert.equal(buy.help, "You pay 10 HT each, from 1 to 5. This doesn't move the price.");
		assert.equal(sell.max, 4);
		assert.equal(sell.amountLabel, 'Amount to sell (u)');
		assert.notEqual(sell.key, buy.key);
		assert.deepEqual(sell.ships.map(ship => ship.name), ['Wayfarer', 'Lantern']);
		assert.deepEqual(validateTrade(sell, '4', ['3', '1']).body, { amount: 4, ships: [{ name: 'Wayfarer', units: 3 }, { name: 'Lantern', units: 1 }] });
		assert.equal(wholesaleForm(seen, VEGA, null), null);
		assert.equal(wholesaleForm(mine(game({ resolution: priceChanged() })), ME, null), null);
	});

	it('says why, to them alone, when they can trade nothing that way, and starts on a way they can (S05-4)', () => {
		const seen = (wholesaleMax) => ({ ...mine(game({ resolution: priceChanged() })), you: { ...mine(game()).you, wholesaleMax } });

		assert.equal(wholesaleForm(seen({ buy: 0, sell: 3 }), ME, null).type, 'SELL');
		assert.equal(wholesaleForm(seen({ buy: 0, sell: 3 }), ME, 'BUY').cannot, "You can't afford any units at 10 HT.");
		assert.equal(wholesaleForm(seen({ buy: 2, sell: 0 }), ME, 'SELL').cannot, "You have no ISO in your ships in port at Mu Herculis, so you can't sell any.");
		assert.equal(wholesaleForm(seen({ buy: 0, sell: 0 }), ME, null).type, 'BUY');
	});
});
