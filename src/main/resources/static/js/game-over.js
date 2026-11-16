/*
 * Pure helpers for Game over (s09, E05-S08): every player's final money, most first, and the winners. They read
 * `status` and `result` ({ standings: [{ playerId, corporationName, money }], winners: [playerId] }) from GET /api/games/{id};
 * the server sends `result` only once the game is over, the one point where money isn't private (R39, R29).
 */

export const GAME_OVER_TITLE = 'Game over';
export const NOT_OVER_TEXT = "This game isn't over yet. Everyone's final money and the winner appear here after the last turn.";
export const CASH_ONLY_TEXT = 'Only cash counts: ships and goods are worth nothing. Money was private until now.';

/** Whether [game] is over (R39): its last turn's Trading has resolved its last good. */
export function isOver(game) {
	return game.status === 'FINISHED';
}

/** "2,310 HT": money with thousands separators, as the log writes it (S08-4). */
export function moneyText(amount) {
	return `${amount.toLocaleString('en-US')} HT`;
}

/** "A", "A and B", "A, B and C". */
function namesText(names) {
	return names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} and ${names.at(-1)}`;
}

/**
 * S08-2, S08-4: "Vega Lines wins with 2,310 HT", or for a shared win "Vega Lines and Kessler Cargo win with 2,310 HT";
 * "" before the game is over.
 */
export function winnerText(game) {
	if (!isOver(game) || !game.result) {
		return '';
	}
	const { standings, winners } = game.result;
	const won = standings.filter(s => winners.includes(s.id));
	if (won.length === 0) {
		return '';
	}
	return `${namesText(won.map(s => s.corporationName))} ${won.length === 1 ? 'wins' : 'win'} with ${moneyText(won[0].money)}`;
}

/**
 * S08-2, S08-3: the final standings as s09 lists them, most money first: place (players tied on money share it),
 * corporation, money, and whether they won and are the viewer [playerId]. Empty before the game is over.
 */
export function standingRows(game, playerId) {
	if (!isOver(game) || !game.result) {
		return [];
	}
	const { standings, winners } = game.result;
	return standings.map(standing => ({
		place: standings.findIndex(s => s.money === standing.money) + 1,
		corporation: standing.corporationName,
		money: moneyText(standing.money),
		winner: winners.includes(standing.id),
		you: standing.id === playerId,
	}));
}
