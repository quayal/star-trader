/*
 * Pure helpers for the Investment phase (E06-S01, R44), which happens on the Infrastructure screen (s10, E06-S08, D12).
 * They read `turn.phase` and `turn.doneInvesting` from GET /api/games/{id} (BACKWARD_COMPATIBILITY.md §6):
 * `turn.doneInvesting` is the public list of players' ids who signalled done (D09); nothing private is needed.
 */

export const DONE_INVESTING_BUTTON = "I'm done investing";
/** S01-2: shown in place of the button once the player has signalled done. */
export const DONE_INVESTING_TEXT = 'You signalled done investing';
export const INVESTMENT_CLOSED_BY_YOU = 'Everyone is done. Investment has closed.';
export const ALREADY_CLOSED_INVESTING = 'Investment has already closed.';
export const INVESTMENT_NOT_OPEN = "Investment isn't open.";

/** Whether [game] is running and in this turn's Investment. */
export function isInvesting(game) {
	return game.status === 'RUNNING' && game.turn?.phase === 'INVESTMENT';
}

/** Whether the player with [playerId] has signalled done in this Investment. */
export function isDoneInvesting(game, playerId) {
	return isInvesting(game) && game.turn.doneInvesting.includes(playerId);
}

/**
 * Who the game is still waiting for during Investment (S01-3, D09): "you" first while the viewer isn't done, then the
 * other players' corporation names in join order. Empty outside Investment.
 */
export function waitingForInvesting(game, playerId) {
	if (!isInvesting(game)) {
		return [];
	}
	const pending = game.players.filter(p => !game.turn.doneInvesting.includes(p.id));
	const you = pending.some(p => p.id === playerId) ? ['you'] : [];
	return [...you, ...pending.filter(p => p.id !== playerId).map(p => p.corporationName)];
}

/** What to say once "done" was accepted: nothing while others still invest, or that this player's signal closed it. */
export function doneInvestingAcceptedText(game) {
	return isInvesting(game) ? '' : INVESTMENT_CLOSED_BY_YOU;
}

/** Whether a refused "done" only means Investment closed before the click (A04), which isn't the player's error. */
export function isInvestmentClosedRefusal(status, message) {
	return status === 409 && message === INVESTMENT_NOT_OPEN;
}

/** What to say when "done" was refused: a close that raced the click isn't the player's error (A04). */
export function doneInvestingRefusedText(status, message, fallback) {
	return isInvestmentClosedRefusal(status, message) ? ALREADY_CLOSED_INVESTING : message || fallback;
}

// ── Collect (E06-S03, R47) ──
//
// At the start of Investment a player with factories places each factory's output. `game.you.collect` (private to the
// viewer, BACKWARD_COMPATIBILITY.md §6) lists their factories: `{ system, systemName, goods, units, destinations }`, each
// destination being their warehouse there (`ship` null) or one of their ships in port there, with `freeSpaces` (null for
// CHIPS and FOOD, which take no space). It's null once they confirmed, outside Investment, and for a player with none.

export const COLLECT_TITLE = 'Collect your output';
export const COLLECT_INTRO = 'Your factories have made their goods. Choose where each goes. Whatever you leave out, or can\'t fit, is lost.';
export const COLLECT_BUTTON = 'Confirm output';
/** S03-5: shown in the top bar in place of "done" until the player has confirmed. */
export const COLLECT_FIRST_TEXT = 'Place your factories\' output first.';
export const COLLECT_WHOLE_NUMBER = 'Enter a whole number of units, 0 or more.';

/** Whether [game] is in Investment and the viewer still has to place their factories' output (S03-1). */
export function isCollecting(game) {
	return isInvesting(game) && Boolean(game.you?.collect);
}

/** What the viewer's collect is keyed by: their typed amounts belong to one game and turn only (lesson 2026-11-04). */
export function collectKey(game) {
	return `${game.id}/${game.turn.number}`;
}

/** The viewer's factories to place, or none. */
export function collectFactories(game) {
	return game.you?.collect?.factories ?? [];
}

