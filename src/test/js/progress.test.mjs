import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { YOUR_JUMPS, YOUR_PICK, progressSnapshot, progressText } from '../../main/resources/static/js/progress.js';
import { YOUR_DICTATOR_DECISION, YOUR_RESOLVE } from '../../main/resources/static/js/trading.js';

const ME = 'me@example.com';
const VEGA = 'vega@example.com';

function game({ id = 'g1', log = ['Game started'], turnOrder = [VEGA, ME], doneJumping = [], phase = 'SHIP_JUMPS' } = {}) {
	return {
		id,
		status: 'RUNNING',
		turn: { number: 1, phase, initiative: [VEGA, ME], turnOrder, doneJumping, jumpedShips: [] },
		players: [
			{ email: VEGA, corporationName: 'Vega Lines', ships: [] },
			{ email: ME, corporationName: 'Orion Freight', ships: [] },
		],
		log: log.map(text => ({ turn: 1, text })),
	};
}

describe('progressText', () => {
	it("announces other players' new public events (S04-1)", () => {
		const before = progressSnapshot(game(), null);
		const after = game({ log: ['Game started', 'Jump: Vega Lines — Meridian → Tau Ceti'] });

		assert.equal(progressText(before, after, ME), 'Jump: Vega Lines — Meridian → Tau Ceti.');
	});

	it('says when it has become your jumps or your pick', () => {
		const before = progressSnapshot(game(), null);

		assert.equal(
			progressText(before, game({ log: ['Game started', 'Landing: x'], doneJumping: [VEGA] }), ME),
			`Landing: x. ${YOUR_JUMPS}`,
		);
		const picking = progressSnapshot(game({ turnOrder: [null, null] }), null);
		assert.equal(progressText(picking, game({ turnOrder: [VEGA, null] }), ME), YOUR_PICK);
	});

	it('announces nothing on the first load, for another game, or when nothing is new', () => {
		assert.equal(progressText(null, game(), ME), '');
		assert.equal(progressText(progressSnapshot(game({ id: 'g2' }), null), game(), ME), '');
		assert.equal(progressText(progressSnapshot(game(), null), game(), ME), '');
	});

	it('announces only the last three of many new events', () => {
		const before = progressSnapshot(game(), null);
		const after = game({ log: ['Game started', 'a', 'b', 'c', 'd'] });

		assert.equal(progressText(before, after, ME), 'b. c. d.');
	});
});

describe('progressSnapshot', () => {
	it('never lowers what was seen, so a stale poll after your own step announces nothing old', () => {
		const afterYourStep = progressSnapshot(game({ log: ['Game started', 'Jump: Orion Freight — Wayfarer → Tau Ceti'] }), null);
		const stale = progressSnapshot(game(), afterYourStep);
		const caughtUp = game({ log: ['Game started', 'Jump: Orion Freight — Wayfarer → Tau Ceti'] });

		assert.equal(stale.logLength, 2);
		assert.equal(progressText(stale, caughtUp, ME), '');
	});

	it('starts again for another game', () => {
		const other = progressSnapshot(game({ id: 'g2', log: ['x'] }), progressSnapshot(game({ log: ['a', 'b', 'c'] }), null));

		assert.equal(other.logLength, 1);
	});
});

describe('progressText in Trading (E05-S01)', () => {
	function trading(initiative) {
		const shipJumps = game({ turnOrder: [VEGA, ME], doneJumping: [VEGA, ME] });
		return { ...shipJumps, turn: { ...shipJumps.turn, phase: 'TRADING', initiative, tradingQueue: [{}], resolvedGoods: [] } };
	}

	it('says when Trading starts with you first in initiative, and only then', () => {
		const before = progressSnapshot(game({ doneJumping: [VEGA] }), null);

		assert.equal(progressText(before, trading([ME, VEGA]), ME), YOUR_RESOLVE);
		assert.equal(progressText(before, trading([VEGA, ME]), ME), '');
		assert.equal(progressText(progressSnapshot(trading([ME, VEGA]), null), trading([ME, VEGA]), ME), '');
	});
});

describe('progressText for the Dictator (E05-S02)', () => {
	function asking(dictator) {
		const shipJumps = game({ turnOrder: [VEGA, ME], doneJumping: [VEGA, ME] });
		const good = { system: 'MU_HERCULIS', systemName: 'Mu Herculis', goods: 'ISO' };
		return {
			...shipJumps,
			turn: {
				...shipJumps.turn, phase: 'TRADING', initiative: [VEGA, ME], tradingQueue: [{}], resolvedGoods: [],
				resolution: { good, offers: [], dictator, awaitingDictator: dictator, finished: false },
			},
		};
	}

	it('says when a good you are Dictator of starts resolving, and only then', () => {
		const before = progressSnapshot(asking(null), null);

		assert.equal(progressText(before, asking(ME), ME), YOUR_DICTATOR_DECISION);
		assert.equal(progressText(before, asking(VEGA), ME), '');
		assert.equal(progressText(progressSnapshot(asking(ME), null), asking(ME), ME), '');
	});
});
