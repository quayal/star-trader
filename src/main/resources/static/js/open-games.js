/*
 * Open games (R119, R120): every game still in its lobby, listed on the Lobby screen so players need only the
 * service's address, not a link with a game id. The server says where the viewer stands in each (`IN`, `ASKED`,
 * `NONE`); this module only turns that into text and the action the row offers.
 */

import { lengthText } from './game.js';

/** ms between two loads of the list while the Lobby screen shows it. */
export const OPEN_GAMES_POLL_MS = 4000;

/** "Orion Freight (Ada)": who created the game. Plain text: the page puts it in with textContent. */
export function creatorText(game) {
	return `${game.creator.corporationName} (${game.creator.name})`;
}

/**
 * One row: [title] and [details] as text, a [badge] `{ text, tone }` or null, and the [action]: `open` (the viewer is
 * in), `cancel` (they asked), `ask`, or null for a full game they can't ask to join.
 */
export function openGameRow(game) {
	const details = `${lengthText(game.turns)} · ${game.players} of ${game.maxPlayers} players · ${game.corporations.join(', ')}`;
	const row = { id: game.id, title: creatorText(game), details, href: `/#game/${encodeURIComponent(game.id)}` };
	if (game.standing === 'IN') {
		return { ...row, badge: { text: "You're in", tone: 'live' }, action: 'open' };
	}
	if (game.standing === 'ASKED') {
		return { ...row, badge: { text: `Waiting for ${game.creator.corporationName} to accept`, tone: 'warn' }, action: 'cancel' };
	}
	if (game.players >= game.maxPlayers) {
		return { ...row, badge: { text: 'Full', tone: null }, action: null };
	}
	return { ...row, badge: null, action: 'ask' };
}

/**
 * What became of the viewer's requests between two loads of the list ([before], [after]): the id of a game whose
 * creator let them in ([accepted], opened at once), and a message for a request that is gone without that.
 */
export function requestOutcome(before, after) {
	const now = new Map(after.map(game => [game.id, game]));
	let accepted = null;
	const messages = [];
	for (const game of before.filter(g => g.standing === 'ASKED')) {
		const later = now.get(game.id);
		if (later?.standing === 'IN') {
			accepted ??= game.id;
		} else if (later?.standing === 'NONE') {
			messages.push(`${game.creator.corporationName} declined your request to join.`);
		} else if (!later) {
			messages.push(`${game.creator.corporationName}'s game is no longer open.`);
		}
	}
	return { accepted, message: messages.join(' ') };
}
