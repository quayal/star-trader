import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	JUMPING_NOW,
	deckDecisionText,
	SLOT_DONE,
	jumper,
	jumpingText,
	shipOptionText,
	shipsToJump,
	slotStatuses,
	validateJump,
} from '../../main/resources/static/js/ship-jumps.js';
import { phaseText } from '../../main/resources/static/js/bidding.js';

const ME = 'me';
const VEGA = 'vega';
const KESSLER = 'kessler';
const SET = [VEGA, ME, KESSLER];

function game({ phase = 'SHIP_JUMPS', status = 'RUNNING', turnOrder = SET, jumpedShips = [], doneJumping = [] } = {}) {
	return {
		status,
		turns: 20,
		turn: { number: 4, phase, doneBidding: [], initiative: [KESSLER, ME, VEGA], turnOrder, jumpedShips, doneJumping },
		players: [
			{ id: VEGA, corporationName: 'Vega Lines', ships: [{ name: 'Meridian', system: 'GAMMA_LEPORIS', systemName: 'Gamma Leporis' }] },
			{
				id: ME,
				corporationName: 'Orion Freight',
				ships: [
					{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti' },
					{ name: 'Lantern', system: 'BETA_HYDRI', systemName: 'Beta Hydri' },
				],
			},
			{ id: KESSLER, corporationName: 'Kessler Cargo', ships: [] },
		],
	};
}

describe('jumper', () => {
	it('is the first place whose slot has not ended (R26)', () => {
		assert.equal(jumper(game()), VEGA);
		assert.equal(jumper(game({ doneJumping: [VEGA] })), ME);
	});

	it('is nobody while players still pick, after the last slot, or outside Ship-jumps', () => {
		assert.equal(jumper(game({ turnOrder: [null, null, KESSLER] })), null);
		assert.equal(jumper(game({ doneJumping: SET })), null);
		assert.equal(jumper(game({ phase: 'TRADING' })), null);
		assert.equal(jumper(game({ phase: 'BIDDING', turnOrder: [] })), null);
	});
});

describe('jumpingText', () => {
	it('names who is jumping, or says they are your jumps (S02-1, S02-9)', () => {
		assert.equal(jumpingText(game(), ME), 'Vega Lines is jumping');
		assert.equal(jumpingText(game({ doneJumping: [VEGA] }), ME), 'your jumps');
		assert.equal(jumpingText(game({ doneJumping: SET }), ME), 'all jumps done');
	});

	it('says nothing while players pick or in another phase', () => {
		assert.equal(jumpingText(game({ turnOrder: [null, null, KESSLER] }), ME), '');
		assert.equal(jumpingText(game({ phase: 'TRADING' }), ME), '');
	});

	it('joins the top bar once the order is set', () => {
		assert.equal(phaseText(game(), ME), 'Turn 4 of 20 · Ship-jumps — Vega Lines is jumping');
	});
});

describe('slotStatuses', () => {
	it('marks the slot jumping now and the slots done (S02-1)', () => {
		assert.deepEqual(slotStatuses(game({ doneJumping: [VEGA] })), [SLOT_DONE, JUMPING_NOW, '']);
	});

	it('marks nothing while players pick', () => {
		assert.deepEqual(slotStatuses(game({ turnOrder: [null, null, KESSLER] })), ['', '', '']);
	});
});

describe('shipsToJump', () => {
	it('lists your ships that have not jumped, only during your slot (S02-2, S02-3)', () => {
		const yourSlot = game({ doneJumping: [VEGA], jumpedShips: ['Meridian', 'Wayfarer'] });

		assert.deepEqual(shipsToJump(yourSlot, ME).map(s => s.name), ['Lantern']);
		assert.deepEqual(shipsToJump(game(), ME), []);
	});

	it('leaves out a ship still in the shipyard (R64)', () => {
		const yourSlot = game({ doneJumping: [VEGA] });
		yourSlot.players[1].ships[1].location = 'SHIPYARD';

		assert.deepEqual(shipsToJump(yourSlot, ME).map(s => s.name), ['Wayfarer']);
	});
});

describe('shipOptionText', () => {
	it('shows the ship with its current system (S02-2)', () => {
		assert.equal(shipOptionText({ name: 'Wayfarer', systemName: 'Tau Ceti' }), 'Wayfarer (at Tau Ceti)');
	});
});

describe('validateJump', () => {
	it('refuses the system the ship is already at (S02-4)', () => {
		const ship = { name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti' };

		assert.equal(validateJump(ship, 'TAU_CETI'), 'Wayfarer is already at Tau Ceti. Pick another destination.');
		assert.equal(validateJump(ship, 'BETA_HYDRI'), '');
	});
});

describe('deckDecisionText: a step on the Deck the game waits on you for', () => {
	it('says it is your pick while you are the next to choose a place', () => {
		// initiative Kessler, Me, Vega; Kessler has picked
		assert.equal(deckDecisionText(game({ turnOrder: [KESSLER, null, null] }), ME), 'Your decision is needed: choose your place in the turn order.');
		assert.equal(deckDecisionText(game({ turnOrder: [KESSLER, null, null] }), VEGA), '');
	});

	it('says it is your jump slot once the order is set and the players before you are done', () => {
		assert.equal(deckDecisionText(game({ doneJumping: [VEGA] }), ME), "Your decision is needed: it's your turn to jump your ships, or to finish jumping.");
		assert.equal(deckDecisionText(game(), ME), ''); // Vega is jumping
	});

	it('says nothing outside Ship-jumps, after the last slot, or for no viewer', () => {
		assert.equal(deckDecisionText(game({ phase: 'TRADING' }), ME), '');
		assert.equal(deckDecisionText(game({ doneJumping: SET }), ME), '');
		assert.equal(deckDecisionText(game({ turnOrder: [null, null, null] }), null), '');
	});
});
