/*
 * Pure helpers for reputation (E13): every player's is public, 0 to 40 (R85), and a player may buy 1d6 more once per
 * Investment for 20 HT (R87). The server sends each player's `reputation` and, to the viewer in Investment,
 * `you.invest.reputationCost` and `you.invest.boughtReputation`; nothing is calculated here that the server decides.
 */

import { canBuy } from './investment.js';

export const MAX_REPUTATION = 40;

/** The button on the viewer's own Corporations card (creator, 2026-11-19): "Buy 1d6 reputation — 20 HT". */
export function buyLabel(cost) {
	return `Buy 1d6 reputation — ${cost} HT`;
}

export const BOUGHT_TEXT = "You've bought reputation this Investment.";

/** E13-S01-1: "27 / 40" for the Corporations card; a response from before reputation reads as the start value, 20. */
export function reputationText(reputation) {
	return `${reputation ?? 20} / ${MAX_REPUTATION}`;
}

/** The viewer's own reputation, from the public players list. */
export function myReputation(game) {
	return game.players?.find(player => player.id === game.you?.id)?.reputation ?? 20;
}

/**
 * E13-S03: what the viewer's Corporations card says about buying, `{ intro, cost, canBuy, reason }`. [canBuy] is false once bought this
 * Investment, at 40, or with too little money, and [reason] then says why, in the server's words.
 */
export function reputationPurchase(game) {
	if (!canBuy(game)) {
		return null;
	}
	const cost = game.you.invest.reputationCost ?? 20;
	const reputation = myReputation(game);
	const intro = `Your reputation is ${reputation} / ${MAX_REPUTATION}. Once per Investment, buy 1d6 more for ${cost} HT.`;
	if (game.you.invest.boughtReputation) {
		return { intro, cost, canBuy: false, reason: BOUGHT_TEXT };
	}
	if (reputation >= MAX_REPUTATION) {
		return { intro, cost, canBuy: false, reason: `Your reputation is already at ${MAX_REPUTATION}.` };
	}
	const money = game.you.money ?? 0;
	if (money < cost) {
		return { intro, cost, canBuy: false, reason: `Buying reputation costs ${cost} HT; you have ${money} HT.` };
	}
	return { intro, cost, canBuy: true, reason: '' };
}
