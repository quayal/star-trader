import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { loanBox } from '../../main/resources/static/js/loan-box.js';

const loan = { amount: 100, interest: 4, term: 4, turnsLeft: 2, fallsDue: false, repayment: 104, dueTurn: 5 };
const sentence = line => line.map(part => part.text).join('');
const bolded = line => line.filter(part => part.strong).map(part => part.text);

describe('the Loan box (R90–R95)', () => {
	it('shows nothing without an open loan', () => {
		assert.equal(loanBox(null, { loan: null }, 3), null);
		assert.equal(loanBox(undefined, undefined, 3), null);
	});

	it('words the amount, the interest and the turn it is paid, with the figures in bold', () => {
		const box = loanBox(loan, { loan, interestDue: true, interestPaid: false }, 4);

		assert.deepEqual(box.lines.map(sentence), [
			'You owe 100 HT',
			'Interest 4 HT a turn, 2 turns left',
			'The loan is paid in full in turn 5, as Investment closes',
			'Turn 4’s interest is due in Investment',
		]);
		assert.deepEqual(box.lines.map(bolded), [['100 HT'], ['4 HT'], ['turn 5'], ['due in Investment']]);
		assert.match(box.note, /Repaying early costs 104 HT/);
	});

	it('says paid once this turn’s interest is, and leaves the line out when none is due', () => {
		assert.equal(sentence(loanBox(loan, { loan, interestDue: true, interestPaid: true }, 4).lines.at(-1)), 'Turn 4’s interest is paid');
		assert.equal(loanBox(loan, { loan, interestDue: false, interestPaid: false }, 4).lines.length, 3);
	});

	it('works the due turn out when the server does not send it, never printing undefined', () => {
		const old = { ...loan, dueTurn: undefined };

		assert.equal(sentence(loanBox(old, { loan: old, interestDue: true }, 4).lines[2]), 'The loan is paid in full in turn 5, as Investment closes');
	});

	it('words one turn left and none left', () => {
		assert.equal(sentence(loanBox({ ...loan, turnsLeft: 1 }, null, 5).lines[1]), 'Interest 4 HT a turn, 1 turn left');
		assert.equal(sentence(loanBox({ ...loan, turnsLeft: 0 }, null, 6).lines[1]), 'Interest 4 HT a turn, none left');
	});
});
