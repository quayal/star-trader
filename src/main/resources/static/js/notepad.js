/*
 * Pure helpers for the Deck's notepad (R135): free text the player keeps for planning their next turns, private to them, saved
 * as they type. They only word the state; GET and PUT /api/games/{id}/notepad (`{ text }`) do the keeping
 * (BACKWARD_COMPATIBILITY.md §6).
 */

/** The most a notepad holds (R135); the server refuses more. */
export const NOTEPAD_MAX_LENGTH = 5000;

/** How long after the last keystroke the text is saved, in milliseconds. */
export const SAVE_DELAY_MS = 800;

export const NOTEPAD_HINT = 'Only you can see this. Use it for what you plan to do in the next turns.';

/** What the status line says: nothing while loading, then Saved, Saving…, Not saved yet, or the last refusal. */
export function notepadStatus(state) {
	return {
		loading: 'Loading…',
		saved: 'Saved',
		dirty: 'Not saved yet…',
		saving: 'Saving…',
		failed: 'Couldn’t save. Your text is still here; it will try again as you type.',
		readonly: 'You are out of the game: your notes are read-only.',
	}[state] ?? '';
}

/** "4,950 of 5,000 characters left", shown once the text is close to the limit; '' before. */
export function charactersLeftText(length) {
	const left = NOTEPAD_MAX_LENGTH - length;
	return left > 500 ? '' : `${left.toLocaleString('en-US')} of ${NOTEPAD_MAX_LENGTH.toLocaleString('en-US')} characters left`;
}

/** Whether the viewer, in [game], may edit their notes: a player still in it, in a game that exists. */
export function canEditNotepad(game) {
	const me = (game.players ?? []).find(player => player.id === game.you?.id);
	return Boolean(me) && !me.eliminated;
}
