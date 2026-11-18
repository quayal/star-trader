/*
 * Pure helpers for "Your trades" on the Deck (creator, 2026-11-18): the signed-in player's trades, newest first. Each
 * trade's log entry carries it as `log[].trade: { playerId, system, systemName, goods, type, units, price }`, public like
 * the line itself (R18); entries logged before that carry none, so the history starts there.
 */

export const NO_TRADES_TEXT = 'No trades yet.';
/** How many trades the Deck shows before "Show all". */
export const SHORT_LIST = 8;

/**
 * [myId]'s trades in [log] (oldest first, as the poll keeps it), newest first:
 * `{ turn: 'T3', verb: 'Bought', tone: 'buy', text: '4 FOOD at Tau Ceti, 12 HT each (48 HT)', system }`.
 */
export function yourTrades(log, myId) {
	return (log ?? [])
		.filter(entry => entry.trade?.playerId === myId)
		.map(({ turn, trade }) => ({
			turn: `T${turn}`,
			verb: trade.type === 'SELL' ? 'Sold' : 'Bought',
			tone: trade.type === 'SELL' ? 'sell' : 'buy',
			text: `${trade.units} ${trade.goods} at ${trade.systemName}, ${trade.price} HT each (${trade.units * trade.price} HT)`,
			system: trade.system,
		}))
		.reverse();
}

/** The trades to show: all when [expanded], else the newest [SHORT_LIST]; [more] is how many are left out. */
export function shownTrades(trades, expanded) {
	const shown = expanded ? trades : trades.slice(0, SHORT_LIST);
	return { shown, more: trades.length - shown.length };
}
