import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { bonusFrom, crewBonusText, crewLabel, guideSections, jumpModifierLines } from '../../main/resources/static/js/guide.js';

const rules = {
	phases: ['Bidding', 'Jumps', 'Trading', 'Opportunity', 'Investment'],
	agents: [{ name: 'Novak', effect: '+4 to the jump result of the ship he travels on', inGame: true }, { name: 'Badger', effect: '−3 to security', inGame: false }],
	crewClasses: [
		{ id: 'A', pricePerCrew: 20, jumpModifier: 5, interceptModifier: 1 },
		{ id: 'D', pricePerCrew: 2, jumpModifier: 0, interceptModifier: -1 },
	],
	safeJumpBonus: 2,
	novakJumpBonus: 4,
	battleCommInterceptBonus: 1,
	triggerInterceptBonus: 1,
	wolfInitiativeBonus: 4,
	shortySabotageBonus: 4,
	sabotage: [{ from: null, effect: 0, reputationLoss: 0 }, { from: 7, effect: 0, reputationLoss: 10 }, { from: 21, effect: 10, reputationLoss: null }],
	sabotageBlame: 8,
	reputation: { start: 20, max: 40, recovery: 3, recoveryUpTo: 17, buyCost: 20, bonus: { 25: 5, 27: 10 } },
	hulls: [
		{ name: 'Clarinet', price: 160, moduleCapacity: 5, crew: 1, cargoSpaces: 0, weapons: 3, shields: 2, intercept: 2, repairCost: 20, military: false, aerodynamic: false },
		{ name: 'Dagger', price: 250, moduleCapacity: 1, crew: 1, cargoSpaces: 0, weapons: 5, shields: 4, intercept: 2, repairCost: 50, military: true, aerodynamic: true },
	],
	modules: [
		{ name: 'Safe jump', price: 10, crew: 0, weapons: 0, rocketWeapons: 0, cargoSpaces: 0, passengers: 0, military: false, takesModuleCapacity: false, jumpModifier: 2 },
		{ name: 'Arsenal', price: 100, crew: 1, weapons: 7, rocketWeapons: 14, cargoSpaces: 0, passengers: 0, military: true, takesModuleCapacity: true, jumpModifier: 0 },
	],
};

describe('the Guide (playtest, 2026-11-20)', () => {
	it('lists each phase with what a player does in it, and every agent, those not in the game yet marked', () => {
		const guide = guideSections(rules);
		assert.equal(guide.phases.length, 5);
		assert.match(guide.phases[3].text, /collect/);
		assert.deepEqual(guide.agents[1], { name: 'Badger', effect: '−3 to security', note: 'Not in the game yet' });
	});

	it('reads the modifiers and tables from the rules', () => {
		const modifiers = guideSections(rules).modifiers;
		assert.deepEqual(modifiers[0].lines, ['Crew A +5 · D +0', 'Safe jump module +2', 'Novak aboard +4']);
		assert.deepEqual(modifiers[1].lines[0], 'Crew A +1 · D −1');
		assert.deepEqual(modifiers[3].lines.slice(0, 3), [
			'below 7: effect 0; you lose 0 reputation',
			'7–20: effect 0; you lose 10 reputation',
			'21+: effect 10; the player you blame loses 8',
		]);
		assert.equal(bonusFrom(rules), 25);
	});

	it('shows nothing before the rules have loaded', () => {
		assert.equal(guideSections(null), null);
		assert.deepEqual(jumpModifierLines(null), []);
		assert.equal(bonusFrom(null), undefined);
	});

	it('labels a crew class with its price and bonuses', () => {
		assert.equal(crewLabel(rules.crewClasses[0]), 'A · 20 HT per crew · jump +5 · intercept +1');
		assert.equal(crewLabel({ id: 'B', pricePerCrew: 10, jumpModifier: 3, interceptModifier: 0 }), 'B · 10 HT per crew · jump +3');
		assert.deepEqual(jumpModifierLines(rules), ['Crew A +5 · D +0', 'Safe jump +2', 'Novak +4']);
		assert.deepEqual(jumpModifierLines(rules, { jumpModifier: -4 }).at(-1), 'This turn −4 (news)');
		assert.equal(crewBonusText(rules.crewClasses[1]), 'jump +0 · intercept −1');
	});

	it('tabulates every hull and module, so a ship can be planned outside Investment (playtest, 2026-11-21)', () => {
		const { hulls, modules } = guideSections(rules).ships;
		assert.deepEqual(hulls.columns, ['Hull', 'Price', 'Modules', 'Crew', 'Cargo', 'Weapons', 'Shields', 'Intercept', 'Repair', 'Notes']);
		assert.deepEqual(hulls.rows[0], ['Clarinet', '160 HT', '5', '1', '0', '3', '2', '2', '20 HT', '']);
		assert.deepEqual(hulls.rows[1].at(-1), 'Military (opportunity only) · aerodynamic');
		assert.deepEqual(modules.columns, ['Module', 'Price', 'Crew', 'Weapons (rockets)', 'Cargo', 'Passengers', 'Notes']);
		assert.deepEqual(modules.rows[0], ['Safe jump', '10 HT', '0', '0', '0', '0', 'Takes no module space · jump +2']);
		assert.deepEqual(modules.rows[1], ['Arsenal', '100 HT', '1', '7 (14)', '0', '0', 'Military (opportunity only)']);
		assert.match(modules.note, /crew at the ship's class: A 20 HT · D 2 HT/);
	});
});
