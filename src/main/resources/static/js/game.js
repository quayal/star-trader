/*
 * Pure helpers for the game screens on index.html: Create a game (s11, `#new`), the lobby
 * (s12, `#game/{id}` before the start), the routes of the Deck, System details, Trading, the Game log, Infrastructure and
 * Fleet (s01, s02, s03, s04, s10; their helpers are in board.js, trading.js, log.js, investment.js and fleet.js) and the
 * unknown screen (s08).
 */

export const TURNS_ERROR = 'Enter a whole number of turns from 1 to 50.';
export const MIN_TURNS = 1;
export const MAX_TURNS = 50;
export const MIN_PLAYERS_TO_START = 2;
export const POLL_INTERVAL_MS = 2000;

const PHASE_NAMES = { BIDDING: 'Bidding', INITIATIVE: 'Initiative', NEWS: 'News', JUMPS: 'Jumps', TRADING: 'Trading', OPPORTUNITY: 'Opportunity', INVESTMENT: 'Investment', CONTROL: 'Control' };
const LOCATION_NAMES = { PORT: 'Port', PLANET: 'Planet', SPACE: 'Space', SHIPYARD: 'Shipyard' };

/**
 * Screen routes: '' (or '#') → home; `#new` → create (s11); `#game/{id}` → the lobby or the Deck (s12/s01);
 * `#game/{id}/system/{SYSTEM}` → System details (s02); `#game/{id}/log` → the Game log (s04); `#game/{id}/trading` → Trading
 * (s03); `#game/{id}/infrastructure` → Infrastructure (s10, E06-S08); `#game/{id}/fleet` → Fleet (E07-S03);
 * `#game/{id}/corporations` → Corporations (E11-S01); `#game/{id}/guide` → the Guide (playtest, 2026-11-20); `#game/{id}/game-over` → Game over (s09). Any other fragment is an unknown screen (s08), carrying
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
	const infrastructure = /^#game\/([^/]+)\/infrastructure$/.exec(text);
	if (infrastructure) {
		return { view: 'infrastructure', id: decodeURIComponent(infrastructure[1]) };
	}
	const fleet = /^#game\/([^/]+)\/fleet$/.exec(text);
	if (fleet) {
		return { view: 'fleet', id: decodeURIComponent(fleet[1]) };
	}
	const fleetShip = /^#game\/([^/]+)\/fleet\/([^/]+)$/.exec(text);
	if (fleetShip) {
		return { view: 'fleet', id: decodeURIComponent(fleetShip[1]), ship: decodeURIComponent(fleetShip[2]) };
	}
	const assets = /^#game\/([^/]+)\/assets$/.exec(text);
	if (assets) {
		return { view: 'assets', id: decodeURIComponent(assets[1]) };
	}
	const corporations = /^#game\/([^/]+)\/corporations$/.exec(text);
	if (corporations) {
		return { view: 'corporations', id: decodeURIComponent(corporations[1]) };
	}
	const opportunity = /^#game\/([^/]+)\/opportunity$/.exec(text);
	if (opportunity) {
		return { view: 'opportunity', id: decodeURIComponent(opportunity[1]) };
	}
	const news = /^#game\/([^/]+)\/news$/.exec(text);
	if (news) {
		return { view: 'news', id: decodeURIComponent(news[1]) };
	}
	const guide = /^#game\/([^/]+)\/guide$/.exec(text);
	if (guide) {
		return { view: 'guide', id: decodeURIComponent(guide[1]) };
	}
	const over = /^#game\/([^/]+)\/game-over$/.exec(text);
	if (over) {
		return { view: 'over', id: decodeURIComponent(over[1]) };
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

/** Infrastructure (s10, E06-S08, D12) of game [id]: Investment happens there, apart from buying a ship, which is on Fleet (D18). */
export function infrastructureLink(id) {
	return `#game/${encodeURIComponent(id)}/infrastructure`;
}

/** The Fleet tab (E07-S03) of game [id]: the player's own ships. */
export function fleetLink(id) {
	return `#game/${encodeURIComponent(id)}/fleet`;
}

/** The Fleet tab of game [id] with the card of the player's ship [ship] in view (creator, 2026-11-18). */
export function fleetShipLink(id, ship) {
	return `#game/${encodeURIComponent(id)}/fleet/${encodeURIComponent(ship)}`;
}

