import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	GAMES_KEY,
	MAX_REMEMBERED_GAMES,
	forgetGame,
	gameRow,
	latestGame,
	rememberGame,
	rememberedGames,
} from '../../main/resources/static/js/your-games.js';

function fakeStorage(entries = {}) {
	const map = new Map(Object.entries(entries));
	return {
		getItem: (key) => (map.has(key) ? map.get(key) : null),
		setItem: (key, value) => map.set(key, String(value)),
		removeItem: (key) => map.delete(key),
	};
}

function throwingStorage() {
	const fail = () => {
		throw new Error('SecurityError');
	};
	return { getItem: fail, setItem: fail, removeItem: fail };
}

describe('rememberGame', () => {
	it('keeps the most recently opened game first, once', () => {
		const storage = fakeStorage();

		rememberGame('a', storage);
		rememberGame('b', storage);
		rememberGame('a', storage);

		assert.deepEqual(rememberedGames(storage), ['a', 'b']);
		assert.equal(latestGame(storage), 'a');
		assert.equal(storage.getItem(GAMES_KEY), '["a","b"]');
	});

	it(`keeps at most ${MAX_REMEMBERED_GAMES} games, dropping the oldest`, () => {
		const storage = fakeStorage();

		for (let n = 1; n <= MAX_REMEMBERED_GAMES + 2; n++) {
			rememberGame(`g${n}`, storage);
		}

		const games = rememberedGames(storage);
		assert.equal(games.length, MAX_REMEMBERED_GAMES);
		assert.equal(games[0], `g${MAX_REMEMBERED_GAMES + 2}`);
		assert.ok(!games.includes('g1') && !games.includes('g2'));
	});
});

describe('forgetGame', () => {
	it('drops one game and keeps the order of the rest', () => {
		const storage = fakeStorage({ [GAMES_KEY]: '["a","b","c"]' });

		forgetGame('b', storage);

		assert.deepEqual(rememberedGames(storage), ['a', 'c']);
	});
});

describe('rememberedGames', () => {
	it('is empty when nothing, or something unreadable, is stored', () => {
		assert.deepEqual(rememberedGames(fakeStorage()), []);
		assert.deepEqual(rememberedGames(fakeStorage({ [GAMES_KEY]: 'not json' })), []);
		assert.deepEqual(rememberedGames(fakeStorage({ [GAMES_KEY]: '{"a":1}' })), []);
		assert.deepEqual(rememberedGames(fakeStorage({ [GAMES_KEY]: '["a",7,null,"b"]' })), ['a', 'b']);
		assert.equal(latestGame(fakeStorage()), null);
	});

	it('works without storage: nothing is remembered and nothing throws', () => {
		for (const storage of [throwingStorage(), null]) {
			assert.doesNotThrow(() => rememberGame('a', storage));
			assert.doesNotThrow(() => forgetGame('a', storage));
			assert.deepEqual(rememberedGames(storage), []);
			assert.equal(latestGame(storage), null);
		}
	});
});

describe('gameRow', () => {
	const game = {
		id: 'g/1',
		turns: 5,
		status: 'LOBBY',
		players: [{ corporationName: 'Orion Freight' }, { corporationName: 'Vega Lines' }],
	};

	it('links to the game and describes it by status, length and corporations', () => {
		assert.deepEqual(gameRow(game), {
			href: '/#game/g%2F1',
			status: 'In the lobby',
			details: '5 turns · Orion Freight, Vega Lines',
		});
	});

	it('names every status', () => {
		assert.equal(gameRow({ ...game, status: 'RUNNING' }).status, 'Running');
		assert.equal(gameRow({ ...game, status: 'FINISHED' }).status, 'Finished');
	});
});
