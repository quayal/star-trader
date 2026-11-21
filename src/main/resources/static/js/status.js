/*
 * The status strip in the top bar (playtest, 2026-11-21: "readily available to the player at all times"): the viewer's
 * money (private, R29, from `you`), and their reputation with what it does and connections, both public, from their own
 * entry in `players` (R85–R88, R81). Nothing here is calculated that the server decides.
 */

/** The strip's items, `[{ label, value, note }]`, or empty before the viewer's data has loaded. [bonusFrom] as in reputationNote. */
export function statusItems(game, bonusFrom) {
	const me = game.players?.find(player => player.id === game.you?.id);
	if (!me) {
		return [];
	}
	const connections = me.connections ?? {};
	return [
		{ label: 'Money', value: `${game.you.money ?? 0} HT`, note: '' },
		{ label: 'Reputation', value: `${me.reputation ?? 20}`, note: reputationEffect(me, bonusFrom) },
		{ label: 'Connections', value: `POL ${connections.political ?? 0} · BUSS ${connections.business ?? 0} · CRIM ${connections.criminal ?? 0}`, note: '' },
	];
}

/** "+3 next turn · +15 HT bonus" (R86, R88), from the server's `reputationRecovery` and `reputationBonus`. */
export function reputationEffect(player, bonusFrom) {
	const recovery = player.reputationRecovery > 0 ? `+${player.reputationRecovery} next turn` : '';
	const bonus = player.reputationBonus > 0 ? `+${player.reputationBonus} HT bonus` : bonusFrom ? `bonus from ${bonusFrom}` : '';
	return [recovery, bonus].filter(Boolean).join(' · ');
}
