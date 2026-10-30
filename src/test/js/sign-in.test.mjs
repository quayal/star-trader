import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { codeSentMessage, errorText, fieldForDetailsError } from '../../main/resources/static/js/sign-in.js';

describe('fieldForDetailsError', () => {
	it("puts each of E01-S01's messages on its field", () => {
		assert.equal(fieldForDetailsError('Player name is required'), 'playerName');
		assert.equal(fieldForDetailsError('Corporation name is required'), 'corporationName');
		assert.equal(fieldForDetailsError('Email address is invalid'), 'email');
	});

	it('leaves any other message for the whole form', () => {
		assert.equal(fieldForDetailsError("We couldn't send the code. Try again."), null);
		assert.equal(fieldForDetailsError(''), null);
		assert.equal(fieldForDetailsError(undefined), null);
	});
});

describe('codeSentMessage', () => {
	it('names the email the code went to', () => {
		assert.equal(codeSentMessage('ada@example.com'), 'We sent a code to ada@example.com. It may take a minute.');
	});
});

describe('errorText', () => {
	it("returns the server's reason", async () => {
		assert.equal(await errorText({ text: async () => 'The code is not valid\n' }, 'fallback'), 'The code is not valid');
	});

	it('falls back when the server sent no reason', async () => {
		assert.equal(await errorText({ text: async () => '' }, 'Something went wrong.'), 'Something went wrong.');
	});
});