/** A factory's heading: "ISO at Tau Ceti — 6 units". */
export function collectHeading(factory) {
	return `${factory.goods} at ${factory.systemName} — ${factory.units} ${factory.units === 1 ? 'unit' : 'units'}`;
}

/** S03-2: a destination's name and what it has room for. CHIPS and FOOD need no space, so only POLY and ISO show it. */
export function destinationLabel(destination) {
	const name = destination.ship ?? 'Warehouse';
	return destination.freeSpaces === null ? name : `${name} (${destination.freeSpaces} free)`;
}

/**
 * The amounts a factory starts with: its units filled into its destinations in order, each up to its free space (all of
 * it for CHIPS and FOOD), so the usual case needs no typing. Whatever doesn't fit stays unplaced, and the warning says so.
 */
export function collectDefaults(factory) {
	let left = factory.units;
	return factory.destinations.map(destination => {
		const placed = destination.freeSpaces === null ? left : Math.min(left, destination.freeSpaces);
		left -= placed;
		return placed;
	});
}

/** How many units of [factory] are lost for [amounts] (a number per destination): the ones not placed, and the ones over a destination's free space (S03-3). */
export function collectLoss(factory, amounts) {
	const placed = amounts.reduce((sum, units) => sum + units, 0);
	const overflow = factory.destinations.reduce(
		(sum, destination, index) => sum + (destination.freeSpaces === null ? 0 : Math.max(0, amounts[index] - destination.freeSpaces)), 0);
	return { unplaced: Math.max(0, factory.units - placed), overflow };
}

/**
 * S03-3, S03-4: the consequence of the amounts, before the player confirms, as one line per factory that loses
 * something, or "" when nothing is lost. A factory with nowhere to keep its goods loses all of them.
 */
export function collectWarning(factories, amounts) {
	return factories.map((factory, index) => {
		if (factory.destinations.length === 0) {
			return `All ${factory.units} ${factory.goods} at ${factory.systemName} will be lost: you have no warehouse or ship there to keep it.`;
		}
		const { unplaced, overflow } = collectLoss(factory, amounts[index]);
		if (unplaced + overflow === 0) {
			return '';
		}
		const reasons = [overflow > 0 ? `${overflow} won't fit` : '', unplaced > 0 ? `${unplaced} not placed` : ''].filter(Boolean).join(', ');
		return `${unplaced + overflow} ${factory.goods} at ${factory.systemName} will be lost (${reasons}).`;
	}).filter(Boolean).join(' ');
}

/**
 * The request for what the player typed: [values] is, per factory, the text in each destination's input. Each is a whole
 * number, 0 or more, and a factory's amounts can't add up to more than it makes. On a problem: `{ error, factory,
 * destination }`, pointing at the input to focus; else `{ body }`.
 */
export function validateCollect(factories, values) {
	const placements = [];
	for (const [f, factory] of factories.entries()) {
		let total = 0;
		for (const [d, destination] of factory.destinations.entries()) {
			const text = String(values[f][d] ?? '').trim();
			const units = text === '' ? 0 : Number(text);
			if (!Number.isInteger(units) || units < 0) {
				return { error: COLLECT_WHOLE_NUMBER, factory: f, destination: d };
			}
			total += units;
			if (units > 0) {
				placements.push({ system: factory.system, goods: factory.goods, ship: destination.ship, units });
			}
		}
		if (total > factory.units) {
			return { error: `${factory.goods} at ${factory.systemName}: ${total} placed, but the factory makes ${factory.units}.`, factory: f, destination: 0 };
		}
	}
	return { body: { placements } };
}

// ── The Infrastructure screen (s10, E06-S08, D12) ──

export const NOT_INVESTING_TEXT = 'Investment opens after Trading.';
/** S08-4: on the Deck and System details, while the viewer still has to place their output. */
export const COLLECT_ELSEWHERE_TEXT = 'Collect your output first.';
export const INFRASTRUCTURE_LINK_TEXT = 'Go to Infrastructure';

