/*
 * Pure helpers for placing secret trade offers during Bidding (E03-S03) on the Deck (s01) and System details
 * (s02). They read the game shape from GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6): the caller's own offers
 * are in `you.offers`, and nobody else's are ever sent (R17).
 */

export const MIN_PRICE = 1;
export const MAX_PRICE = 20;

export const PRICE_EMPTY = 'Enter a price.';
export const PRICE_ERROR = 'Price must be a whole number from 1 to 20.';
export const ALL_OFFERED = 'You have an offer on every good there this turn.';
export const NO_SHIP_WARNING = 'You have no ship here. If a buy wins, the goods are lost; a sell has nothing to draw from. '
	+ 'A ship can still jump here before Trading.';
/** S03-7 (proposed wording): what a player sees when their offer reached the server after Bidding closed. */
export const CLOSED_TEXT = "Bidding has closed — this offer wasn't placed.";
/** The server's A04 message (BACKWARD_COMPATIBILITY.md §6). */
const SERVER_CLOSED = 'Bidding has closed';

const TYPE_NAMES = { BUY: 'Buy', SELL: 'Sell' };

/** R03: a whole number of HT from 1 to 20, or the error to show next to the field. */
export function validatePrice(raw) {
	const text = String(raw ?? '').trim();
	if (text === '') {
		return { error: PRICE_EMPTY };
	}
	if (!/^\d+$/.test(text)) {
		return { error: PRICE_ERROR };
	}
	const price = Number(text);
	return price >= MIN_PRICE && price <= MAX_PRICE ? { price } : { error: PRICE_ERROR };
}

export function isBidding(game) {
	return game.status === 'RUNNING' && game.turn?.phase === 'BIDDING';
}

/** R38's refusal, shown in place of the good the player is Dictator of at that system. */
export function dictatorText(goods) {
	return `You're Dictator of ${goods} here — you place that offer when it resolves in Trading.`;
}

/**
 * What the offer form may offer at [systemId] for the player with [email]: the goods traded there in market order,
 * minus those they already have an offer on this turn (R33) and those they're Dictator of there (R38). `notes`
 * says why goods are missing when that matters: the Dictator line for each such good, and "every good" once the
 * player has an offer on all the others.
 */
export function offerChoices(game, systemId, email) {
	const system = game.board.find(s => s.id === systemId);
	if (!system) {
		return { goods: [], notes: [] };
	}
	const offered = new Set((game.you?.offers ?? []).filter(o => o.system === systemId).map(o => o.goods));
	const me = game.players.find(p => p.email === email);
	const dictatorOf = new Set((me?.marketPositions ?? [])
		.filter(position => position.system === systemId && position.tier === 'DICTATOR')
		.map(position => position.goods));
	const traded = system.market.map(entry => entry.goods);
	const goods = traded.filter(g => !offered.has(g) && !dictatorOf.has(g));
	const dictatorGoods = traded.filter(g => dictatorOf.has(g));
	const notes = dictatorGoods.map(dictatorText);
	if (goods.length === 0 && traded.some(g => offered.has(g))) {
		notes.unshift(ALL_OFFERED);
	}
	return { goods, notes };
}

/** R23: whether the player with [email] has a ship at [systemId]; without one the form warns but still allows. */
export function hasShipAt(game, systemId, email) {
	const me = game.players.find(p => p.email === email);
	return (me?.ships ?? []).some(ship => ship.system === systemId);
}

/** "Buy POLY at 8 HT" — one of the player's own offers, without its system. */
export function offerText(offer) {
	return `${TYPE_NAMES[offer.type] ?? offer.type} ${offer.goods} at ${offer.price} HT`;
}

/** The status line after an offer is placed; it says again who can see it (R17). */
export function placedText(offer, systemName) {
	return `Offer placed: ${offerText(offer)}, ${systemName}. Hidden from other players until Trading.`;
}

/**
 * Whether the Deck shows "Your offers this turn": not once the game is over (E05-S08-6). The last turn's offers have
 * all been resolved by then, so the list and its "Only you can see these until Trading resolves them" are stale.
 */
export function showsYourOffers(game) {
	return game.status !== 'FINISHED';
}

/** "Your offers this turn" when it's empty: still open during Bidding, closed after. */
export function noOffersText(game) {
	return isBidding(game) ? 'No offers yet this turn.' : 'You placed no offers this turn.';
}

/** Whether a refused offer was refused because Bidding had closed (A04), from the response status and body. */
export function isClosedRefusal(status, message) {
	return status === 409 && message === SERVER_CLOSED;
}

/** What to show for a refused offer: the S03-7 wording for a close, otherwise the server's own message. */
export function refusalText(status, message, fallback) {
	if (isClosedRefusal(status, message)) {
		return CLOSED_TEXT;
	}
	return message || fallback;
}
