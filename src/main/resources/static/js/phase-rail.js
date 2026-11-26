/*
 * Pure helpers for the phase rail (D34, `.ai/specs/implemented/2026-11-26-jumps-screen-and-phase-rail.md`): the turn's phases in R44 order in
 * the top bar of every game screen, done / now / later, each phase with a screen a link to it. They read `status` and
 * `turn.phase` from GET /api/games/{id}; everything here is public.
 */

/** R44's order, with each phase's screen as [parseRoute]'s view name, or null for a phase without one (Control). */
export const RAIL_PHASES = [
	{ phase: 'BIDDING', name: 'Bidding', screen: 'market' },
	{ phase: 'INITIATIVE', name: 'Initiative', screen: 'game' },
	{ phase: 'NEWS', name: 'News', screen: 'news' },
	{ phase: 'JUMPS', name: 'Jumps', screen: 'jumps' },
	{ phase: 'TRADING', name: 'Trading', screen: 'trading' },
	{ phase: 'OPPORTUNITY', name: 'Opportunity', screen: 'opportunity' },
	{ phase: 'INVESTMENT', name: 'Investment', screen: 'infrastructure' },
	{ phase: 'CONTROL', name: 'Control', screen: null },
];

/**
 * The rail for [game]: one item per phase, `{ phase, name, screen, state }` with state 'done', 'now' or 'later', and
 * [current] true on the item whose screen is [view] (the screen the player is on). Empty unless the game is running.
 */
export function phaseRail(game, view = null) {
	if (game.status !== 'RUNNING' || !game.turn) {
		return [];
	}
	const now = RAIL_PHASES.findIndex(item => item.phase === game.turn.phase);
	return RAIL_PHASES.map((item, index) => ({
		...item,
		state: index < now ? 'done' : index === now ? 'now' : 'later',
		current: item.screen !== null && item.screen === view && item.screen !== 'game',
	}));
}

/** The accessible name of a rail item: "Jumps, now", "Bidding, done", "Control, later". */
export function railItemLabel(item) {
	return `${item.name}, ${item.state}`;
}
