/*
 * Pure helpers for picking places in the turn order (E04-S01) on the Deck (s01) and in the top bar. They read
 * `turn.initiative` and `turn.turnOrder` (one entry per place, an email or null while free) from GET /api/games/{id}
 * (BACKWARD_COMPATIBILITY.md §6). Everything here is public (R26).
 */

export const TURN_ORDER_NOTE = 'Chosen in initiative order; applies to the whole turn.';
export const PICK_LEGEND = 'Choose your place in the turn order';
export const PICK_BUTTON = 'Confirm position';
export const FREE_PLACE = '—';

/** 1st, 2nd, 3rd, 4th … (at most six players, so no 11th–13th). */
export function ordinal(place) {
	return `${place}${{ 1: 'st', 2: 'nd', 3: 'rd' }[place] ?? 'th'}`;
}

function places(game) {
	return game.turn?.turnOrder ?? [];
}

/**
 * Whose pick it is (R26, R27): during Ship-jumps, the first player in initiative order without a place, or null
 * once the order is set (and outside Ship-jumps).
 */
export function picker(game) {
	if (game.status !== 'RUNNING' || game.turn?.phase !== 'SHIP_JUMPS' || places(game).length === 0) {
		return null;
	}
	return (game.turn.initiative ?? []).find(email => !places(game).includes(email)) ?? null;
}

function corporation(game, email) {
	return game.players.find(p => p.email === email)?.corporationName ?? email;
}

/** S01-2: the top bar's addition while someone picks, "" otherwise. */
export function pickingText(game, email) {
	const who = picker(game);
	if (who === null) {
		return '';
	}
	return who === email ? 'choose your place in the turn order' : `${corporation(game, who)} is choosing their place in the turn order`;
}

/** S01-3: every place with who holds it, "—" for a free one. Empty during Bidding. */
export function turnOrderRows(game) {
	return places(game).map((email, index) => ({
		place: ordinal(index + 1),
		holder: email === null ? FREE_PLACE : corporation(game, email),
	}));
}

/** S01-4: the free places (1 = first) the player with [email] may pick from, or [] when it isn't their pick. */
export function freePlaces(game, email) {
	if (picker(game) !== email) {
		return [];
	}
	return places(game).flatMap((holder, index) => (holder === null ? [index + 1] : []));
}

/** S01-4: "You act 2nd this turn." once the player has a place, "" before. */
export function youActText(game, email) {
	const index = places(game).indexOf(email);
	return index === -1 ? '' : `You act ${ordinal(index + 1)} this turn.`;
}
