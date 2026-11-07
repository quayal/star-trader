/*
 * Pure helpers for announcing other players' progress as polls bring it in (E04-S04, D01): new public log lines (picks,
 * jumps, landings, resolutions) and the moment it becomes the viewer's pick, jumps or turn to resolve the next good
 * (E05-S01), or theirs to decide as Dictator (E05-S02). They read `log`, `turn.initiative`, `turn.turnOrder`,
 * `turn.doneJumping`, `turn.tradingQueue` and `turn.resolution` from
 * GET /api/games/{id}; the log is public by definition (S06-3).
 */

import { picker } from './turn-order.js';
import { jumper } from './ship-jumps.js';
import { YOUR_DICTATOR_DECISION, YOUR_RESOLVE, awaitingDictator, resolver } from './trading.js';

export const YOUR_PICK = "It's your turn to choose your place in the turn order.";
export const YOUR_JUMPS = "It's your turn to jump.";
/** Coming back to a busy game announces only the latest few events, not the whole backlog. */
export const MAX_ANNOUNCED_EVENTS = 3;

/**
 * What the page has already shown of [game]: the most log lines (never lowered by a stale poll), and whose pick and
 * jumps it was. [previous] is the last snapshot, or null; one for another game is ignored.
 */
export function progressSnapshot(game, previous) {
	const known = previous?.gameId === game.id ? previous : null;
	return {
		gameId: game.id,
		logLength: Math.max(known?.logLength ?? 0, game.log.length),
		picker: picker(game),
		jumper: jumper(game),
		resolver: resolver(game),
		dictator: awaitingDictator(game),
	};
}

/**
 * What to announce when a poll brings [game] after the page showed [previous] (a snapshot of the same game): the new
 * log lines, at most the last three, then a cue when it has just become [email]'s pick or jumps. "" when nothing is new,
 * on the game's first load, or for another game's snapshot.
 */
export function progressText(previous, game, email) {
	if (!previous || previous.gameId !== game.id) {
		return '';
	}
	const events = game.log.slice(previous.logLength).slice(-MAX_ANNOUNCED_EVENTS).map(entry => `${entry.text}.`);
	const cues = [];
	if (picker(game) === email && previous.picker !== email) {
		cues.push(YOUR_PICK);
	}
	if (jumper(game) === email && previous.jumper !== email) {
		cues.push(YOUR_JUMPS);
	}
	if (resolver(game) === email && previous.resolver !== email) {
		cues.push(YOUR_RESOLVE); // E05-S01: Trading has started and you're first in initiative
	}
	if (awaitingDictator(game) === email && previous.dictator !== email) {
		cues.push(YOUR_DICTATOR_DECISION); // E05-S02: a good you're Dictator of has started resolving
	}
	return [...events, ...cues].join(' ');
}
