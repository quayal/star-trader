/*
 * Pure helpers for the game screens on index.html: Create a game (s11, `#new`), the lobby
 * (s12, `#game/{id}` before the start), the routes of the Deck, System details, Trading and the Game log (s01, s02, s03,
 * s04; their helpers are in board.js, trading.js and log.js) and the unknown screen (s08).
 */

export const TURNS_ERROR = 'Enter a whole number of turns from 1 to 50.';
export const MIN_TURNS = 1;
export const MAX_TURNS = 50;
export const MIN_PLAYERS_TO_START = 2;
export const POLL_INTERVAL_MS = 2000;

const PHASE_NAMES = { BIDDING: 'Bidding', SHIP_JUMPS: 'Ship-jumps', TRADING: 'Trading' };
const LOCATION_NAMES = { PORT: 'Port', PLANET: 'Planet', SPACE: 'Space' };

/**
 * Screen routes: '' (or '#') → home; `#new` → create (s11); `#game/{id}` → the lobby or the Deck (s12/s01);
 * `#game/{id}/system/{SYSTEM}` → System details (s02); `#game/{id}/log` → the Game log (s04); `#game/{id}/trading` → Trading
 * (s03). Any other fragment is an unknown screen (s08), carrying
 * the game id when there is one so its "back to the Deck" link stays in that game.
 */
export function parseRoute(hash) {
	const text = hash ?? '';
	if (text === '' || text === '#') {
		return { view: 'home' };
	}
	if (text === '#new') {
		return { view: 'new' };
	}
	const system = /^#game\/([^/]+)\/system\/([A-Z_]+)$/.exec(text);
	if (system) {
		return { view: 'system', id: decodeURIComponent(system[1]), systemId: system[2] };
	}
	const log = /^#game\/([^/]+)\/log$/.exec(text);
	if (log) {
		return { view: 'log', id: decodeURIComponent(log[1]) };
	}
	const trading = /^#game\/([^/]+)\/trading$/.exec(text);
	if (trading) {
		return { view: 'trading', id: decodeURIComponent(trading[1]) };
	}
	const game = /^#game\/([^/]+)$/.exec(text);
	if (game) {
		return { view: 'game', id: decodeURIComponent(game[1]) };
	}
	const inGame = /^#game\/([^/]+)\//.exec(text);
	return inGame ? { view: 'unknown', id: decodeURIComponent(inGame[1]) } : { view: 'unknown' };
}

/** The Deck (s01) of game [id]. */
export function deckLink(id) {
	return `#game/${encodeURIComponent(id)}`;
}

/** System details (s02) of [systemId] in game [id]. */
export function systemLink(id, systemId) {
	return `#game/${encodeURIComponent(id)}/system/${systemId}`;
}

/** The Game log (s04) of game [id]. */
export function logLink(id) {
	return `#game/${encodeURIComponent(id)}/log`;
}

/** Trading (s03) of game [id]. */
export function tradingLink(id) {
	return `#game/${encodeURIComponent(id)}/trading`;
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
