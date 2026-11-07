import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	TURNS_ERROR,
	deckLink,
	gameLink,
	lengthText,
	lobbyState,
	logLink,
	parseRoute,
	shipLine,
	systemLink,
	tradingLink,
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

	it('reads System details of a game (s02)', () => {
		assert.deepEqual(parseRoute('#game/g-1/system/TAU_CETI'), { view: 'system', id: 'g-1', systemId: 'TAU_CETI' });
		assert.deepEqual(parseRoute('#game/a%2Fb/system/MU_HERCULIS'), { view: 'system', id: 'a/b', systemId: 'MU_HERCULIS' });
	});

	it('reads the Game log of a game (s04)', () => {
		assert.deepEqual(parseRoute('#game/g-1/log'), { view: 'log', id: 'g-1' });
		assert.deepEqual(parseRoute('#game/a%2Fb/log'), { view: 'log', id: 'a/b' });
	});

	it('reads Trading of a game (s03)', () => {
		assert.deepEqual(parseRoute('#game/g-1/trading'), { view: 'trading', id: 'g-1' });
		assert.deepEqual(parseRoute(tradingLink('a/b')), { view: 'trading', id: 'a/b' });
		assert.equal(tradingLink('a/b'), '#game/a%2Fb/trading');
		assert.deepEqual(parseRoute('#game/g-1/trading/2'), { view: 'unknown', id: 'g-1' });
	});

	it('shows the home view only for an empty fragment', () => {
		assert.deepEqual(parseRoute(''), { view: 'home' });
		assert.deepEqual(parseRoute('#'), { view: 'home' });
		assert.deepEqual(parseRoute(undefined), { view: 'home' });
	});

	it('treats any other fragment as an unknown screen (s08), keeping the game it was in', () => {
		assert.deepEqual(parseRoute('#dashboard'), { view: 'unknown' });
		assert.deepEqual(parseRoute('#game/'), { view: 'unknown' });
		assert.deepEqual(parseRoute('#game/g-1/log/2'), { view: 'unknown', id: 'g-1' });
		assert.deepEqual(parseRoute('#game/g-1/system/'), { view: 'unknown', id: 'g-1' });
		assert.deepEqual(parseRoute('#game/g-1/system/tau-ceti'), { view: 'unknown', id: 'g-1' });
	});
});

describe('deckLink, systemLink and logLink', () => {
	it('encode the game id the way parseRoute decodes it', () => {
		assert.equal(deckLink('a/b'), '#game/a%2Fb');
		assert.equal(systemLink('a/b', 'TAU_CETI'), '#game/a%2Fb/system/TAU_CETI');
		assert.deepEqual(parseRoute(systemLink('a/b', 'TAU_CETI')), { view: 'system', id: 'a/b', systemId: 'TAU_CETI' });
		assert.equal(logLink('a/b'), '#game/a%2Fb/log');
		assert.deepEqual(parseRoute(logLink('a/b')), { view: 'log', id: 'a/b' });
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
