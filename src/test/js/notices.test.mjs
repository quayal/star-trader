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

describe('News cards (playtest, 2026-11-21)', () => {
	const news = kind => ({ kind, system: null, systemName: null, playerIds: [], round: null, interception: null });

	it('pops up an event and an expired opportunity for everyone, one card each', () => {
		const cards = noticeCards(game([
			{ turn: 3, text: 'News: Epidemics: FOOD prices rise.', notice: news('NEWS_EVENT') },
			{ turn: 3, text: 'News: the opportunity to buy drugs has expired.', notice: news('OPPORTUNITY_EXPIRED') },
		]), 0, 'p1');
		assert.deepEqual(cards.map(card => [card.title, card.lead]), [
			['News', 'Epidemics: FOOD prices rise.'],
			['An opportunity expired', 'The opportunity to buy drugs has expired.'],
		]);
	});

	it('lists what an event did under its announcement, in the same card', () => {
		const [card] = noticeCards(game([
			{ turn: 3, text: 'News: Pirates attack Mu Herculis.', notice: news('NEWS_EVENT') },
			{ turn: 3, text: 'Lantern (Orion Freight) was lost to the pirates (rolled 2)', notice: news('NEWS_EVENT') },
		]), 0, 'p1');
		assert.equal(card.lead, 'Pirates attack Mu Herculis.');
		assert.deepEqual(card.lines, ['Lantern (Orion Freight) was lost to the pirates (rolled 2)']);
	});
});

describe('the seizure card (creator, 2026-11-23)', () => {
	const seized = { kind: 'SHIP_SEIZED', system: 'TAU_CETI', systemName: 'Tau Ceti', playerIds: ['p3'], round: null, interception: null };
	const log = game([
		{ turn: 2, text: 'Jump: Vega Lines — Meridian to Tau Ceti' },
		{ turn: 2, text: 'Meridian (Vega Lines) was seized at Tau Ceti: 6 vs security 7.', notice: seized },
		{ turn: 2, text: 'Vega Lines’s reputation: −13 for seized by security …', notice: seized },
	]);

	it('tells the owner their ship was seized, with the roll and the reputation loss in one card', () => {
		const [card] = noticeCards(log, 0, 'p3');

		assert.equal(card.title, 'Your ship was seized');
		assert.equal(card.lead, 'Planet security at Tau Ceti confiscated your ship and everything aboard.');
		assert.equal(card.tone, 'alert');
		assert.deepEqual(card.lines, ['Meridian (Vega Lines) was seized at Tau Ceti: 6 vs security 7.', 'Vega Lines’s reputation: −13 for seized by security …']);
		assert.equal(card.end, 3);
	});

	it('shows everyone else no card, but the lines as toasts (R124)', () => {
		assert.deepEqual(noticeCards(log, 0, 'p1'), []);
		assert.deepEqual(toasts(log.log, 0, 'p1').shown, [
			'Jump: Vega Lines — Meridian to Tau Ceti',
			'Meridian (Vega Lines) was seized at Tau Ceti: 6 vs security 7.',
			'Vega Lines’s reputation: −13 for seized by security …',
		]);
		assert.deepEqual(toasts(log.log, 0, 'p3').shown, ['Jump: Vega Lines — Meridian to Tau Ceti']);
	});

	it('gives every other setback its own card, worded for the player it happened to', () => {
		const setback = (kind, playerIds = ['p3']) => ({ kind, system: 'TAU_CETI', systemName: 'Tau Ceti', playerIds, round: null, interception: null });
		const titles = ['LANDING_FORCED', 'JUMP_FAILED', 'EXPEDITION_LOST', 'LOST_TO_PIRATES', 'NO_PROMOTION', 'DEMOTED']
			.map(kind => noticeCards(game([{ turn: 2, text: kind, notice: setback(kind) }]), 0, 'p3')[0]);

		assert.deepEqual(titles.map(card => card.title), [
			'Your landing failed', 'Your jump failed', 'Your expedition was lost', 'Your ship was lost to pirates',
			'No market position gained', 'You lost a market position',
		]);
		assert.ok(titles.every(card => card.tone === 'alert'));
	});
	it('gives a challenge a card for both players, worded for each, and a toast for the others (R126)', () => {
		const lines = ['Orion challenged Vega’s Dictator of CHIPS at Tau Ceti (political 8 vs 4), rolled 2: Vega loses the position', 'Vega’s reputation: −2 …'];
		const challenge = kind => game(lines.map(text => ({ turn: 2, text, notice: { kind, system: 'TAU_CETI', systemName: 'Tau Ceti', playerIds: ['p1', 'p3'], round: null, interception: null } })));
		const card = (kind, id) => noticeCards(challenge(kind), 0, id);

		assert.deepEqual(card('CHALLENGE_SUCCEEDED', 'p1').map(c => [c.title, c.tone, c.lines.length]), [['Your challenge succeeded', 'rise', 2]]);
		assert.deepEqual(card('CHALLENGE_SUCCEEDED', 'p3').map(c => [c.title, c.tone]), [['You lost a market position', 'alert']]);
		assert.deepEqual(card('CHALLENGE_FAILED', 'p1').map(c => [c.title, c.tone]), [['Your challenge failed', 'alert']]);
		assert.deepEqual(card('CHALLENGE_FAILED', 'p3').map(c => [c.title, c.tone]), [['You held your market position', 'rise']]);
		assert.deepEqual(card('CHALLENGE_SUCCEEDED', 'p2'), []);
		assert.deepEqual(toasts(challenge('CHALLENGE_SUCCEEDED').log, 0, 'p2').shown, lines);
		assert.deepEqual(toasts(challenge('CHALLENGE_SUCCEEDED').log, 0, 'p1').shown, []);
	});
});

