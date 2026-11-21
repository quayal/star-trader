import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { newsAnnouncements, tipOffRows, waitingForNews } from '../../main/resources/static/js/news.js';

const ME = 'me';

function game({ phase = 'NEWS', tipOffs = [], doneNews = [], log = [] } = {}) {
	return {
		status: 'RUNNING',
		turn: { number: 3, phase, doneNews },
		players: [{ id: ME, corporationName: 'Orion Freight' }, { id: 'vega', corporationName: 'Vega Lines' }],
		you: { id: ME, tipOffs },
		log,
	};
}

describe('the News screen (R109–R110)', () => {
	const hidden = { slot: 3, need: 'CRIM 5', effectiveTurn: 5, learned: false, kind: null, details: null, taken: false, needRefusal: null };

	it('shows a tip-off’s need and turn to everyone and offers to learn it in News', () => {
		const [row] = tipOffRows(game({ tipOffs: [hidden] }));
		assert.equal(row.title, 'Tip-off #3');
		assert.equal(row.need, 'CRIM 5');
		assert.equal(row.turn, 'effective turn 5');
		assert.equal(row.details, null);
		assert.deepEqual(row.learn, { enabled: true, reason: '', label: 'Learn (5 HT)' });
	});

	it('says why it can’t be learned: the connection, or outside News', () => {
		const short = { ...hidden, needRefusal: 'You need CRIM 5 to learn this (yours: 3).' };
		assert.deepEqual(tipOffRows(game({ tipOffs: [short] }))[0].learn.reason, 'You need CRIM 5 to learn this (yours: 3).');
		assert.equal(tipOffRows(game({ phase: 'JUMPS', tipOffs: [hidden] }))[0].learn.reason, 'Tip-offs are learned in News.');
	});

	it('shows the details of a learned tip-off and no button', () => {
		const learned = { ...hidden, learned: true, kind: 'OPPORTUNITY', details: 'Opportunity: buy drugs …', taken: true };
		const [row] = tipOffRows(game({ tipOffs: [learned] }));
		assert.equal(row.details, 'Opportunity: buy drugs …');
		assert.equal(row.taken, 'Already taken.');
		assert.equal(row.learn, null);
	});

	it('waits for the players not yet done, and lists this turn’s announcements', () => {
		assert.deepEqual(waitingForNews(game({ doneNews: ['vega'] }), ME), ['you']);
		assert.deepEqual(waitingForNews(game({ phase: 'JUMPS' }), ME), []);
		const log = [{ turn: 2, text: 'News: old' }, { turn: 3, text: 'Vega Lines chose 1st' }, { turn: 3, text: 'News: Epidemics' }];
		assert.deepEqual(newsAnnouncements(game({ log })), ['News: Epidemics']);
	});
});
