import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { NOTEPAD_MAX_LENGTH, canEditNotepad, charactersLeftText, notepadStatus } from '../../main/resources/static/js/notepad.js';

describe('the notepad (R135)', () => {
	it('words every state of saving', () => {
		assert.equal(notepadStatus('saved'), 'Saved');
		assert.equal(notepadStatus('dirty'), 'Not saved yet…');
		assert.equal(notepadStatus('saving'), 'Saving…');
		assert.match(notepadStatus('failed'), /Couldn’t save/);
		assert.match(notepadStatus('readonly'), /read-only/);
		assert.equal(notepadStatus('nonsense'), '');
	});

	it('counts the characters left only near the limit', () => {
		assert.equal(charactersLeftText(0), '');
		assert.equal(charactersLeftText(4499), '');
		assert.equal(charactersLeftText(4500), '500 of 5,000 characters left');
		assert.equal(charactersLeftText(NOTEPAD_MAX_LENGTH), '0 of 5,000 characters left');
	});

	it('lets a player still in the game edit, and nobody else', () => {
		const game = eliminated => ({ you: { id: 'p1' }, players: [{ id: 'p1', eliminated }, { id: 'p2', eliminated: false }] });

		assert.equal(canEditNotepad(game(false)), true);
		assert.equal(canEditNotepad(game(true)), false);
		assert.equal(canEditNotepad({ you: null, players: [] }), false);
	});
});