/** The Assets tab (creator, 2026-11-18) of game [id]: where the player's money sits. */
export function assetsLink(id) {
	return `#game/${encodeURIComponent(id)}/assets`;
}

/**
 * The Opportunity tab of game [id] (playtest, 2026-11-21): the phase's own screen, the output to collect at the top, then
 * sabotage, agents and the learned opportunities.
 */
export function opportunityLink(id) {
	return `#game/${encodeURIComponent(id)}/opportunity`;
}

/** The News tab of game [id] (R109): the tip-offs in play and this turn's announcements. */
export function newsLink(id) {
	return `#game/${encodeURIComponent(id)}/news`;
}

/** The Guide tab of game [id] (playtest, 2026-11-20): each phase, every agent and the key modifiers. */
export function guideLink(id) {
	return `#game/${encodeURIComponent(id)}/guide`;
}

/** The Corporations tab (E11-S01, D20) of game [id]: every public fact about every player. */
export function corporationsLink(id) {
	return `#game/${encodeURIComponent(id)}/corporations`;
}

/** The hash of a game's Game over screen (s09, E05-S08). */
export function gameOverLink(id) {
	return `#game/${encodeURIComponent(id)}/game-over`;
}

/**
 * D15 (E08-S03): the screen to open because [game] has moved on since [previous], the last state this page drew of it,
 * or null when it hasn't. A new phase or turn is played on 'game' (the Deck: Bidding and Ship-jumps), 'trading',
 * 'opportunity' (its own tab since the 2026-11-21 playtest) or 'infrastructure' (Investment), and a game that has just ended opens 'over'. The names are [parseRoute]'s views. A first
 * load (no previous, or another game's) never counts, so a reload doesn't pull the player away from where they opened.
 */
export function screenForPhaseChange(previous, game) {
	if (!previous || previous.id !== game.id || game.status === 'LOBBY') {
		return null;
	}
	if (previous.status === game.status && previous.turn?.number === game.turn?.number && previous.turn?.phase === game.turn?.phase) {
		return null;
	}
	if (game.status === 'FINISHED') {
		return 'over';
	}
	return { NEWS: 'news', TRADING: 'trading', OPPORTUNITY: 'opportunity', INVESTMENT: 'infrastructure' }[game.turn?.phase] ?? 'game';
}

/**
 * The link a phase change opens, for one of [screenForPhaseChange]'s screens. Every screen it can return has one: News
 * once had none, so the page threw instead of opening it (playtest, 2026-11-21).
 */
export function phaseScreenLink(screen, id) {
	return { game: deckLink, news: newsLink, trading: tradingLink, opportunity: opportunityLink, infrastructure: infrastructureLink, over: gameOverLink }[screen](id);
}

/**
 * E08-S03-4: the id of the Deck heading that takes focus when a phase change opens the Deck: the bid's in Bidding, the
 * turn order's in Initiative and Jumps; null in any other phase.
 */
export function deckHeadingFor(game) {
	if (game.status !== 'RUNNING') {
		return null;
	}
	return { BIDDING: 'bid-title', INITIATIVE: 'turn-order-title', JUMPS: 'turn-order-title' }[game.turn?.phase] ?? null;
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

/** What the lobby shows to the player with [myId]. Corporation names only: ids stay off screen. */
export function lobbyState(game, myId) {
	const isCreator = game.creatorId === myId;
	const enoughPlayers = game.players.length >= MIN_PLAYERS_TO_START;
	const creator = game.players.find(p => p.id === game.creatorId);
	return {
		isCreator,
		corporationNames: game.players.map(p => p.corporationName),
		canStart: isCreator && enoughPlayers,
		startBlockedReason: isCreator && !enoughPlayers ? `At least ${MIN_PLAYERS_TO_START} players are needed to start` : null,
		waitingText: isCreator ? null : `Waiting for ${creator?.corporationName ?? 'the creator'} to start the game`,
	};
}

/** "Turn 4 of 20 · Jumps"; a finished game keeps its last turn and says so instead of a phase. */
export function turnText(game) {
	const phase = game.status === 'FINISHED' ? 'Game over' : PHASE_NAMES[game.turn.phase] ?? game.turn.phase;
	return `Turn ${game.turn.number} of ${game.turns} · ${phase}`;
}

export function shipLine(ship) {
	return `${ship.name} — ${ship.systemName} · ${LOCATION_NAMES[ship.location] ?? ship.location}`;
}
