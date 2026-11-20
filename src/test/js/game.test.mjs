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
	gameOverLink,
	assetsLink,
	corporationsLink,
	guideLink,
	fleetLink,
	fleetShipLink,
	infrastructureLink,
	turnText,
	validateTurns,
	screenForPhaseChange,
	deckHeadingFor,
} from '../../main/resources/static/js/game.js';

const lobby = {
	id: 'g-1',
	turns: 5,
	status: 'LOBBY',
	creatorId: 'ada',
	players: [
		{ id: 'ada', name: 'Ada', corporationName: 'Orion Freight', ships: [] },
	],
	turn: null,
	log: [],
	you: { money: 0 },
};

const withGrace = {
	...lobby,
	players: [...lobby.players, { id: 'grace', name: 'Grace', corporationName: 'Vega Lines', ships: [] }],
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

	it('reads the Fleet tab of a game (E07-S03)', () => {
		assert.deepEqual(parseRoute('#game/g-1/fleet'), { view: 'fleet', id: 'g-1' });
		assert.deepEqual(parseRoute(fleetLink('a/b')), { view: 'fleet', id: 'a/b' });
		assert.equal(fleetLink('a/b'), '#game/a%2Fb/fleet');
		assert.deepEqual(parseRoute('#game/g-1/fleet/Wayfarer'), { view: 'fleet', id: 'g-1', ship: 'Wayfarer' });
		assert.deepEqual(parseRoute(fleetShipLink('a/b', 'Star Runner')), { view: 'fleet', id: 'a/b', ship: 'Star Runner' });
		assert.equal(fleetShipLink('a/b', 'Star Runner'), '#game/a%2Fb/fleet/Star%20Runner');
		assert.deepEqual(parseRoute('#game/g-1/fleet/a/b'), { view: 'unknown', id: 'g-1' });
	});

	it('reads the Corporations tab of a game (E11-S01)', () => {
		assert.deepEqual(parseRoute('#game/g-1/corporations'), { view: 'corporations', id: 'g-1' });
		assert.deepEqual(parseRoute(corporationsLink('a/b')), { view: 'corporations', id: 'a/b' });
		assert.equal(corporationsLink('a/b'), '#game/a%2Fb/corporations');
		assert.deepEqual(parseRoute('#game/g-1/corporations/2'), { view: 'unknown', id: 'g-1' });
		assert.deepEqual(parseRoute(guideLink('a/b')), { view: 'guide', id: 'a/b' });
		assert.equal(guideLink('a/b'), '#game/a%2Fb/guide');
		assert.deepEqual(parseRoute(assetsLink('a/b')), { view: 'assets', id: 'a/b' });
		assert.equal(assetsLink('a/b'), '#game/a%2Fb/assets');
	});

	it('reads Infrastructure of a game (s10, E06-S08)', () => {
		assert.deepEqual(parseRoute('#game/g-1/infrastructure'), { view: 'infrastructure', id: 'g-1' });
		assert.deepEqual(parseRoute(infrastructureLink('a/b')), { view: 'infrastructure', id: 'a/b' });
		assert.equal(infrastructureLink('a/b'), '#game/a%2Fb/infrastructure');
		assert.deepEqual(parseRoute('#game/g-1/infrastructure/2'), { view: 'unknown', id: 'g-1' });
	});

	it('reads Trading of a game (s03)', () => {
		assert.deepEqual(parseRoute('#game/g-1/trading'), { view: 'trading', id: 'g-1' });
		assert.deepEqual(parseRoute(tradingLink('a/b')), { view: 'trading', id: 'a/b' });
		assert.deepEqual(parseRoute('#game/g-1/game-over'), { view: 'over', id: 'g-1' }); // s09, E05-S08
		assert.deepEqual(parseRoute(gameOverLink('a/b')), { view: 'over', id: 'a/b' });
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
		assert.deepEqual(lobbyState(lobby, 'ada'), {
			isCreator: true,
			corporationNames: ['Orion Freight'],
			canStart: false,
			startBlockedReason: 'At least 2 players are needed to start',
			waitingText: null,
		});
		assert.equal(lobbyState(withGrace, 'ada').canStart, true);
		assert.equal(lobbyState(withGrace, 'ada').startBlockedReason, null);
	});

	it('tells everyone else who they are waiting for', () => {
		const state = lobbyState(withGrace, 'grace');

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
		assert.equal(turnText({ status: 'RUNNING', turns: 20, turn: { number: 4, phase: 'INITIATIVE' } }), 'Turn 4 of 20 · Initiative');
		assert.equal(turnText({ status: 'RUNNING', turns: 20, turn: { number: 4, phase: 'NEWS' } }), 'Turn 4 of 20 · News');
		assert.equal(turnText({ status: 'RUNNING', turns: 20, turn: { number: 4, phase: 'JUMPS' } }), 'Turn 4 of 20 · Jumps');
		assert.equal(turnText({ status: 'RUNNING', turns: 20, turn: { number: 4, phase: 'TRADING' } }), 'Turn 4 of 20 · Trading');
		assert.equal(turnText({ status: 'RUNNING', turns: 20, turn: { number: 4, phase: 'INVESTMENT' } }), 'Turn 4 of 20 · Investment');
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

describe('the screen follows the phase (D15, E08-S03)', () => {
	const at = (phase, { number = 3, status = 'RUNNING', id = 'g-1' } = {}) => ({ id, status, turn: { number, phase } });

	it('opens the Deck when Ship-jumps starts (S03-1)', () => {
		assert.equal(screenForPhaseChange(at('BIDDING'), at('JUMPS')), 'game');
	});

	it("opens the Deck when Investment closes and the next turn's Bidding starts (S03-2)", () => {
		assert.equal(screenForPhaseChange(at('INVESTMENT'), at('BIDDING', { number: 4 })), 'game');
	});

	it('opens Trading, Infrastructure and Game over as before (S03-3)', () => {
		assert.equal(screenForPhaseChange(at('JUMPS'), at('TRADING')), 'trading');
		assert.equal(screenForPhaseChange(at('TRADING'), at('INVESTMENT')), 'infrastructure');
		assert.equal(screenForPhaseChange(at('INVESTMENT'), at('INVESTMENT', { status: 'FINISHED' })), 'over');
	});

	it('follows a change that skipped a state between two polls', () => {
		assert.equal(screenForPhaseChange(at('BIDDING'), at('TRADING')), 'trading');
		assert.equal(screenForPhaseChange(at('TRADING'), at('BIDDING', { number: 4 })), 'game');
	});

	it('counts the same phase of a later turn as a change', () => {
		assert.equal(screenForPhaseChange(at('BIDDING'), at('BIDDING', { number: 4 })), 'game');
	});

	it('opens the Deck when the game starts', () => {
		assert.equal(screenForPhaseChange({ id: 'g-1', status: 'LOBBY', turn: null }, at('BIDDING', { number: 1 })), 'game');
	});

	it('opens nothing while the phase goes on, or once the game stays over (S03-5)', () => {
		assert.equal(screenForPhaseChange(at('TRADING'), at('TRADING')), null);
		assert.equal(screenForPhaseChange(at('INVESTMENT', { status: 'FINISHED' }), at('INVESTMENT', { status: 'FINISHED' })), null);
	});

	it("opens nothing for the first state seen, another game's state, or a lobby (S03-5)", () => {
		assert.equal(screenForPhaseChange(null, at('TRADING')), null);
		assert.equal(screenForPhaseChange(at('JUMPS', { id: 'g-2' }), at('TRADING')), null);
		assert.equal(screenForPhaseChange({ id: 'g-1', status: 'LOBBY', turn: null }, { id: 'g-1', status: 'LOBBY', turn: null }), null);
	});

	it('names the Deck heading the phase needs (S03-4)', () => {
		assert.equal(deckHeadingFor(at('BIDDING')), 'bid-title');
		assert.equal(deckHeadingFor(at('JUMPS')), 'turn-order-title');
		assert.equal(deckHeadingFor(at('TRADING')), null);
		assert.equal(deckHeadingFor(at('INVESTMENT', { status: 'FINISHED' })), null);
	});
});