/** What the Infrastructure screen is for the viewer: 'closed' outside Investment, 'collect', 'invest', or 'done' (S08-3, S08-4, S08-6). */
export function infrastructureState(game, playerId) {
	if (!isInvesting(game)) {
		return 'closed';
	}
	if (isCollecting(game)) {
		return 'collect';
	}
	return isDoneInvesting(game, playerId) ? 'done' : 'invest';
}

/** S08-4: once the viewer is done, who the game still waits for ("Waiting for Vega Lines"), or that it's closing; else nothing. */
export function infrastructureStatusText(game, playerId) {
	if (infrastructureState(game, playerId) !== 'done') {
		return '';
	}
	const others = waitingForInvesting(game, playerId);
	return others.length ? `${DONE_INVESTING_TEXT}. Waiting for ${others.join(', ')}.` : DONE_INVESTING_TEXT;
}

/** The viewer's own warehouses for the screen (S08-6): where, blocks, "used / spaces", and what is held ("Empty" when nothing). */
export function ownWarehouseRows(game) {
	return (game.you?.warehouses ?? []).map(warehouse => {
		const held = Object.entries(warehouse.cargo).map(([goods, units]) => `${units} ${goods}`);
		return {
			systemName: warehouse.systemName,
			blocks: warehouse.blocks,
			cargoUsed: warehouse.cargoUsed,
			cargoSpaces: warehouse.cargoSpaces,
			held: held.length ? held.join(', ') : 'Empty',
		};
	});
}

/** The viewer's own factories for the screen (S08-6): where, which good, units a turn. Public data, picked out by playerId. */
export function ownFactoryRows(game, playerId) {
	const me = game.players.find(player => player.id === playerId);
	return (me?.factories ?? []).map(factory => ({
		systemName: game.board.find(system => system.id === factory.system)?.name ?? factory.system,
		goods: factory.goods,
		size: factory.size,
	}));
}

// ── Buying warehouse blocks (E06-S04, R45) ──
//
// `game.you.invest` (private to the viewer, BACKWARD_COMPATIBILITY.md §6) is `{ blockCost, systems: [{ system, systemName,
// blocks }], factories }`: what a block costs, every system with the blocks the viewer already owns there (a ship isn't
// needed to buy anywhere), and the factories (below). It's null outside Investment and while they still have to collect.

export const INVEST_NOTE = 'Everyone invests at the same time. The turn moves on once everyone is done.';
export const BLOCKS_BUTTON = 'Add blocks';
export const BLOCKS_WHOLE_NUMBER = 'Enter a whole number of blocks, 1 or more.';
/** R45: one block holds as many cargo spaces as a ship (R42). */
export const CARGO_SPACES_PER_BLOCK = 10;

/** Whether the viewer may buy now (S04-1): in Investment, with nothing left to collect. */
export function canBuy(game) {
	return isInvesting(game) && Boolean(game.you?.invest);
}

/** The systems the viewer may buy warehouse blocks in (S04-1): all six; a ship isn't needed. */
export function investSystems(game) {
	return game.you?.invest?.systems ?? [];
}

/** What the viewer owns in a system, with its capacity at 10 cargo spaces a block (S04-3): "2 blocks, 20 cargo spaces". */
export function warehouseText(blocks) {
	return blocks === 0 ? 'no warehouse yet' : `${blocksText(blocks)}, ${blocks * CARGO_SPACES_PER_BLOCK} cargo spaces`;
}

/** "1 block", "3 blocks". */
export function blocksText(blocks) {
	return `${blocks} ${blocks === 1 ? 'block' : 'blocks'}`;
}

/** A system's option in the picker: "Tau Ceti — 2 blocks, 20 cargo spaces". */
export function systemOptionLabel(system) {
	return `${system.systemName} \u2014 ${warehouseText(system.blocks)}`;
}

/** The number of blocks typed, or null when it isn't a whole number, 1 or more. */
export function parseBlocks(text) {
	const trimmed = String(text ?? '').trim();
	if (!/^\d+$/.test(trimmed)) {
		return null;
	}
	const blocks = Number(trimmed);
	return blocks >= 1 ? blocks : null;
}

