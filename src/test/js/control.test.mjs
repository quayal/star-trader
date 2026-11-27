import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
	BACKING_NO_LABEL,
	BACKING_YES_LABEL,
	ESCAPE_BACKS,
	backingAnsweredText,
	backingFor,
	backingHeading,
	backingQuestion,
	controlText,
	eliminatedText,
	isControlWaiting,
} from '../../main/resources/static/js/control.js';
import { landingOptions } from '../../main/resources/static/js/landing.js';

const players = [
	{ id: 'p1', corporationName: 'Orion Freight', reputation: 5, eliminated: false },
	{ id: 'p2', corporationName: 'Vega Lines', reputation: 20, eliminated: false },
];

function game({ phase = 'CONTROL', controlSubject = 'p1', backingAsk = 'p1', status = 'RUNNING' } = {}) {
	return { status, turn: { number: 3, phase, controlSubject }, players, you: { backingAsk } };
}

describe('the reputation control question (R129)', () => {
	it('asks the viewer to back the corporation under control, and nobody else', () => {
		assert.equal(backingFor(game()).subject.corporationName, 'Orion Freight');
		assert.equal(backingFor(game({ backingAsk: null })), null);
		assert.equal(backingFor({ ...game(), you: null }), null);
	});

	it('words the question for the corporation', () => {
		const backing = backingFor(game());

		assert.equal(backingHeading(backing), 'Orion Freight: reputation control');
		assert.match(backingQuestion(backing), /Orion Freight \(reputation 5\) is under investigation/);
		assert.equal(BACKING_YES_LABEL, 'Yes, back them');
		assert.equal(BACKING_NO_LABEL, 'No, stay out of it');
		assert.equal(ESCAPE_BACKS, false);
		assert.equal(backingAnsweredText('Orion Freight', true), 'You back Orion Freight.');
		assert.equal(backingAnsweredText('Orion Freight', false), 'You stay out of Orion Freight’s control.');
	});

	it('tells everyone what the game waits for', () => {
		assert.equal(isControlWaiting(game()), true);
		assert.equal(controlText(game()), 'reputation control of Orion Freight, waiting for backing');
		assert.equal(controlText(game({ phase: 'INVESTMENT' })), null);
		assert.equal(controlText(game({ controlSubject: null })), null);
	});

	it('marks an eliminated corporation', () => {
		assert.equal(eliminatedText({ eliminated: true }), 'Eliminated from the game');
		assert.equal(eliminatedText({ eliminated: false }), null);
	});
});

describe('the Port refusing a military ship (R56)', () => {
	const system = { name: 'Beta Hydri', portClass: 4, legalSystem: 4, police: 8, security: 10 };

	it('disables the Port choice and says why', () => {
		const port = landingOptions(system, { portRefused: true, landing: null }).find(o => o.value === 'PORT');

		assert.equal(port.disabled, true);
		assert.match(port.label, /refused, military parts or illegal goods aboard \(legal system 4\)/);
	});

	it('leaves the Port open for any other ship', () => {
		const port = landingOptions(system, { portRefused: false, landing: null }).find(o => o.value === 'PORT');

		assert.equal(port.disabled, false);
	});
});
