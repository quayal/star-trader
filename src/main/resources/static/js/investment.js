/*
 * Pure helpers for the Investment phase (E06-S01, R44) in the top bar of the Deck (s01) and System details (s02). They
 * read `turn.phase` and `turn.doneInvesting` from GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6):
 * `turn.doneInvesting` is the public list of players' emails who signalled done (D09); nothing private is needed.
 */

export const DONE_INVESTING_BUTTON = "I'm done investing";
/** S01-2: shown in place of the button once the player has signalled done. */
export const DONE_INVESTING_TEXT = 'You signalled done investing';
export const INVESTMENT_CLOSED_BY_YOU = 'Everyone is done. Investment has closed.';
export const ALREADY_CLOSED_INVESTING = 'Investment has already closed.';
export const INVESTMENT_NOT_OPEN = "Investment isn't open.";

/** Whether [game] is running and in this turn's Investment. */
export function isInvesting(game) {
	return game.status === 'RUNNING' && game.turn?.phase === 'INVESTMENT';
}

/** Whether the player with [email] has signalled done in this Investment. */
export function isDoneInvesting(game, email) {
	return isInvesting(game) && game.turn.doneInvesting.includes(email);
}

/**
 * Who the game is still waiting for during Investment (S01-3, D09): "you" first while the viewer isn't done, then the
 * other players' corporation names in join order. Empty outside Investment.
 */
export function waitingForInvesting(game, email) {
	if (!isInvesting(game)) {
		return [];
	}
	const pending = game.players.filter(p => !game.turn.doneInvesting.includes(p.email));
	const you = pending.some(p => p.email === email) ? ['you'] : [];
	return [...you, ...pending.filter(p => p.email !== email).map(p => p.corporationName)];
}

/** What to say once "done" was accepted: nothing while others still invest, or that this player's signal closed it. */
export function doneInvestingAcceptedText(game) {
	return isInvesting(game) ? '' : INVESTMENT_CLOSED_BY_YOU;
}

/** Whether a refused "done" only means Investment closed before the click (A04), which isn't the player's error. */
export function isInvestmentClosedRefusal(status, message) {
	return status === 409 && message === INVESTMENT_NOT_OPEN;
}

/** What to say when "done" was refused: a close that raced the click isn't the player's error (A04). */
export function doneInvestingRefusedText(status, message, fallback) {
	return isInvestmentClosedRefusal(status, message) ? ALREADY_CLOSED_INVESTING : message || fallback;
}