/** The total for what was typed, "3 blocks cost 60 HT", or "" while it isn't a valid number (S04-1). */
export function blocksCostText(text, blockCost) {
	const blocks = parseBlocks(text);
	return blocks === null ? '' : `${blocksText(blocks)} ${blocks === 1 ? 'costs' : 'cost'} ${blocks * blockCost} HT`;
}

/** S04-2: the blocks to buy, or the error the server would give: not a whole number, or more than [money] pays for. */
export function validateBlocks(text, blockCost, money) {
	const blocks = parseBlocks(text);
	if (blocks === null) {
		return { error: BLOCKS_WHOLE_NUMBER };
	}
	const cost = blocks * blockCost;
	if (cost > money) {
		return { error: `${blocksCostText(text, blockCost)}; you have ${money} HT.` };
	}
	return { blocks };
}

// ── Building or extending a factory (E06-S05, R46) ──
//
// `game.you.invest.factories` is `[{ system, systemName, goods, size, price, unitCost }]`: for each system, each good it can
// produce, whether or not the viewer has a ship or a factory there, with the units they make there now (0 for no factory),
// the good's market price and the cost of one unit of size, 5 × price + 10 HT. Beta Hydri and Epsilon Eridani produce nothing.

export const UNITS_BUTTON = 'Add units';
export const UNITS_WHOLE_NUMBER = 'Enter a whole number of units, 1 or more.';

/** The goods the viewer can add units to now (S05-1, S05-5). */
export function investFactories(game) {
	return game.you?.invest?.factories ?? [];
}

/** Whether to show the panel: the viewer may buy and something can be produced (no ship needed, E06-S05). */
export function canBuildFactory(game) {
	return canBuy(game) && investFactories(game).length > 0;
}

/** The systems that can produce something, in the order of the list (map order): `[{ system, systemName }]` for the System dropdown. */
export function factorySystems(game) {
	const seen = new Set();
	return investFactories(game).filter(entry => !seen.has(entry.system) && seen.add(entry.system))
		.map(entry => ({ system: entry.system, systemName: entry.systemName }));
}

/** What [systemId] can produce, for the Goods dropdown. */
export function factoriesAt(game, systemId) {
	return investFactories(game).filter(entry => entry.system === systemId);
}

/** A goods option: "ISO \u2014 makes 6 a turn", or "\u2014 no factory yet". */
export function goodsOptionLabel(factory) {
	return `${factory.goods} \u2014 ${factory.size === 0 ? 'no factory yet' : `makes ${factory.size} a turn`}`;
}

/** "1 unit", "3 units". */
export function unitsText(units) {
	return `${units} ${units === 1 ? 'unit' : 'units'}`;
}

/** The number of units typed, or null when it isn't a whole number, 1 or more. */
export function parseUnits(text) {
	return parseBlocks(text);
}

/** S05-1, S05-2: the total for what was typed, with the unit cost and the good's price it comes from; "" while it isn't valid. */
export function unitsCostText(text, factory) {
	const units = parseUnits(text);
	if (units === null) {
		return '';
	}
	return `${unitsText(units)} ${units === 1 ? 'costs' : 'cost'} ${units * factory.unitCost} HT (${factory.unitCost} HT a unit; ${factory.goods} is at ${factory.price} HT)`;
}

/**
 * S05-4: the units to buy, or the error the server would give: not whole, beyond the factory cap's [cap].room (R84, with
 * its [cap].refusal; see connections.js `factoryCap`), or more than [money] pays for.
 */
export function validateUnits(text, factory, money, cap = null) {
	const units = parseUnits(text);
	if (units === null) {
		return { error: UNITS_WHOLE_NUMBER };
	}
	if (cap && units > cap.room) {
		return { error: cap.refusal };
	}
	const cost = units * factory.unitCost;
	if (cost > money) {
		return { error: `${unitsText(units)} ${units === 1 ? 'costs' : 'cost'} ${cost} HT; you have ${money} HT.` };
	}
	return { units };
}
