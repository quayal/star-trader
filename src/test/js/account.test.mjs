import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { ACCOUNT_SHOWN, accountLines } from '../../main/resources/static/js/account.js';

describe('accountLines (playtest, 2026-11-20)', () => {
	const ledger = [
		{ turn: 4, reason: 'Sold 6 ISO at Tau Ceti', amount: 72, credit: true },
		{ turn: 4, reason: 'Loan interest', amount: -3, credit: false },
	];

	it('shows credits green with a plus and debits red with a minus, newest first', () => {
		assert.deepEqual(accountLines({ you: { ledger } }, false), {
			shown: [
				{ turn: 'T4', reason: 'Sold 6 ISO at Tau Ceti', amount: '+72', tone: 'rise' },
				{ turn: 'T4', reason: 'Loan interest', amount: '−3', tone: 'fall' },
			],
			more: 0,
		});
	});

	it('shows the newest few until expanded', () => {
		const long = Array.from({ length: ACCOUNT_SHOWN + 3 }, (_, n) => ({ turn: 1, reason: `M${n}`, amount: n + 1, credit: true }));
		assert.equal(accountLines({ you: { ledger: long } }, false).more, 3);
		assert.equal(accountLines({ you: { ledger: long } }, true).shown.length, ACCOUNT_SHOWN + 3);
	});

	it('shows nothing for a response without a ledger', () => {
		assert.deepEqual(accountLines({ you: {} }, false), { shown: [], more: 0 });
	});
});
