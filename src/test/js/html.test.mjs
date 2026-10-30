import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { escapeHtml } from '../../main/resources/static/js/html.js';

describe('escapeHtml', () => {
	it('turns markup a player typed into plain text', () => {
		assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;');
	});

	it("escapes ampersands first and single quotes too", () => {
		assert.equal(escapeHtml("Tom & Jerry's &lt;"), 'Tom &amp; Jerry&#39;s &amp;lt;');
	});

	it('leaves ordinary names and numbers as they are', () => {
		assert.equal(escapeHtml('Ada Nova'), 'Ada Nova');
		assert.equal(escapeHtml(17), '17');
	});
});
