import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { MAX_TOASTS, cardsFrom, evasionFirst, moveCard, noticeCards, toasts, untradedCards } from '../../main/resources/static/js/notices.js';

const players = [
	{ id: 'p1', corporationName: 'Orion Freight' },
	{ id: 'p2', corporationName: 'Evil Corp' },
	{ id: 'p3', corporationName: 'Vega Lines' },
];

const tried = success => ({
	kind: 'INTERCEPTION', system: 'TAU_CETI', systemName: 'Tau Ceti', playerIds: ['p2', 'p1'], round: null,
	interception: {
		interceptorId: 'p2', interceptorShip: 'Meridian', interceptorRoll: 'Meridian (2 + d6 3 + 0 = 5)',
		arrivingId: 'p1', arrivingShip: 'Wayfarer', arrivingRoll: 'Wayfarer (2 + d6 4 + 0 = 6)', success,
	},
});
const battle = (kind, round = null) => ({ kind, system: 'TAU_CETI', systemName: 'Tau Ceti', playerIds: ['p1', 'p2'], round, interception: null });

function game(log, extra = {}) {
	return { id: 'g1', status: 'RUNNING', players, log, turn: { number: 2, phase: 'JUMPS' }, ...extra };
}

describe('noticeCards (creator, 2026-11-18)', () => {
	const failed = game([
		{ turn: 2, text: 'Jump: Orion Freight — Wayfarer to Tau Ceti' },
		{ turn: 2, text: 'Interception: Meridian … vs Wayfarer …: escapes', notice: tried(false) },
		{ turn: 2, text: 'Evil Corp’s reputation: −4 …', notice: tried(false) },
	]);

	it('tells the interceptor their interception failed, with both rolls and the reputation line', () => {
		const [card] = noticeCards(failed, 0, 'p2');

		assert.equal(card.title, 'Your interception failed');
		assert.equal(card.lead, 'Orion Freight’s Wayfarer evaded your Meridian at Tau Ceti.');
		assert.deepEqual(card.lines, ['Rolls: Meridian (2 + d6 3 + 0 = 5) vs Wayfarer (2 + d6 4 + 0 = 6)', 'Evil Corp’s reputation: −4 …']);
		assert.equal(card.end, 3);
		assert.equal(card.key, 'g1/log/1');
	});

	it('tells the intercepted player who tried, with what, and that they evaded', () => {
		const [card] = noticeCards(failed, 0, 'p1');

		assert.equal(card.title, 'You evaded an interception');
		assert.equal(card.lead, 'Evil Corp tried to intercept your Wayfarer with Meridian at Tau Ceti. Wayfarer evaded them.');
	});

	it('shows the evaded card before the landing dialog, only to the player who evaded (creator, 2026-11-19)', () => {
		const caught = game([{ turn: 2, text: '…: intercepted', notice: tried(true) }]);

		assert.equal(evasionFirst(noticeCards(failed, 0, 'p1')), true);
		assert.equal(evasionFirst(noticeCards(failed, 0, 'p2')), false);
		assert.equal(evasionFirst(noticeCards(caught, 0, 'p1')), false);
		assert.equal(evasionFirst(noticeCards(failed, 3, 'p1')), false);
	});

	it('words a success for each side, and tells a bystander too', () => {
		const caught = game([{ turn: 2, text: '…: intercepted', notice: tried(true) }]);

		assert.equal(noticeCards(caught, 0, 'p2')[0].title, 'Your interception succeeded');
		assert.equal(noticeCards(caught, 0, 'p1')[0].title, 'You were intercepted');
		assert.equal(noticeCards(caught, 0, 'p3')[0].lead, 'Evil Corp tried to intercept Orion Freight’s Wayfarer with Meridian. It was caught: a battle starts.');
	});

	it('makes one card per battle round for a bystander, and leaves it to the battle report for those fighting', () => {
		const fight = game([
			{ turn: 2, text: 'Battle at Tau Ceti: Wayfarer, Meridian', notice: battle('BATTLE_STARTED') },
			{ turn: 2, text: 'Battle at Tau Ceti, round 1', notice: battle('BATTLE_ROUND', 1) },
			{ turn: 2, text: 'Salvo: Meridian → Wayfarer: 2 hits', notice: battle('BATTLE_ROUND', 1) },
			{ turn: 2, text: 'Wayfarer is destroyed', notice: battle('SHIP_DESTROYED') },
			{ turn: 2, text: 'The battle at Tau Ceti is over', notice: battle('BATTLE_OVER') },
		]);

		assert.deepEqual(noticeCards(fight, 0, 'p3').map(card => card.title),
			['Battle at Tau Ceti', 'Battle at Tau Ceti — round 1', 'Ship destroyed', 'The battle at Tau Ceti is over']);
		assert.deepEqual(noticeCards(fight, 0, 'p3')[1].lines, ['Salvo: Meridian → Wayfarer: 2 hits']);
		assert.deepEqual(noticeCards(fight, 0, 'p1').map(card => card.title),
			['Battle at Tau Ceti', 'Ship destroyed', 'The battle at Tau Ceti is over']);
	});

	it('starts from where this browser left off', () => {
		assert.deepEqual(noticeCards(failed, 3, 'p2'), []);
		assert.equal(cardsFrom(undefined, 7), 7);
		assert.equal(cardsFrom(4, 7), 4);
		assert.equal(cardsFrom(9, 7), 7);
	});
});

