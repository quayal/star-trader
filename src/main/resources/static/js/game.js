/*
 * Pure helpers for the game screens on index.html: Create a game (s11, `#new`), the lobby
 * (s12, `#game/{id}` before the start) and the game view after it (E02-S05).
 */

export const TURNS_ERROR = 'Enter a whole number of turns from 1 to 50.';
export const MIN_TURNS = 1;
export const MAX_TURNS = 50;
export const MIN_PLAYERS_TO_START = 2;
export const POLL_INTERVAL_MS = 2000;

const PHASE_NAMES = { BIDDING: 'Bidding', SHIP_JUMPS: 'Ship-jumps', TRADING: 'Trading' };
const LOCATION_NAMES = { PORT: 'Port', PLANET: 'Planet', SPACE: 'Space' };

/** `#new` → create; `#game/{id}` → that game; anything else → the home view. */
export function parseRoute(hash) {
	if (hash === '#new') {
		return { view: 'new' };
	}
	const match = /^#game\/(.+)$/.exec(hash ?? '');
	if (match) {
		return { view: 'game', id: decodeURIComponent(match[1]) };
	}
	return { view: 'home' };
}

/** The length as a number, or the error the server would give (GameCreationService). */
export function validateTurns(raw) {
	const text = String(raw ?? '').trim();
	if (!/^\d+$/.test(text)) {
		return { error: TURNS_ERROR };
	}
	const turns = Number(text);
	return turns >= MIN_TURNS && turns <= MAX_TURNS ? { turns } : { error: TURNS_ERROR };
}

export function lengthText(turns) {
	return `${turns} ${turns === 1 ? 'turn' : 'turns'}`;
}

export function gameLink(origin, id) {
	return `${origin}/#game/${encodeURIComponent(id)}`;
}

/** What the lobby shows to the player with [myEmail]. Corporation names only: emails stay off screen. */
export function lobbyState(game, myEmail) {
	const isCreator = game.creatorEmail === myEmail;
	const enoughPlayers = game.players.length >= MIN_PLAYERS_TO_START;
	const creator = game.players.find(p => p.email === game.creatorEmail);
	return {
		isCreator,
		corporationNames: game.players.map(p => p.corporationName),
		canStart: isCreator && enoughPlayers,
		startBlockedReason: isCreator && !enoughPlayers ? `At least ${MIN_PLAYERS_TO_START} players are needed to start` : null,
		waitingText: isCreator ? null : `Waiting for ${creator?.corporationName ?? 'the creator'} to start the game`,
	};
}

/** "Turn 4 of 20 · Ship-jumps"; a finished game keeps its last turn and says so instead of a phase. */
export function turnText(game) {
	const phase = game.status === 'FINISHED' ? 'Game over' : PHASE_NAMES[game.turn.phase] ?? game.turn.phase;
	return `Turn ${game.turn.number} of ${game.turns} · ${phase}`;
}

export function shipLine(ship) {
	return `${ship.name} — ${ship.systemName} · ${LOCATION_NAMES[ship.location] ?? ship.location}`;
}
