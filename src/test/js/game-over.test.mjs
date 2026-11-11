import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { gameJustEnded, isOver, moneyText, standingRows, winnerText } from '../../main/resources/static/js/game-over.js';

const VEGA = 'vega@example.com';
const KESSLER = 'kessler@example.com';
const ORION = 'orion@example.com';

function game({ status = 'FINISHED', standings, winners } = {}) {
	const finalMoney = standings ?? [
		{ email: VEGA, corporationName: 'Vega Lines', money: 2310 },
		{ email: KESSLER, corporationName: 'Kessler Cargo', money: 1900 },
		{ email: ORION, corporationName: 'Orion Freight', money: 300 },
	];
	return {
		id: 'g1',
		status,
		result: status === 'FINISHED' ? { standings: finalMoney, winners: winners ?? [finalMoney[0].email] } : null,
	};
}

describe('Game over (s09, E05-S08)', () => {
	it('is over only once the game has finished', () => {
		assert.equal(isOver(game()), true);
		assert.equal(isOver(game({ status: 'RUNNING' })), false);
	});

	it('writes money with thousands separators', () => {
		assert.equal(moneyText(2310), '2,310 HT');
		assert.equal(moneyText(300), '300 HT');
	});

	it('names the winner (S08-2)', () => {
		assert.equal(winnerText(game()), 'Vega Lines wins with 2,310 HT');
		assert.equal(winnerText(game({ status: 'RUNNING' })), '');
	});

	it('lets players tied on money share the win (S08-4)', () => {
		const tied = game({
			standings: [
				{ email: VEGA, corporationName: 'Vega Lines', money: 2310 },
				{ email: KESSLER, corporationName: 'Kessler Cargo', money: 2310 },
				{ email: ORION, corporationName: 'Orion Freight', money: 300 },
			],
			winners: [VEGA, KESSLER],
		});
		assert.equal(winnerText(tied), 'Vega Lines and Kessler Cargo win with 2,310 HT');
		assert.deepEqual(standingRows(tied, ORION).map(row => row.place), [1, 1, 3]);
	});

	it("lists everyone's final money, most first, marking the winner and the viewer (S08-2, S08-3)", () => {
		assert.deepEqual(standingRows(game(), KESSLER), [
			{ place: 1, corporation: 'Vega Lines', money: '2,310 HT', winner: true, you: false },
			{ place: 2, corporation: 'Kessler Cargo', money: '1,900 HT', winner: false, you: true },
			{ place: 3, corporation: 'Orion Freight', money: '300 HT', winner: false, you: false },
		]);
		assert.deepEqual(standingRows(game({ status: 'RUNNING' }), VEGA), []);
	});

	it('has just ended only when the same game was running a moment ago', () => {
		assert.equal(gameJustEnded(game({ status: 'RUNNING' }), game()), true);
		assert.equal(gameJustEnded(game(), game()), false);
		assert.equal(gameJustEnded(null, game()), false);
		assert.equal(gameJustEnded({ ...game({ status: 'RUNNING' }), id: 'other' }, game()), false);
	});
});
