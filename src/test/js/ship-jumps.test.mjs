import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	JUMPING_NOW,
	deckDecisionText,
	failedJumpText,
	jumpChanceText,
	SLOT_DONE,
	jumper,
	jumpingText,
	shipOptionText,
	shipsToJump,
	slotStatuses,
} from '../../main/resources/static/js/ship-jumps.js';
import { phaseText } from '../../main/resources/static/js/bidding.js';

const ME = 'me';
const VEGA = 'vega';
const KESSLER = 'kessler';
const SET = [VEGA, ME, KESSLER];

function game({ phase = undefined, status = 'RUNNING', turnOrder = SET, jumpedShips = [], doneJumping = [] } = {}) {
	return {
		status,
		turns: 20,
		turn: { number: 4, phase: phase ?? (turnOrder.includes(null) ? 'INITIATIVE' : 'JUMPS'), doneBidding: [], initiative: [KESSLER, ME, VEGA], turnOrder, jumpedShips, doneJumping },
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
		assert.equal(phaseText(game(), ME), 'Turn 4 of 20 · Jumps — Vega Lines is jumping');
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

describe('deckDecisionText: a step on the Deck the game waits on you for', () => {
	it('says it is your pick while you are the next to choose a place', () => {
		// initiative Kessler, Me, Vega; Kessler has picked
		assert.equal(deckDecisionText(game({ turnOrder: [KESSLER, null, null] }), ME), 'Your decision is needed: choose your place in the turn order.');
		assert.equal(deckDecisionText(game({ turnOrder: [KESSLER, null, null] }), VEGA), '');
	});

	it('says it is your jump slot once the order is set and the players before you are done', () => {
		assert.equal(deckDecisionText(game({ doneJumping: [VEGA] }), ME), "Your decision is needed: it's your turn to jump ships.");
		assert.equal(deckDecisionText(game(), ME), ''); // Vega is jumping
	});

	it('says nothing outside Ship-jumps, after the last slot, or for no viewer', () => {
		assert.equal(deckDecisionText(game({ phase: 'TRADING' }), ME), '');
		assert.equal(deckDecisionText(game({ doneJumping: SET }), ME), '');
		assert.equal(deckDecisionText(game({ turnOrder: [null, null, null] }), null), '');
	});
});

describe('jumpChanceText (R68)', () => {
	const withChances = {
		...game(),
		you: {
			ships: [
				{
					name: 'Wayfarer',
					jumpChances: { MU_HERCULIS: { target: 9, percent: 83 }, BETA_HYDRI: { target: 13, percent: 100 } },
				},
			],
		},
	};

	it("shows the chosen ship's chance and the roll it needs", () => {
		assert.equal(jumpChanceText(withChances, 'Wayfarer', 'MU_HERCULIS'), 'Chance of success: 83% (2d6 roll of 9 or less)');
	});

	it('shows a certain jump as 100% with no roll to make', () => {
		assert.equal(jumpChanceText(withChances, 'Wayfarer', 'BETA_HYDRI'), 'Chance of success: 100%');
	});

	it("says a move within the ship's own system always succeeds (R69)", () => {
		assert.equal(jumpChanceText(withChances, 'Wayfarer', 'TAU_CETI'), 'Moves into Space here: always succeeds.');
	});

	it("shows nothing for someone else's ship, or a game without your ships", () => {
		assert.equal(jumpChanceText(withChances, 'Meridian', 'TAU_CETI'), '');
		assert.equal(jumpChanceText(game(), 'Wayfarer', 'MU_HERCULIS'), '');
	});
});

describe('failedJumpText (R68)', () => {
	it('says the jump failed when the ship jumped but waits to land nowhere', () => {
		assert.equal(failedJumpText(game({ jumpedShips: ['Wayfarer'] }), 'Wayfarer'), "Wayfarer's jump failed: it stays at Tau Ceti.");
	});

	it('says nothing when the ship made the jump and waits to land', () => {
		const landing = game({ jumpedShips: ['Wayfarer'] });
		landing.turn.landingShip = 'Wayfarer';
		assert.equal(failedJumpText(landing, 'Wayfarer'), '');
	});

	it("says nothing for a ship that hasn't jumped", () => {
		assert.equal(failedJumpText(game(), 'Wayfarer'), '');
	});
});
