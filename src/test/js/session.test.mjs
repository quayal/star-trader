import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	DEFAULT_NEXT,
	TOKEN_KEY,
	clearToken,
	fetchSession,
	getToken,
	requireSession,
	safeNext,
	saveToken,
	signInUrl,
} from '../../main/resources/static/js/session.js';

function fakeStorage(entries = {}) {
	const map = new Map(Object.entries(entries));
	return {
		getItem: (key) => (map.has(key) ? map.get(key) : null),
		setItem: (key, value) => map.set(key, String(value)),
		removeItem: (key) => map.delete(key),
	};
}

function throwingStorage() {
	const fail = () => {
		throw new Error('SecurityError');
	};
	return { getItem: fail, setItem: fail, removeItem: fail };
}

function fakeFetch(status, body = {}) {
	const calls = [];
	const fn = async (url, init) => {
		calls.push({ url, init });
		return { status, ok: status >= 200 && status < 300, json: async () => body };
	};
	fn.calls = calls;
	return fn;
}

function fakeLocation(pathname, search = '', hash = '') {
	return { pathname, search, hash, replacedWith: null, replace(url) { this.replacedWith = url; } };
}

describe('token storage', () => {
	it('keeps the token so a reload stays signed in', () => {
		const storage = fakeStorage();
		saveToken('abc', storage);
		assert.equal(getToken(storage), 'abc');
		clearToken(storage);
		assert.equal(getToken(storage), null);
	});

	it('treats blocked storage as signed out instead of throwing', () => {
		const storage = throwingStorage();
		assert.doesNotThrow(() => saveToken('abc', storage));
		assert.doesNotThrow(() => clearToken(storage));
		assert.equal(getToken(storage), null);
	});
});

describe('safeNext', () => {
	it('keeps a path on this site, with its query and hash', () => {
		assert.equal(safeNext('/trade-offers.html?x=1#y'), '/trade-offers.html?x=1#y');
		assert.equal(safeNext('/#game/42'), '/#game/42');
	});

	it('falls back to Create a game when there is no link', () => {
		assert.equal(safeNext(null), DEFAULT_NEXT);
		assert.equal(safeNext(''), DEFAULT_NEXT);
	});

	for (const link of ['https://evil.example/', '//evil.example/', '/\\evil.example', 'javascript:alert(1)', 'trade-offers.html']) {
		it(`rejects ${link}, which could leave the site`, () => {
			assert.equal(safeNext(link), DEFAULT_NEXT);
		});
	}

	it('does not return to the sign-in screen itself', () => {
		assert.equal(safeNext('/sign-in.html'), DEFAULT_NEXT);
		assert.equal(safeNext('/sign-in.html?next=%2F'), DEFAULT_NEXT);
	});
});

describe('signInUrl', () => {
	it('keeps the link to return to', () => {
		assert.equal(signInUrl('/trade-offers.html?a=1#b'), '/sign-in.html?next=%2Ftrade-offers.html%3Fa%3D1%23b');
	});
});

describe('fetchSession', () => {
	it('sends the token as a bearer header and returns the session', async () => {
		const fetchFn = fakeFetch(200, { playerName: 'Ada' });
		const session = await fetchSession({ storage: fakeStorage({ [TOKEN_KEY]: 't1' }), fetchFn });
		assert.deepEqual(session, { playerName: 'Ada' });
		assert.equal(fetchFn.calls[0].url, '/api/login/session');
		assert.equal(fetchFn.calls[0].init.headers.Authorization, 'Bearer t1');
	});

	it('does not call the server without a token', async () => {
		const fetchFn = fakeFetch(200);
		assert.equal(await fetchSession({ storage: fakeStorage(), fetchFn }), null);
		assert.equal(fetchFn.calls.length, 0);
	});

	it('forgets a token the server no longer knows', async () => {
		const storage = fakeStorage({ [TOKEN_KEY]: 'stale' });
		assert.equal(await fetchSession({ storage, fetchFn: fakeFetch(401) }), null);
		assert.equal(getToken(storage), null);
	});

	it('keeps the token when the server fails for another reason', async () => {
		const storage = fakeStorage({ [TOKEN_KEY]: 't1' });
		await assert.rejects(fetchSession({ storage, fetchFn: fakeFetch(500) }));
		assert.equal(getToken(storage), 't1');
	});
});

describe('requireSession', () => {
	it('lets a signed-in player stay on the page', async () => {
		const location = fakeLocation('/trade-offers.html');
		const session = await requireSession({
			storage: fakeStorage({ [TOKEN_KEY]: 't1' }),
			fetchFn: fakeFetch(200, { playerName: 'Ada' }),
			location,
		});
		assert.deepEqual(session, { playerName: 'Ada' });
		assert.equal(location.replacedWith, null);
	});

	it('sends a signed-out player to sign in with the link kept', async () => {
		const location = fakeLocation('/trade-offers.html', '?x=1', '#y');
		const session = await requireSession({ storage: fakeStorage(), fetchFn: fakeFetch(200), location });
		assert.equal(session, null);
		assert.equal(location.replacedWith, signInUrl('/trade-offers.html?x=1#y'));
	});

	it('sends a player whose session was lost to sign in with the link kept', async () => {
		const location = fakeLocation('/');
		await requireSession({ storage: fakeStorage({ [TOKEN_KEY]: 'stale' }), fetchFn: fakeFetch(401), location });
		assert.equal(location.replacedWith, signInUrl('/'));
	});
});