describe('moveCard', () => {
	it('asks once per step, keyed by it, and takes the player where it is done', () => {
		const asked = game([], { turn: { number: 2, phase: 'JUMPS', interception: { ship: 'Wayfarer' } }, you: { id: 'p2', interception: { answered: false, ships: ['Meridian'] } } });

		assert.deepEqual(moveCard(asked, 'p2'), {
			key: 'g1/2/intercept/Wayfarer',
			title: 'Your move: intercept?',
			lead: 'Wayfarer has arrived where your ships wait in Space. Choose whether to try to intercept it.',
			link: '#game/g1',
			focus: 'fight-title',
		});
	});

	it('says how much the trader can trade', () => {
		const good = { system: 'TAU_CETI', systemName: 'Tau Ceti', goods: 'ISO' };
		const trading = game([], {
			turn: { number: 2, phase: 'TRADING', initiative: ['p1'], tradingQueue: [], resolvedGoods: [], resolution: { good, awaitingTrade: { playerId: 'p1', type: 'SELL', price: 9, maxUnits: 5 } } },
			you: { id: 'p1', tradeMax: 4 },
		});

		const card = moveCard(trading, 'p1');
		assert.equal(card.key, 'g1/2/trade/TAU_CETI/ISO');
		assert.equal(card.lead, 'The market has reached your sell of ISO at Tau Ceti at 9 HT. You can sell 4 u.');
		assert.equal(card.link, '#game/g1/trading');
	});

	it('shows the Dictator the market price of the good (creator, 2026-11-20)', () => {
		const good = { system: 'TAU_CETI', systemName: 'Tau Ceti', goods: 'ISO' };
		const asking = game([], {
			board: [{ id: 'TAU_CETI', market: [{ goods: 'ISO', price: 12 }] }],
			turn: { number: 2, phase: 'TRADING', initiative: ['p1'], tradingQueue: [], resolvedGoods: [], resolution: { good, dictator: 'p1', awaitingDictator: 'p1', offers: [] } },
			you: { id: 'p1' },
		});

		assert.equal(moveCard(asking, 'p1').lead, 'You’re Dictator of ISO at Tau Ceti (market price 12 HT). Place an offer or pass.');
	});

	it('says only that it is the player\'s turn to jump (creator, 2026-11-20)', () => {
		const jumping = game([], { turn: { number: 2, phase: 'JUMPS', initiative: ['p1'], turnOrder: ['p1'], doneJumping: [] }, you: { id: 'p1' } });

		assert.equal(moveCard(jumping, 'p1')?.lead, 'It’s your turn to jump ships.');
	});

	it('has nothing when the game waits on someone else', () => {
		assert.equal(moveCard(game([], { you: { id: 'p3' } }), 'p3'), null);
	});
});

describe('toasts', () => {
	it('shows the newest lines without a card, and counts the rest', () => {
		const log = ['a', 'b', 'c', 'd', 'e'].map(text => ({ turn: 1, text })).concat([{ turn: 1, text: 'card', notice: battle('BATTLE_OVER') }]);

		assert.deepEqual(toasts(log, 0), { shown: ['c', 'd', 'e'], more: 2 });
		assert.deepEqual(toasts(log, 4), { shown: ['e'], more: 0 });
		assert.equal(MAX_TOASTS, 3);
	});
});

describe('battle summary and untraded offers (playtest, 2026-11-20)', () => {
	it('shows a finished battle in one card, by section', () => {
		const sections = [{ title: 'Round 1', lines: ['Meridian fires at Starfarer: 3 hits'] }, { title: 'Aftermath', lines: ['Vega Lines: reputation −7'] }];
		const game = {
			id: 'g',
			players: [],
			log: [{ turn: 2, text: 'Summary of the battle at Tau Ceti', notice: { kind: 'BATTLE_SUMMARY', system: 'TAU_CETI', systemName: 'Tau Ceti', playerIds: [], round: null }, battleSummary: sections }],
		};
		const [card] = noticeCards(game, 0, 'me');
		assert.equal(card.title, 'Battle at Tau Ceti — summary');
		assert.deepEqual(card.sections, sections);
	});

	it('gives each offer that resolved without trading its own card, once per turn and offer', () => {
		const game = { id: 'g', turn: { number: 3 }, you: { untradedOffers: [{ system: 'TAU_CETI', systemName: 'Tau Ceti', goods: 'ISO', type: 'BUY', price: 12 }] } };
		const [card] = untradedCards(game);
		assert.equal(card.key, 'g/3/untraded/TAU_CETI/ISO/BUY');
		assert.match(card.lead, /^Your ISO buy at Tau Ceti \(12 HT\) didn’t trade/);
		assert.deepEqual(untradedCards({ id: 'g', turn: { number: 3 }, you: {} }), []);
	});
});