describe('penalty cards (R136, creator 2026-11-24)', () => {
	const penalty = (kind, playerIds = ['p3'], ledgerReason = null) => ({ kind, system: null, systemName: null, playerIds, round: null, interception: null, ledgerReason });
	const undelivered = penalty('PASSENGERS_UNDELIVERED', ['p3'], 'Passengers not delivered');
	const log = [
		{ turn: 4, text: 'Vega Lines’s Meridian didn’t bring 2 passenger groups to Tau Ceti.', notice: undelivered },
		{ turn: 4, text: 'Vega Lines’s Lantern didn’t bring 1 passenger group to Beta Hydri.', notice: undelivered },
	];
	const ledger = [
		{ turn: 4, reason: 'Passengers not delivered', amount: -90, credit: false },
		{ turn: 3, reason: 'Passengers not delivered', amount: -40, credit: false },
		{ turn: 4, reason: 'Bought 3 ISO at Tau Ceti', amount: -36, credit: false },
	];

	it('tells the player their passengers weren’t delivered, in red, with what it cost them from their own ledger', () => {
		const [card] = noticeCards(game(log, { you: { ledger } }), 0, 'p3');

		assert.equal(card.title, 'Your passengers weren’t delivered');
		assert.equal(card.tone, 'alert');
		assert.match(card.lead, / You paid 90 HT\.$/);
		assert.deepEqual(card.lines, log.map(entry => entry.text));
	});

	it('leaves the amount out when nothing was paid, and gives everyone else the lines as toasts', () => {
		assert.doesNotMatch(noticeCards(game(log, { you: { ledger: [] } }), 0, 'p3')[0].lead, /paid/);
		assert.deepEqual(noticeCards(game(log), 0, 'p1'), []);
		assert.deepEqual(toasts(log, 0, 'p1').shown, log.map(entry => entry.text));
	});

	it('gives two players’ penalties logged one after the other a card each', () => {
		const both = game([
			{ turn: 4, text: 'Vega Lines’s reputation: −4 for unpaid loan interest', notice: penalty('INTEREST_UNPAID', ['p3']) },
			{ turn: 4, text: 'Orion Freight’s reputation: −4 for unpaid loan interest', notice: penalty('INTEREST_UNPAID', ['p1']) },
		]);

		assert.deepEqual(noticeCards(both, 0, 'p3').map(card => card.lines), [['Vega Lines’s reputation: −4 for unpaid loan interest']]);
		assert.deepEqual(noticeCards(both, 0, 'p1').map(card => card.lines), [['Orion Freight’s reputation: −4 for unpaid loan interest']]);
	});

	it('words every penalty for its player: red, but green for a control passed', () => {
		const cards = ['CONTROL_CLEARED', 'CONTROL_FINED', 'ELIMINATED', 'BACKING_LOST', 'INTEREST_UNPAID', 'LOAN_SEIZED', 'SABOTAGE_BLAMED', 'SABOTAGE_TRACED']
			.map(kind => noticeCards(game([{ turn: 2, text: kind, notice: penalty(kind) }]), 0, 'p3')[0]);

		assert.deepEqual(cards.map(card => [card.title, card.tone]), [
			['You passed the reputation control', 'rise'],
			['You were fined', 'alert'],
			['You’re out of the game', 'alert'],
			['Your backing cost you', 'alert'],
			['Your loan interest went unpaid', 'alert'],
			['Your loan was collected', 'alert'],
			['You were blamed for sabotage', 'alert'],
			['Your sabotage was traced', 'alert'],
		]);
	});

	it('turns News red for the players the event left worse off, and only for them', () => {
		const log = [{ turn: 3, text: 'News: Government screening.', notice: penalty('NEWS_EVENT', ['p1']) }];

		assert.deepEqual(noticeCards(game(log), 0, 'p1').map(card => [card.title, card.tone]), [['News — it cost you', 'alert']]);
		assert.deepEqual(noticeCards(game(log), 0, 'p2').map(card => [card.title, card.tone]), [['News', null]]);
	});
});

describe('a gained market position (R43, creator 2026-11-24)', () => {
	const game = {
		id: 'g1',
		players: [{ id: 'p1', corporationName: 'Vega Lines' }, { id: 'p2', corporationName: 'Orion Freight' }, { id: 'p3', corporationName: 'Kessler' }],
		log: [{ turn: 3, text: 'Vega Lines traded 7 u — rolled 5: now Dictator of ISO at Tau Ceti; Orion Freight is demoted to Wholesaler', notice: { kind: 'POSITION_GAINED', system: 'TAU_CETI', systemName: 'Tau Ceti', playerIds: ['p1', 'p2'], round: null, interception: null } }],
	};

	it('gives the trader a green card', () => {
		const [card] = noticeCards(game, 0, 'p1');

		assert.equal(card.title, 'You gained a market position');
		assert.equal(card.tone, 'rise');
		assert.deepEqual(card.lines, [game.log[0].text]);
	});

	it('gives the demoted Dictator a red one, and nobody else a card', () => {
		const [card] = noticeCards(game, 0, 'p2');

		assert.equal(card.title, 'You lost a market position');
		assert.equal(card.tone, 'alert');
		assert.deepEqual(noticeCards(game, 0, 'p3'), []);
	});
});
