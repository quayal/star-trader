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

/** Whether [game] is in Opportunity and the viewer still has to place their factories' output (S03-1, R102). */
export function isCollecting(game) {
	return game.status === 'RUNNING' && game.turn?.phase === 'OPPORTUNITY' && Boolean(game.you?.collect);
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

export const NOT_INVESTING_TEXT = 'Investment opens after Opportunity. Browse and plan here now; you buy then.';
/** S08-4: on the Deck and System details, while the viewer still has to place their output. */
export const COLLECT_ELSEWHERE_TEXT = 'Collect your output first.';
export const OPPORTUNITY_LINK_TEXT = 'Go to Opportunity';
/** Infrastructure during Opportunity (playtest, 2026-11-21): the phase is played on its own tab. */
export const OPPORTUNITY_ELSEWHERE_TEXT = 'It’s Opportunity now: collect, sabotage, agents and opportunities are on the Opportunity tab. Investment comes next.';
/** The Opportunity tab outside its phase (creator, 2026-11-23: plan here at any time). */
export const OPPORTUNITY_NOT_YET_TEXT = 'Opportunity comes after Trading. Plan your sabotage and recruiting here now; you act on your Opportunity turn. Agents move at any time.';
/** The invest dialog outside Investment (creator, 2026-11-23): what it shows is for planning. */
export const BUY_IN_INVESTMENT_TEXT = 'You can buy this in Investment. Prices and your money may have changed by then.';

/**
 * What the Infrastructure screen is for the viewer: 'collect' or 'opportunity' in Opportunity (R102), 'invest' or 'done'
 * in Investment, and 'closed' otherwise (S08-3, S08-4, S08-6).
 */
export function infrastructureState(game, playerId) {
	if (isCollecting(game)) {
		return 'collect';
	}
	if (game.status === 'RUNNING' && game.turn?.phase === 'OPPORTUNITY') {
		return 'opportunity';
	}
	if (!isInvesting(game)) {
		return 'closed';
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

/**
 * The viewer's own warehouses for the screen (S08-6): where, blocks, "used / spaces", and what is held ("Empty" when
 * nothing). While the viewer may plan (creator, 2026-11-19, 2026-11-23: at any time in a running game), each row's
 * `invest` is 'add', and every system where they have none yet follows as a row with `invest` 'build' and `blocks` 0;
 * otherwise `invest` is null and only owned rows show.
 */
export function ownWarehouseRows(game) {
	const owned = (game.you?.warehouses ?? []).map(warehouse => {
		const held = Object.entries(warehouse.cargo).map(([goods, units]) => `${units} ${goods}`);
		return {
			system: warehouse.system,
			systemName: warehouse.systemName,
			blocks: warehouse.blocks,
			cargoUsed: warehouse.cargoUsed,
			cargoSpaces: warehouse.cargoSpaces,
			held: held.length ? held.join(', ') : 'Empty',
			invest: canPlan(game) ? 'add' : null,
		};
	});
	if (!canPlan(game)) {
		return owned;
	}
	const build = investSystems(game)
		.filter(entry => !owned.some(row => row.system === entry.system))
		.map(entry => ({ system: entry.system, systemName: entry.systemName, blocks: 0, cargoUsed: 0, cargoSpaces: 0, held: '', invest: 'build' }));
	return [...owned, ...build];
}

/**
 * The viewer's own factories for the screen (S08-6): where, which good, units a turn. Public data, picked out by
 * playerId. While the viewer may plan (creator, 2026-11-19, 2026-11-23), each row's `invest` is 'add', and every good a system can
 * produce where they have no factory follows with `invest` 'build' and `size` 0; otherwise `invest` is null.
 */
export function ownFactoryRows(game, playerId) {
	const me = game.players.find(player => player.id === playerId);
	const buying = canPlanFactory(game);
	const owned = (me?.factories ?? []).map(factory => ({
		system: factory.system,
		systemName: game.board.find(system => system.id === factory.system)?.name ?? factory.system,
		goods: factory.goods,
		size: factory.size,
		disabledTurns: factory.disabledTurns ?? 0,
		invest: buying ? 'add' : null,
	}));
	if (!buying) {
		return owned;
	}
	const build = investFactories(game)
		.filter(entry => !owned.some(row => row.system === entry.system && row.goods === entry.goods))
		.map(entry => ({ system: entry.system, systemName: entry.systemName, goods: entry.goods, size: 0, invest: 'build' }));
	return [...owned, ...build];
}

/**
 * What the viewer may do at one system's Warehouses and Factories tables (creator, 2026-11-19): `{ warehouse, factories }`,
 * `warehouse` being 'add' (they own one here), 'build' (they don't) or null when they can't buy now, and `factories` the
 * goods produced here, each `{ goods, size, invest }` with 'add' or 'build'. Empty when there is nothing to plan.
 */
export function systemInvestActions(game, systemId) {
	if (!canPlan(game)) {
		return { warehouse: null, factories: [] };
	}
	const blocks = investSystems(game).find(entry => entry.system === systemId)?.blocks ?? 0;
	return {
		warehouse: blocks > 0 ? 'add' : 'build',
		factories: factoriesAt(game, systemId).map(entry => ({ goods: entry.goods, size: entry.size, invest: entry.size > 0 ? 'add' : 'build' })),
	};
}

/** A row's button: "Add blocks", "Build warehouse", "Add units", "Build factory". */
export function investButtonLabel(kind, invest) {
	if (kind === 'blocks') {
		return invest === 'build' ? 'Build warehouse' : BLOCKS_BUTTON;
	}
	return invest === 'build' ? 'Build factory' : UNITS_BUTTON;
}

/** The most blocks the viewer's money pays for. */
export function maxBlocks(game) {
	const cost = game.you?.invest?.blockCost;
	return cost ? Math.max(0, Math.floor((game.you.money ?? 0) / cost)) : 0;
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

/** Whether the viewer may buy now (S04-1): in Investment. */
export function canBuy(game) {
	return isInvesting(game) && Boolean(game.you?.invest);
}

/**
 * Whether the viewer may browse and plan what Investment sells (creator, 2026-11-23): at any time while the game runs,
 * since the server sends `you.invest` then. Only [canBuy] lets a purchase through.
 */
export function canPlan(game) {
	return game.status === 'RUNNING' && Boolean(game.you?.invest);
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

/** Whether to offer factories: the viewer may plan and something can be produced (no ship needed, E06-S05). */
export function canPlanFactory(game) {
	return canPlan(game) && investFactories(game).length > 0;
}

/** What [systemId] can produce, for the Goods dropdown. */
export function factoriesAt(game, systemId) {
	return investFactories(game).filter(entry => entry.system === systemId);
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

// ── The invest dialog (creator, 2026-11-19): opened from a warehouse or factory row ──


/**
 * What the dialog for [target] (`{ kind: 'blocks', system }` or `{ kind: 'units', system, goods }`) shows: `{ title,
 * intro, factory, wait }`, `factory` being the `you.invest.factories` entry for units and `wait` why Confirm waits ('' in
 * Investment). Null when there's nothing to plan (the game isn't running, or the good isn't produced there), so the page
 * closes the dialog.
 */
export function investDialogText(game, target) {
	if (!canPlan(game) || !target) {
		return null;
	}
	if (target.kind === 'blocks') {
		const system = investSystems(game).find(entry => entry.system === target.system);
		if (!system) {
			return null;
		}
		const { blockCost } = game.you.invest;
		return {
			title: system.blocks === 0 ? `Build a warehouse at ${system.systemName}` : `Add warehouse blocks at ${system.systemName}`,
			intro: `You have ${warehouseText(system.blocks)} here. ${blockCost} HT a block, ${CARGO_SPACES_PER_BLOCK} cargo spaces each. New blocks first count next turn.`,
			factory: null,
			wait: canBuy(game) ? '' : BUY_IN_INVESTMENT_TEXT,
		};
	}
	const factory = factoriesAt(game, target.system).find(entry => entry.goods === target.goods);
	if (!factory) {
		return null;
	}
	return {
		title: factory.size === 0 ? `Build a factory for ${factory.goods} at ${factory.systemName}` : `Add ${factory.goods} units at ${factory.systemName}`,
		intro: `${factory.size === 0 ? 'No factory here yet' : `It makes ${factory.size} a turn`}. ${factory.unitCost} HT a unit (5 × the ${factory.price} HT price + 10).`,
		factory,
		wait: canBuy(game) ? '' : BUY_IN_INVESTMENT_TEXT,
	};
}

/** "Most you can add: 4 blocks", or why it's none. [max] is what the money (and, for units, the cap) allows. */
export function investMaxText(kind, max) {
	if (max > 0) {
		return `You can add up to ${kind === 'blocks' ? blocksText(max) : unitsText(max)}.`;
	}
	return kind === 'blocks' ? "You can't pay for a block." : "You can't add a unit: you're short of money or at your factory cap.";
}
