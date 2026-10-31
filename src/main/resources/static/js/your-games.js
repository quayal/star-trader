/*
 * "Your games" (E02-S05 follow-up): the games this browser has opened as a member, so a player who
 * leaves a lobby can get back to it. Kept in localStorage next to the session token, most recent
 * first. The list is per browser, not per player: the screen re-checks each game with the server,
 * and a game that answers 403 or 404 is forgotten.
 */

import { lengthText } from './game.js';

export const GAMES_KEY = 'starTrader.games';
export const MAX_REMEMBERED_GAMES = 10;

const STATUS_NAMES = { LOBBY: 'In the lobby', RUNNING: 'Running', FINISHED: 'Finished' };

function defaultStorage() {
	try {
		return globalThis.localStorage ?? null;
	} catch {
		return null;
	}
}

/** The remembered game ids, most recent first; empty when storage is blocked or holds something else. */
export function rememberedGames(storage = defaultStorage()) {
	try {
		const ids = JSON.parse(storage?.getItem(GAMES_KEY) ?? '[]');
		return Array.isArray(ids) ? ids.filter(id => typeof id === 'string') : [];
	} catch {
		return [];
	}
}

function save(ids, storage) {
	try {
		storage?.setItem(GAMES_KEY, JSON.stringify(ids));
	} catch {
		// Storage blocked: the list just isn't kept.
	}
}

export function rememberGame(id, storage = defaultStorage()) {
	save([id, ...rememberedGames(storage).filter(other => other !== id)].slice(0, MAX_REMEMBERED_GAMES), storage);
}

export function forgetGame(id, storage = defaultStorage()) {
	save(rememberedGames(storage).filter(other => other !== id), storage);
}

export function latestGame(storage = defaultStorage()) {
	return rememberedGames(storage)[0] ?? null;
}

/** One "Your games" row. Plain text only: the page puts it in with textContent. */
export function gameRow(game) {
	return {
		href: `/#game/${encodeURIComponent(game.id)}`,
		status: STATUS_NAMES[game.status] ?? game.status,
		details: `${lengthText(game.turns)} · ${game.players.map(p => p.corporationName).join(', ')}`,
	};
}
