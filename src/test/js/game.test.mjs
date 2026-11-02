import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	TURNS_ERROR,
	gameLink,
	lengthText,
	lobbyState,
	parseRoute,
	shipLine,
	turnText,
	validateTurns,
} from '../../main/resources/static/js/game.js';

const lobby = {
	id: 'g-1',
	turns: 5,
	status: 'LOBBY',
	creatorEmail: 'ada@example.com',
	players: [
		{ email: 'ada@example.com', name: 'Ada', corporationName: 'Orion Freight', ships: [] },
	],
	turn: null,
	log: [],
	you: { money: 0 },
};

const withGrace = {
	...lobby,
	players: [...lobby.players, { email: 'grace@example.com', name: 'Grace', corporationName: 'Vega Lines', ships: [] }],
};

describe('parseRoute', () => {
	it('reads the create screen and a game link from the hash', () => {
		assert.deepEqual(parseRoute('#new'), { view: 'new' });
		assert.deepEqual(parseRoute('#game/683f225a-cbb1'), { view: 'game', id: '683f225a-cbb1' });
	});

	it('decodes an encoded id', () => {
		assert.deepEqual(parseRoute('#game/a%2Fb'), { view: 'game', id: 'a/b' });
	});

	it('falls back to the home view for anything else', () => {
		assert.deepEqual(parseRoute(''), { view: 'home' });
		assert.deepEqual(parseRoute('#'), { view: 'home' });
		assert.deepEqual(parseRoute('#game/'), { view: 'home' });
		assert.deepEqual(parseRoute('#dashboard'), { view: 'home' });
	});
});

describe('validateTurns', () => {
	it('accepts whole numbers from 1 to 50, spaces around them included', () => {
		assert.deepEqual(validateTurns('1'), { turns: 1 });
		assert.deepEqual(validateTurns(' 50 '), { turns: 50 });
	});

	it('refuses anything else with the server message', () => {
		for (const raw of ['', '0', '51', '2.5', '-3', 'many', '1e1']) {
			assert.deepEqual(validateTurns(raw), { error: TURNS_ERROR }, raw);
		}
		assert.equal(TURNS_ERROR, 'Enter a whole number of turns from 1 to 50.');
	});
});

describe('lengthText', () => {
	it('says turn or turns', () => {
		assert.equal(lengthText(1), '1 turn');
		assert.equal(lengthText(5), '5 turns');
	});
});

describe('gameLink', () => {
	it('builds the lobby link from the site and the game id', () => {
		assert.equal(gameLink('http://localhost:3000', 'g-1'), 'http://localhost:3000/#game/g-1');
	});
});

describe('lobbyState', () => {
	it('lets the creator start only with 2 or more players, saying why not', () => {
		assert.deepEqual(lobbyState(lobby, 'ada@example.com'), {
			isCreator: true,
			corporationNames: ['Orion Freight'],
			canStart: false,
			startBlockedReason: 'At least 2 players are needed to start',
			waitingText: null,
		});
		assert.equal(lobbyState(withGrace, 'ada@example.com').canStart, true);
		assert.equal(lobbyState(withGrace, 'ada@example.com').startBlockedReason, null);
	});

	it('tells everyone else who they are waiting for', () => {
		const state = lobbyState(withGrace, 'grace@example.com');

		assert.equal(state.isCreator, false);
		assert.equal(state.canStart, false);
		assert.equal(state.waitingText, 'Waiting for Orion Freight to start the game');
	});
});

describe('turnText', () => {
	it('shows the turn out of the game length and the phase', () => {
		assert.equal(turnText({ status: 'RUNNING', turns: 20, turn: { number: 1, phase: 'BIDDING' } }), 'Turn 1 of 20 · Bidding');
	});

	it('names every phase of a turn', () => {
		assert.equal(turnText({ status: 'RUNNING', turns: 20, turn: { number: 4, phase: 'SHIP_JUMPS' } }), 'Turn 4 of 20 · Ship-jumps');
		assert.equal(turnText({ status: 'RUNNING', turns: 20, turn: { number: 4, phase: 'TRADING' } }), 'Turn 4 of 20 · Trading');
	});

	it('says the game is over instead of naming the last phase', () => {
		assert.equal(turnText({ status: 'FINISHED', turns: 20, turn: { number: 20, phase: 'TRADING' } }), 'Turn 20 of 20 · Game over');
	});
});

describe('shipLine', () => {
	it('names the ship, its system and where in the system it is', () => {
		assert.equal(
			shipLine({ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT' }),
			'Wayfarer — Tau Ceti · Port',
		);
	});
});
