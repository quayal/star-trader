import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { RAIL_PHASES, phaseRail, railItemLabel } from '../../main/resources/static/js/phase-rail.js';

const at = (phase, status = 'RUNNING') => ({ status, turn: { number: 3, phase } });

describe('the phase rail (D34)', () => {
	it('lists the turn in R44 order', () => {
		assert.deepEqual(RAIL_PHASES.map(item => item.name), ['Bidding', 'Initiative', 'News', 'Jumps', 'Trading', 'Opportunity', 'Investment', 'Control']);
	});

	it('marks the phases played, the one running and the ones to come', () => {
		const rail = phaseRail(at('JUMPS'));
		assert.deepEqual(rail.map(item => item.state), ['done', 'done', 'done', 'now', 'later', 'later', 'later', 'later']);
		assert.equal(railItemLabel(rail[3]), 'Jumps, now');
	});

	it('links each phase with a screen, and Control to none', () => {
		const screens = Object.fromEntries(phaseRail(at('BIDDING')).map(item => [item.phase, item.screen]));
		assert.deepEqual(screens, {
			BIDDING: 'market', INITIATIVE: 'game', NEWS: 'news', JUMPS: 'jumps', TRADING: 'trading', OPPORTUNITY: 'opportunity', INVESTMENT: 'infrastructure', CONTROL: null,
		});
	});

	it('says which phase screen the player is on', () => {
		const rail = phaseRail(at('OPPORTUNITY'), 'trading');
		assert.deepEqual(rail.filter(item => item.current).map(item => item.phase), ['TRADING']);
		assert.equal(phaseRail(at('INITIATIVE'), 'game').some(item => item.current), false);
	});

	it('is empty outside a running game', () => {
		assert.deepEqual(phaseRail({ status: 'LOBBY', turn: null }), []);
		assert.deepEqual(phaseRail(at('CONTROL', 'FINISHED')), []);
	});
});
