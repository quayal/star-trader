/*
 * Pure helpers for the Deck's "Your account" panel (playtest, 2026-11-20): the viewer's own money movements, private like
 * their money (R29). The server sends them newest first as `you.ledger` ({ turn, reason, amount, credit }); credits show
 * green and debits red, with the sign beside the colour.
 */

/** How many movements show before "Show all". */
export const ACCOUNT_SHOWN = 10;

export const NO_MOVEMENTS_TEXT = 'No money has moved yet.';

/**
 * The movements to show, `{ shown: [{ turn: 'T4', reason, amount: '+72', tone: 'rise' | 'fall' }], more }`: the newest
 * [ACCOUNT_SHOWN] unless [expanded], and how many more there are.
 */
export function accountLines(game, expanded) {
	const ledger = game.you?.ledger ?? [];
	const lines = ledger.map(movement => ({
		turn: `T${movement.turn}`,
		reason: movement.reason,
		amount: `${movement.credit ? '+' : '−'}${Math.abs(movement.amount)}`,
		tone: movement.credit ? 'rise' : 'fall',
	}));
	const shown = expanded ? lines : lines.slice(0, ACCOUNT_SHOWN);
	return { shown, more: lines.length - shown.length };
}
