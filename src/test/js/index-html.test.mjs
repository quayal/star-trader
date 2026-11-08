import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, it } from 'node:test';

// index.html's own module script isn't imported by any test, so a syntax error in it would pass every other check
// and leave the page blank (it did once, during E05-S04). Parse it as Node would load it.
describe('index.html', () => {
	it('has an inline module script that parses', () => {
		const html = readFileSync(new URL('../../main/resources/static/index.html', import.meta.url), 'utf8');
		const scripts = [...html.matchAll(/<script type="module">([\s\S]*?)<\/script>/g)].map(match => match[1]);
		assert.ok(scripts.length > 0);
		const dir = mkdtempSync(join(tmpdir(), 'index-html-'));
		try {
			scripts.forEach((script, index) => {
				const file = join(dir, `inline-${index}.mjs`);
				writeFileSync(file, script);
				execFileSync(process.execPath, ['--check', file], { stdio: 'pipe' });
			});
		} finally {
			rmSync(dir, { recursive: true, force: true });
		}
	});
});
