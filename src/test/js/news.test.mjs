import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { newsAnnouncements, tipOffGroups, tipOffRows, turnHeading, waitingForNews } from '../../main/resources/static/js/news.js';

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
		assert.equal(row.effectiveTurn, 5);
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

	it('groups the tip-offs by the turn they take place or expire, soonest first (playtest, 2026-11-21)', () => {
		const at = (slot, effectiveTurn) => ({ ...hidden, slot, effectiveTurn });
		const groups = tipOffGroups(game({ tipOffs: [at(1, 6), at(2, 4), at(3, 3), at(4, 4)] }));
		assert.deepEqual(groups.map(group => group.heading), ['This turn (turn 3)', 'Next turn (turn 4)', 'Turn 6']);
		assert.deepEqual(groups.map(group => group.rows.map(row => row.slot)), [[3], [2, 4], [1]]);
		assert.deepEqual(tipOffGroups(game()), []);
	});

	it('names a turn relative to the current one', () => {
		assert.equal(turnHeading(3, 3), 'This turn (turn 3)');
		assert.equal(turnHeading(4, 3), 'Next turn (turn 4)');
		assert.equal(turnHeading(7, 3), 'Turn 7');
	});
});
