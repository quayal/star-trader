/*
 * The signed-in player's session (E01-S02, E01-S03).
 * The token is kept in localStorage so a reload stays signed in; every game screen calls
 * requireSession() before loading, and a player the server doesn't know is sent to the
 * sign-in screen (s10) with a link back.
 */

export const TOKEN_KEY = 'starTrader.sessionToken';
export const SIGN_IN_PATH = '/sign-in.html';
/** Where a player goes after signing in when they didn't come from a game link: "Create a game" (s11, E02-S05). */
export const DEFAULT_NEXT = '/#new';

function defaultStorage() {
	try {
		return globalThis.localStorage ?? null;
	} catch {
		return null;
	}
}

export function getToken(storage = defaultStorage()) {
	try {
		return storage?.getItem(TOKEN_KEY) || null;
	} catch {
		return null;
	}
}

export function saveToken(token, storage = defaultStorage()) {
	try {
		storage?.setItem(TOKEN_KEY, token);
	} catch {
		// Storage blocked: the player stays signed in until the page is left.
	}
}

export function clearToken(storage = defaultStorage()) {
	try {
		storage?.removeItem(TOKEN_KEY);
	} catch {
		// Nothing stored that could be cleared.
	}
}

/**
 * The link to return to after signing in, or DEFAULT_NEXT. Only a path on this site is
 * accepted, so the sign-in screen can't send a player to another site.
 */
export function safeNext(raw) {
	if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) {
		return DEFAULT_NEXT;
	}
	if (raw === SIGN_IN_PATH || raw.startsWith(`${SIGN_IN_PATH}?`) || raw.startsWith(`${SIGN_IN_PATH}#`)) {
		return DEFAULT_NEXT;
	}
	return raw;
}

export function signInUrl(next) {
	return `${SIGN_IN_PATH}?next=${encodeURIComponent(next)}`;
}

/** The current session ({ playerName, corporationName, email }), or null when the server doesn't know the token. */
export async function fetchSession({ storage = defaultStorage(), fetchFn = globalThis.fetch } = {}) {
	const token = getToken(storage);
	if (!token) {
		return null;
	}
	const response = await fetchFn('/api/login/session', { headers: { Authorization: `Bearer ${token}` } });
	if (response.status === 401) {
		clearToken(storage);
		return null;
	}
	if (!response.ok) {
		throw new Error(`Session check failed with status ${response.status}`);
	}
	return response.json();
}

/**
 * For game screens: resolves with the session, or sends a signed-out player to s10 with
 * the current link kept and resolves with null.
 */
export async function requireSession({
	storage = defaultStorage(),
	fetchFn = globalThis.fetch,
	location = globalThis.location,
} = {}) {
	const session = await fetchSession({ storage, fetchFn });
	if (!session) {
		location.replace(signInUrl(location.pathname + location.search + location.hash));
	}
	return session;
}
