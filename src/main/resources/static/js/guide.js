/*
 * The Guide tab (playtest, 2026-11-20): what a player can do in each phase, every agent and the modifiers that matter. Every
 * number comes from `GET /api/rules`, the server's own tables, so the Guide can't drift from the game; only the one-line
 * phase descriptions are written here.
 */

/** What a player does in each phase, by the phase's name as the server sends it. */
const PHASE_TEXT = {
	Bidding: 'Place trade offers (one per good per system) and a secret initiative bid. In turn 1, place your connection points first.',
	Initiative: 'Everyone’s bid + 2d6 sets the initiative order; then, in that order, each player picks their place in the turn order.',
	News: 'Tip-offs that are due take place or expire, and d3 new ones are drawn. Pay 5 HT to learn one your connections reach.',
	Jumps: 'Jump each ship once, in turn order. Ships arriving in Space can be intercepted; then choose Port, Planet or Space.',
	Trading: 'Goods resolve one at a time, in the order the first player sets. A winning offer trades; Wholesalers and the Dictator may trade at the new price.',
	Opportunity: 'In turn order: collect your factories’ output, then sabotage, recruit an agent or offer one for sale. Agents move at any time.',
	Investment: 'Everyone at once: buy warehouses, factories and ships; refit, repair or sell; raise a connection, buy reputation, take or repay a loan.',
};

const signed = n => (n > 0 ? `+${n}` : n < 0 ? `−${-n}` : '+0');

/**
 * The Guide's sections from [rules] (`/api/rules`), or null until they've loaded: `{ phases: [{ name, text }], agents:
 * [{ name, effect, note }], modifiers: [{ title, lines }] }`.
 */
export function guideSections(rules) {
	if (!rules) {
		return null;
	}
	const crew = rules.crewClasses ?? [];
	const reputation = rules.reputation;
	const bonus = Object.entries(reputation.bonus).map(([level, ht]) => `${level}: ${ht} HT`).join(', ');
	return {
		phases: rules.phases.map(name => ({ name, text: PHASE_TEXT[name] ?? '' })),
		agents: rules.agents.map(agent => ({ name: agent.name, effect: agent.effect, note: agent.inGame ? '' : 'Not in the game yet' })),
		modifiers: [
			{
				title: 'Jumps (added to the target; roll 2d6 at or under it)',
				lines: [
					`Crew ${crew.map(c => `${c.id} ${signed(c.jumpModifier)}`).join(' · ')}`,
					`Safe jump module ${signed(rules.safeJumpBonus)}`,
					`Novak aboard ${signed(rules.novakJumpBonus)}`,
				],
			},
			{
				title: 'Interception',
				lines: [
					`Crew ${crew.map(c => `${c.id} ${signed(c.interceptModifier)}`).join(' · ')}`,
					`Working Battle comm ${signed(rules.battleCommInterceptBonus)}`,
					`Trigger aboard ${signed(rules.triggerInterceptBonus)}`,
				],
			},
			{ title: 'Initiative', lines: [`Bid + 2d6, Wolf ${signed(rules.wolfInitiativeBonus)} (shown in the roll, which reveals him)`] },
			{
				title: 'Sabotage (2d6 + criminal connections, read on the table)',
				lines: [
					...rules.sabotage.map((row, index) => {
						const next = rules.sabotage[index + 1]?.from;
						const range = row.from === null ? `below ${next}` : next ? `${row.from}–${next - 1}` : `${row.from}+`;
						const loss = row.reputationLoss === null ? `the player you blame loses ${rules.sabotageBlame}` : `you lose ${row.reputationLoss} reputation`;
						return `${range}: effect ${row.effect}; ${loss}`;
					}),
					`Shorty at the target's system ${signed(rules.shortySabotageBonus)} on both rolls`,
				],
			},
			{
				title: 'Reputation',
				lines: [
					`Starts at ${reputation.start}, from 0 to ${reputation.max}.`,
					`End of each turn: ${signed(reputation.recovery)} from 1 to ${reputation.recoveryUpTo}.`,
					`Buy 1d6 for ${reputation.buyCost} HT once per Investment.`,
					`Bonus at the end of each turn: ${bonus}.`,
				],
			},
		],
	};
}

/** The lowest reputation that pays a bonus (R88), from [rules], or undefined before they've loaded. */
export function bonusFrom(rules) {
	const levels = Object.keys(rules?.reputation?.bonus ?? {}).map(Number);
	return levels.length ? Math.min(...levels) : undefined;
}

/**
 * The jump modifiers listed in the middle of the Deck's map (playtest, 2026-11-20): `Crew A +5 · B +3 · C +1 · D +0`, the
 * Safe jump module and Novak. Empty until the rules have loaded.
 */
export function jumpModifierLines(rules) {
	if (!rules) {
		return [];
	}
	return [
		`Crew ${rules.crewClasses.map(c => `${c.id} ${signed(c.jumpModifier)}`).join(' · ')}`,
		`Safe jump ${signed(rules.safeJumpBonus)}`,
		`Novak ${signed(rules.novakJumpBonus)}`,
	];
}

/** What a crew class adds (R68, R71): "jump +5 · intercept +1", or just "jump +3" when it doesn't change interception. */
export function crewBonusText(crewClass) {
	const intercept = crewClass.interceptModifier ? ` · intercept ${signed(crewClass.interceptModifier)}` : '';
	return `jump ${signed(crewClass.jumpModifier)}${intercept}`;
}

/** A crew class's label where one is chosen (playtest, 2026-11-20): "A · 20 HT per crew · jump +5 · intercept +1". */
export function crewLabel(crewClass) {
	return `${crewClass.id} · ${crewClass.pricePerCrew} HT per crew · ${crewBonusText(crewClass)}`;
}
