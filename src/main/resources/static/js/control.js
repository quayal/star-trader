/*
 * Pure helpers for the Control phase (R128–R131): the question a player is asked when another corporation fails its reputation
 * control, and what the top bar says while the game waits for the answers. They read `turn.controlSubject` (public: the
 * corporation under control), `you.backingAsk` (private: set only for a player still asked) and `players[].eliminated` from
 * GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6).
 */

/** Escape counts as "No": a player who closes the dialog without choosing doesn't back anyone. */
export const ESCAPE_BACKS = false;

/** The corporation the viewer is asked to back, `{ subject }`, or null. It stays non-null until they answer, so a reload asks again (D09). */
export function backingFor(game) {
	const id = game.you?.backingAsk;
	const subject = id && game.players.find(p => p.id === id);
	return subject ? { subject } : null;
}

/** "Vega Lines: reputation control". */
export function backingHeading(backing) {
	return `${backing.subject.corporationName}: reputation control`;
}

/** The question, with what backing risks. */
export function backingQuestion(backing) {
	return `${backing.subject.corporationName} (reputation ${backing.subject.reputation}) is under investigation. Back them? Your political connections then help lower their second roll, but if they are eliminated you lose political and other connections and 2d6 reputation. Only one corporation may back them.`;
}

export const BACKING_YES_LABEL = 'Yes, back them';
export const BACKING_NO_LABEL = 'No, stay out of it';

/** What to announce once the question is answered. */
export function backingAnsweredText(subjectName, back) {
	return back ? `You back ${subjectName}.` : `You stay out of ${subjectName}’s control.`;
}

/** Whether the game is in Control, waiting for a backer. */
export function isControlWaiting(game) {
	return game.status === 'RUNNING' && game.turn?.phase === 'CONTROL' && Boolean(game.turn.controlSubject);
}

/** The top bar's words while Control waits: "reputation control of Vega Lines: waiting for a backer", or null otherwise. */
export function controlText(game) {
	if (!isControlWaiting(game)) {
		return null;
	}
	const subject = game.players.find(p => p.id === game.turn.controlSubject);
	return subject ? `reputation control of ${subject.corporationName}, waiting for backing` : null;
}

/** "eliminated" for a corporation out of the game (R129), else null: the Corporations tab shows it instead of assets. */
export function eliminatedText(player) {
	return player.eliminated ? 'Eliminated from the game' : null;
}
