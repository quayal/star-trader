import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	agentMoves,
	agentWhereText,
	agentOfferText,
	agentStarts,
	blameChoices,
	disabledText,
	isMyOpportunityTurn,
	loanPreview,
	openLoanText,
	opportunityStatusText,
	publicAgentLines,
	recruitOdds,
	sabotageCost,
	sabotageModifiers,
	sabotageTargets,
	shortyBonus,
	sufferedSabotageText,
	unpaidInterestWarning,
	validateRecruit,
} from '../../main/resources/static/js/opportunity.js';

const ME = 'p1';

function game({ opportunist = 'p2', doneOpportunity = [], owedChoices = [], players = 3, you = {} } = {}) {
	const all = [
		{ id: 'p1', corporationName: 'Orion Freight', ships: [], warehouses: [], factories: [], agentsOnOwn: [] },
		{
			id: 'p2', corporationName: 'Vega Lines',
			ships: [
				{ name: 'Starfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT', agents: 2 },
				{ name: 'Drift', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'SPACE', agents: 0 },
			],
			warehouses: [{ system: 'MU_HERCULIS', blocks: 1 }],
			factories: [{ system: 'TAU_CETI', goods: 'ISO', size: 4, disabledTurns: 0 }],
			agentsOnOwn: [{ system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT', count: 1 }],
		},
		{ id: 'p3', corporationName: 'Kessler Cargo', ships: [], warehouses: [], factories: [], agentsOnOwn: [] },
	].slice(0, players);
	return {
		id: 'g1',
		status: 'RUNNING',
		turn: { number: 2, phase: 'OPPORTUNITY', opportunist, doneOpportunity, owedChoices },
		players: all,
		board: [
			{ id: 'TAU_CETI', name: 'Tau Ceti', legalSystem: 2 },
			{ id: 'MU_HERCULIS', name: 'Mu Herculis', legalSystem: 3 },
		],
		you,
	};
}

describe('the Opportunity turn (R102)', () => {
	it('says whose turn it is and that the viewer comes after them', () => {
		assert.equal(isMyOpportunityTurn(game(), ME), false);
		assert.equal(opportunityStatusText(game(), ME), 'Your turn comes after Vega Lines.');
		assert.match(opportunityStatusText(game({ opportunist: ME }), ME), /^It's your Opportunity turn/);
		assert.equal(opportunityStatusText(game({ doneOpportunity: [ME] }), ME), "You're done with opportunities. Vega Lines is on turn.");
	});

	it('names who the phase still waits for once everyone is done', () => {
		assert.equal(opportunityStatusText(game({ opportunist: null, owedChoices: ['p2'] }), ME), 'Waiting for Vega Lines to place sabotage hits.');
		assert.match(opportunityStatusText(game({ opportunist: null, owedChoices: [ME] }), ME), /^Place the sabotage hits/);
	});
});

describe('sabotage (R97)', () => {
	it('lists other players’ ships in Port, warehouses and factories', () => {
		const targets = sabotageTargets(game(), ME);
		assert.deepEqual(targets.map(t => t.label), [
			'Vega Lines — Starfarer in Port at Tau Ceti',
			'Vega Lines — warehouse at Mu Herculis',
			'Vega Lines — ISO factory at Tau Ceti',
		]);
		assert.equal(sabotageTargets(game(), 'p2').length, 0);
	});

	it('costs 20 HT a legal level and shows the modifiers', () => {
		const [ship, warehouse, factory] = sabotageTargets(game(), ME);
		assert.equal(sabotageCost(game(), ship), 40);
		assert.equal(sabotageCost(game(), warehouse), 60);
		assert.deepEqual(sabotageModifiers(3, factory), { effect: 'Effect +3 criminal −4 factory units', reputation: 'Reputation +3 criminal' });
	});

	it('adds Shorty when he is at the target’s system, wherever there (R108, playtest 2026-11-21)', () => {
		const ship = { kind: 'SHIP', system: 'TAU_CETI', systemName: 'Tau Ceti' };
		const you = agents => ({ you: { agents } });
		const shorty = { agent: 'SHORTY', placeSystemName: 'Tau Ceti' };
		assert.equal(shortyBonus(you([shorty]), ship, 4), 4);
		assert.equal(shortyBonus(you([{ ...shorty, placeSystemName: 'Mu Herculis' }]), ship, 4), 0);
		assert.equal(shortyBonus(you([{ ...shorty, agent: 'WOLF' }]), ship, 4), 0);
		assert.equal(shortyBonus(you([shorty]), null, 4), 0);
		assert.deepEqual(sabotageModifiers(5, ship, 4), { effect: 'Effect +5 criminal +4 Shorty', reputation: 'Reputation +5 criminal +4 Shorty' });
	});

	it('names an innocent player only with three or more', () => {
		assert.deepEqual(blameChoices(game(), ME, 'p2'), [{ id: 'p3', name: 'Kessler Cargo' }]);
		assert.deepEqual(blameChoices(game({ players: 2 }), ME, 'p2'), []);
	});

	it('tells the target what happened without naming anyone', () => {
		const text = sufferedSabotageText({ kind: 'WAREHOUSE', systemName: 'Tau Ceti', effect: 3, lost: { ISO: 2, POLY: 1 } });
		assert.equal(text, 'Your warehouse at Tau Ceti was sabotaged: 2 ISO, 1 POLY missing.');
		assert.equal(sufferedSabotageText({ kind: 'SHIP', ship: 'Starfarer', systemName: 'Tau Ceti', effect: 2, awaitingHits: true, lost: {} }),
			'Starfarer at Tau Ceti was sabotaged: place 2 hits.');
	});

	it('marks a disabled factory', () => {
		assert.equal(disabledText({ disabledTurns: 2 }), 'disabled, 2 more turns');
		assert.equal(disabledText({ disabledTurns: 0 }), '');
	});
});

describe('agents (R103–R106)', () => {
	it('gives the odds of 2d6 × 5 at or below the payment', () => {
		assert.equal(recruitOdds(10), '1/36');
		assert.equal(recruitOdds(35), '21/36');
		assert.equal(recruitOdds(60), '36/36');
		assert.deepEqual(validateRecruit('61'), { error: 'Pay a whole number from 10 to 60 HT.' });
		assert.deepEqual(validateRecruit(' 35 '), { amount: 35 });
	});

	it('starts aboard a ship out of the yard or where the player has one', () => {
		const mine = game({ you: { id: ME, warehouses: [] } });
		mine.players[0].ships = [
			{ name: 'Wayfarer', system: 'TAU_CETI', systemName: 'Tau Ceti', location: 'PORT' },
			{ name: 'Spiral', system: 'MU_HERCULIS', systemName: 'Mu Herculis', location: 'SHIPYARD' },
		];
		assert.deepEqual(agentStarts(mine).map(s => s.label), [
			'Aboard Wayfarer (Tau Ceti)',
			'On its own: Port at Tau Ceti',
			'On its own: Planet at Tau Ceti',
		]);
	});

	it('moves only at the same place', () => {
		const mine = game({ you: { id: ME } });
		mine.players[0].ships = [
			{ name: 'Wayfarer', system: 'TAU_CETI', location: 'PORT' },
			{ name: 'Lantern', system: 'TAU_CETI', location: 'PORT' },
			{ name: 'Corvid', system: 'TAU_CETI', location: 'SPACE' },
		];
		const moves = agentMoves(mine, { agent: 'NOVAK', ship: 'Wayfarer' });
		assert.deepEqual(moves.map(m => m.label), ['Board Lantern', 'Step off (Port)']);
	});

	it('never moves an agent aboard a ship in Space (R104, playtest 2026-11-20)', () => {
		const mine = game({ you: { id: ME } });
		mine.players[0].ships = [
			{ name: 'Wayfarer', system: 'TAU_CETI', location: 'SPACE' },
			{ name: 'Corvid', system: 'TAU_CETI', location: 'SPACE' },
		];
		assert.deepEqual(agentMoves(mine, { agent: 'NOVAK', ship: 'Wayfarer' }), []);
	});

	it("says where an agent is, its ship's place when aboard", () => {
		const aboard = { agent: 'NOVAK', ship: 'Wayfarer', placeSystemName: 'Tau Ceti', placeLocation: 'PORT' };
		const alone = { agent: 'JB', ship: null, system: 'MU_HERCULIS', location: 'PLANET', placeSystemName: 'Mu Herculis', placeLocation: 'PLANET' };
		assert.equal(agentWhereText(game(), aboard), 'aboard Wayfarer, in Port at Tau Ceti');
		assert.equal(agentWhereText(game(), alone), 'on his own on the Planet at Mu Herculis');
	});

	it('shows counts per place and per ship, never names', () => {
		assert.deepEqual(publicAgentLines(game().players[1]), ['1 agent in Port at Tau Ceti', 'Starfarer: 2 agents aboard']);
	});

	it('tells the buyer where the agent is and the seller which one', () => {
		const offer = { id: 1, sellerId: 'p2', buyerId: ME, price: 25, systemName: 'Tau Ceti', location: 'PORT', agent: null };
		assert.equal(agentOfferText(game(), offer, ME), 'Vega Lines offers you an agent for 25 HT (Port at Tau Ceti).');
		assert.equal(agentOfferText(game(), { ...offer, agent: 'Novak' }, 'p2'), 'You offered Novak to Orion Freight for 25 HT (Port at Tau Ceti).');
	});
});

describe('loans (R90–R95)', () => {
	const loan = { cap: 500, terms: [{ turns: 4, divisor: 20 }, { turns: 8, divisor: 10 }], minInterest: 1, loan: null };

	it('previews both interests and refuses above the cap', () => {
		assert.equal(loanPreview('300', loan, 20).text, '4 turns: 14 HT a turn · 8 turns: 28 HT a turn');
		assert.equal(loanPreview('15', loan, 20).text, '4 turns: 1 HT a turn · 8 turns: 1 HT a turn');
		assert.deepEqual(loanPreview('501', loan, 20), { error: 'You can borrow at most 500 HT.' });
	});

	it('summarises an open loan and warns about unpaid interest', () => {
		const open = { ...loan, interestDue: true, interestPaid: false, loan: { amount: 312, interest: 14, turnsLeft: 1, fallsDue: true } };
		assert.equal(openLoanText(open),
			"You owe 312 HT, 14 HT interest a turn, 1 payment left. This turn's interest (14 HT) is unpaid. Your loan (312 HT) falls due when this Investment closes.");
		assert.equal(unpaidInterestWarning({ you: { loan: open } }), "Your loan interest (14 HT) is unpaid: −4 reputation, and it's added to your loan.");
		assert.equal(unpaidInterestWarning({ you: { loan: { ...open, interestPaid: true } } }), '');
	});
});
