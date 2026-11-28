/*
 * Pure helpers for the Market screen (R140, `.ai/specs/implemented/2026-11-26-bidding-market-screen.md`): every market at a glance during
 * Bidding, the viewer's holdings and reach, and a check of their offers. They read the public board (prices, S/D,
 * `lastChange`, `buyCeiling`, `sellFloor`), players (ships' positions, market positions) and log, and the viewer's own
 * `you.ships` (cargo, `jumpChances`), `you.warehouses`, `you.offers`, `you.tipOffs` and `you.money` from GET
 * /api/games/{id} (BACKWARD_COMPATIBILITY.md §6). Nothing new is decided here: the offers check is advice, never a block.
 */

import { buyChance, marketLevel, signedIndicator } from './board.js';

export const MARKET_GOODS = ['CHIPS', 'FOOD', 'ISO', 'POLY'];
/** POLY and ISO take cargo space; CHIPS and FOOD don't (R42). */
const SPACE_GOODS = ['POLY', 'ISO'];
const LOCATION_NAMES = { PORT: 'Port', PLANET: 'Planet', SPACE: 'Space', SHIPYARD: 'Shipyard' };
const TIER_LETTERS = { RETAILER: 'R', WHOLESALER: 'W', DICTATOR: 'D' };
/**
 * The grid's one filter (`2026-11-28-market-grid-ux.md` criterion 1): pressed, it dims the markets where the viewer
 * holds none of the good. "Where I can be" and "My offers" went: nearly every system is in reach, and offers show on
 * their cells anyway.
 */
export const HELD_FILTER = { id: 'goods', label: 'Where I have goods' };

function me(game, viewerId) {
	return game.players.find(player => player.id === viewerId) ?? null;
}

function systemName(game, systemId) {
	return game.board.find(system => system.id === systemId)?.name ?? systemId;
}

/**
 * The viewer's ships, public position joined with their private load: `{ name, system, systemName, location, cargo,
 * free, canJump, chances }`. A ship can still jump this turn unless it's in the yard or on an expedition.
 */
function ownShips(game, viewerId) {
	const yours = Object.fromEntries((game.you?.ships ?? []).map(ship => [ship.name, ship]));
	const away = new Set((game.you?.expeditions ?? []).map(expedition => expedition.ship));
	const jumped = game.turn?.jumpedShips ?? [];
	const jumpsLeft = ['BIDDING', 'INITIATIVE', 'NEWS', 'JUMPS'].includes(game.turn?.phase);
	return (me(game, viewerId)?.ships ?? []).filter(ship => yours[ship.name]).map(ship => {
		const own = yours[ship.name];
		return {
			name: ship.name,
			system: ship.system,
			systemName: ship.systemName,
			location: ship.location,
			cargo: own.cargo ?? {},
			free: Math.max(0, own.cargoSpaces - own.cargoUsed),
			spaces: own.cargoSpaces,
			canJump: jumpsLeft && ship.location !== 'SHIPYARD' && !away.has(ship.name) && !jumped.includes(ship.name),
			chances: own.jumpChances ?? {},
		};
	});
}

function warehouses(game) {
	return (game.you?.warehouses ?? []).map(warehouse => ({
		system: warehouse.system,
		systemName: warehouse.systemName,
		cargo: warehouse.cargo ?? {},
		free: Math.max(0, warehouse.cargoSpaces - warehouse.cargoUsed),
		used: warehouse.cargoUsed,
		spaces: warehouse.cargoSpaces,
	}));
}

/** The ships that can still jump to [systemId] (not those already there), best chance first: `{ ship, percent }`. */
function reachers(ships, systemId) {
	return ships.filter(ship => ship.canJump && ship.system !== systemId && ship.chances[systemId])
		.map(ship => ({ ship, percent: ship.chances[systemId].percent }))
		.sort((a, b) => b.percent - a.percent);
}

function goodsText(cargo) {
	const held = MARKET_GOODS.filter(goods => cargo[goods] > 0).map(goods => `${goods} ${cargo[goods]}`);
	return held.length ? held.join(', ') : 'empty';
}

/**
 * The holdings strip (criterion 11): one chip per ship and warehouse, `{ id, kind, name, system, text, canJump }`, e.g.
 * "Wayfarer · Tau Ceti Port · FOOD 6, ISO 2 · free 0/8". [id] is `ship:<name>` or `warehouse:<system>`.
 */
export function holdings(game, viewerId) {
	const ships = ownShips(game, viewerId).map(ship => ({
		id: `ship:${ship.name}`,
		kind: 'ship',
		name: ship.name,
		system: ship.system,
		text: `${ship.name} · ${ship.systemName} ${LOCATION_NAMES[ship.location] ?? ship.location} · ${goodsText(ship.cargo)} · free ${ship.free}/${ship.spaces}`,
		canJump: ship.canJump,
	}));
	const stores = warehouses(game).map(warehouse => ({
		id: `warehouse:${warehouse.system}`,
		kind: 'warehouse',
		name: 'Warehouse',
		system: warehouse.system,
		text: `Warehouse · ${warehouse.systemName} · ${goodsText(warehouse.cargo)} · free ${warehouse.free}/${warehouse.spaces}`,
		canJump: false,
	}));
	return [...ships, ...stores];
}

/**
 * One column per system (criterion 5): `{ id, name, presence, here, reach }`. [presence] names what's
 * there now ("Wayfarer (Port), warehouse 12/20"); [reach] is "here", the best jump chance of a ship that can still jump
 * ("reach 83% (Wayfarer)"), or "—".
 */
export function marketColumns(game, viewerId) {
	const ships = ownShips(game, viewerId);
	const stores = warehouses(game);
	return game.board.map(system => {
		const there = ships.filter(ship => ship.system === system.id);
		const store = stores.find(warehouse => warehouse.system === system.id);
		const presence = [
			...there.map(ship => `${ship.name} (${LOCATION_NAMES[ship.location] ?? ship.location})`),
			...(store ? [`warehouse ${store.used}/${store.spaces}`] : []),
		].join(', ');
		const best = reachers(ships, system.id)[0];
		const here = there.length > 0 || Boolean(store);
		return {
			id: system.id,
			name: system.name,
			presence,
			here,
			reach: here ? 'here' : best ? `reach ${best.percent}% (${best.ship.name})` : '—',
		};
	});
}

/** What the viewer holds of [goods] at [systemId]: ships there and the warehouse there. */
function heldAt(ships, stores, systemId, goods) {
	return ships.filter(ship => ship.system === systemId).reduce((sum, ship) => sum + (ship.cargo[goods] ?? 0), 0)
		+ (stores.find(warehouse => warehouse.system === systemId)?.cargo[goods] ?? 0);
}

function freeAt(ships, stores, systemId) {
	return ships.filter(ship => ship.system === systemId).reduce((sum, ship) => sum + ship.free, 0)
		+ (stores.find(warehouse => warehouse.system === systemId)?.free ?? 0);
}

/** "▲2", "▼1" or "—": a price's move when it last resolved. */
export function changeText(change) {
	return change > 0 ? `▲${change}` : change < 0 ? `▼${-change}` : '—';
}

/** "rise", "fall" or "": the colour of a price's last move (playtest, 2026-11-27: green up, red down). */
export function changeTone(change) {
	return change > 0 ? 'rise' : change < 0 ? 'fall' : '';
}

/**
 * The matrix (criteria 3, 4): one row per good, one cell per system, `{ system, price, change, level, buyPercent, held,
 * tier, offer, dictator, cheapest, dearest, priceStep }`, and each row's [spread], `{ from, low, to, high, gap }` or
 * null. [priceStep] is 0 (the row's cheapest) to 4 (its dearest), null when the row has one price
 * (`2026-11-28-market-grid-ux.md` criteria 2, 3).
 */
export function marketMatrix(game, viewerId) {
	const ships = ownShips(game, viewerId);
	const stores = warehouses(game);
	const positions = me(game, viewerId)?.marketPositions ?? [];
	const offers = game.you?.offers ?? [];
	return MARKET_GOODS.map(goods => {
		const cells = game.board.map(system => {
			const entry = system.market.find(m => m.goods === goods);
			if (!entry) {
				return { system: system.id, traded: false };
			}
			const tier = positions.find(p => p.system === system.id && p.goods === goods)?.tier ?? null;
			const offer = offers.find(o => o.system === system.id && o.goods === goods) ?? null;
			return {
				system: system.id,
				traded: true,
				price: entry.price,
				change: changeText(entry.lastChange ?? 0),
				changeTone: changeTone(entry.lastChange ?? 0),
				level: marketLevel(entry.indicator),
				buyPercent: Math.round(buyChance(entry.indicator) * 100),
				held: heldAt(ships, stores, system.id, goods),
				tier: tier ? TIER_LETTERS[tier] : '',
				offer: offer ? `${offer.type === 'BUY' ? 'Buy' : 'Sell'} ${offer.price}` : '',
				offerType: offer?.type ?? null,
				dictator: tier === 'DICTATOR',
			};
		});
		const traded = cells.filter(cell => cell.traded);
		const low = Math.min(...traded.map(cell => cell.price));
		const high = Math.max(...traded.map(cell => cell.price));
		for (const cell of traded) {
			cell.cheapest = low !== high && cell.price === low;
			cell.dearest = low !== high && cell.price === high;
			cell.priceStep = low !== high ? Math.round(4 * (cell.price - low) / (high - low)) : null;
		}
		const cheapest = traded.find(cell => cell.price === low);
		const dearest = traded.find(cell => cell.price === high);
		const spread = low < high
			? { from: systemName(game, cheapest.system), low, to: systemName(game, dearest.system), high, gap: high - low }
			: null;
		return { goods, cells, spread };
	});
}

/** Whether [cell] stays bright under filter [filter] ('all' or 'goods'); filtering dims, never removes. */
export function cellMatches(filter, cell) {
	return cell.traded && (filter !== HELD_FILTER.id || cell.held > 0);
}

/** "Buy above 11 or sell below 4 moves no further" (R15), from the server's limits; '' when neither applies. */
export function saturationHint(entry) {
	const parts = [
		...(entry?.buyCeiling ? [`buy above ${entry.buyCeiling}`] : []),
		...(entry?.sellFloor ? [`sell below ${entry.sellFloor}`] : []),
	];
	if (!parts.length) {
		return '';
	}
	const text = `${parts.join(' or ')} moves no further`;
	return text[0].toUpperCase() + text.slice(1);
}

/** The last Trading's trades in [systemId]'s [goods] market, from the log's trade records: "Orion Freight bought 4 at 9". */
function lastTrades(game, systemId, goods) {
	const records = (game.log ?? []).filter(entry => entry.trade?.system === systemId && entry.trade.goods === goods);
	const turn = records.at(-1)?.turn;
	return records.filter(entry => entry.turn === turn).map(entry => {
		const who = game.players.find(player => player.id === entry.trade.playerId)?.corporationName ?? entry.trade.playerId;
		return `${who} ${entry.trade.type === 'BUY' ? 'bought' : 'sold'} ${entry.trade.units} at ${entry.trade.price}`;
	});
}

/**
 * The inspector for [goods] at [systemId] (criteria 8–10): the market's detail, who holds the public tiers, learned
 * news that names it, and the viewer's side: stock and space here, ships that can bring space or goods, stock elsewhere,
 * cash, and the R15 hint.
 */
export function marketDetail(game, viewerId, systemId, goods) {
	const system = game.board.find(s => s.id === systemId);
	const entry = system?.market.find(m => m.goods === goods);
	if (!entry) {
		return null;
	}
	const ships = ownShips(game, viewerId);
	const stores = warehouses(game);
	const holders = tier => game.players.filter(player => (player.marketPositions ?? [])
		.some(p => p.system === systemId && p.goods === goods && p.tier === tier)).map(player => player.corporationName);
	const names = [system.name.toLowerCase(), goods.toLowerCase()];
	const learned = (game.you?.tipOffs ?? []).filter(tipOff => tipOff.learned && tipOff.details).map(tipOff => tipOff.details);
	const announced = (game.log ?? []).filter(e => e.turn === game.turn?.number && e.text.startsWith('News:')).map(e => e.text);
	const news = [...learned, ...announced].filter(text => names.some(name => text.toLowerCase().includes(name)));
	const reach = reachers(ships, systemId);
	const elsewhere = [
		...ships.filter(ship => ship.system !== systemId && ship.cargo[goods] > 0).map(ship => {
			const chance = ship.canJump ? ship.chances[systemId]?.percent : null;
			return `${goods}: ${ship.cargo[goods]} on ${ship.name} at ${ship.systemName}${chance ? ` (reach ${chance}%)` : ''}`;
		}),
		...stores.filter(w => w.system !== systemId && w.cargo[goods] > 0).map(w => `${goods}: ${w.cargo[goods]} in the warehouse at ${w.systemName}`),
	];
	return {
		title: `${goods} at ${system.name}`,
		price: entry.price,
		indicator: `S/D ${signedIndicator(entry.indicator)}`,
		level: marketLevel(entry.indicator),
		buyPercent: Math.round(buyChance(entry.indicator) * 100),
		lastMove: changeText(entry.lastChange ?? 0),
		lastMoveTone: changeTone(entry.lastChange ?? 0),
		lastTrades: lastTrades(game, systemId, goods),
		wholesalers: holders('WHOLESALER'),
		dictator: holders('DICTATOR'),
		news,
		held: heldAt(ships, stores, systemId, goods),
		free: SPACE_GOODS.includes(goods) ? freeAt(ships, stores, systemId) : null,
		reachable: reach.map(r => `${r.ship.name}: ${SPACE_GOODS.includes(goods) ? `free ${r.ship.free}, ` : ''}${r.percent}%`),
		elsewhere,
		cash: game.you?.money ?? 0,
		hint: saturationHint(entry),
	};
}

/**
 * The offers check (criterion 12): warnings for each of the viewer's offers, then for the set. `{ offer, system, goods,
 * text }` per warning, [offer], [system] and [goods] null for the set's. Each is advice: a bid that looks impossible may be a bluff or part of a plan.
 */
export function offersCheck(game, viewerId) {
	const offers = game.you?.offers ?? [];
	const ships = ownShips(game, viewerId);
	const stores = warehouses(game);
	const present = systemId => ships.some(ship => ship.system === systemId) || stores.some(w => w.system === systemId);
	const warnings = [];
	for (const offer of offers) {
		const name = offer.systemName ?? systemName(game, offer.system);
		const reach = reachers(ships, offer.system);
		const tag = `${offer.type === 'BUY' ? 'Buy' : 'Sell'} ${offer.goods} at ${name}`;
		const about = { offer: tag, system: offer.system, goods: offer.goods };
		if (!present(offer.system)) {
			warnings.push({ ...about, text: reach.length
				? `Relies on ${reach[0].ship.name} reaching ${name} (${reach[0].percent}%).`
				: `Nothing of yours can be there. A won buy is lost (R23).` });
		}
		if (offer.type === 'SELL' && heldAt(ships, stores, offer.system, offer.goods) === 0
			&& !reach.some(r => (r.ship.cargo[offer.goods] ?? 0) > 0)) {
			warnings.push({ ...about, text: `Nothing to sell: no ${offer.goods} at ${name}, and no ship with ${offer.goods} can reach it.` });
		}
		if (offer.type === 'BUY' && SPACE_GOODS.includes(offer.goods) && (present(offer.system) || reach.length)
			&& freeAt(ships, stores, offer.system) === 0 && !reach.some(r => r.ship.free > 0)) {
			warnings.push({ ...about, text: `No free space can reach ${name}; overflow is lost (R16).` });
		}
	}
	const places = [...new Set(offers.map(offer => offer.system))];
	const covered = places.filter(present);
	const movers = ships.filter(ship => ship.canJump && !covered.includes(ship.system));
	const most = covered.length + movers.length;
	// Only places a ship could reach count: one nothing can reach already has its own warning.
	const reachable = places.filter(place => !present(place) && reachers(ships, place).length);
	if (reachable.length > movers.length) {
		warnings.push({ offer: null, system: null, goods: null, text: `Offers in ${places.length} systems, but you can cover at most ${most} this turn.` });
	}
	return warnings;
}

/** The texts of the warnings in [warnings] (from offersCheck) about the offer at [systemId] for [goods]. */
export function warningsFor(warnings, systemId, goods) {
	return warnings.filter(warning => warning.system === systemId && warning.goods === goods).map(warning => warning.text);
}

/** The ship or warehouse a holdings chip stands for, `{ name, system, cargo, canJump, chances }`, or null. */
function holdingFor(game, viewerId, holdingId) {
	const ship = ownShips(game, viewerId).find(s => `ship:${s.name}` === holdingId);
	if (ship) {
		return ship;
	}
	const store = warehouses(game).find(w => `warehouse:${w.system}` === holdingId);
	return store ? { name: `Warehouse at ${store.systemName}`, system: store.system, cargo: store.cargo, canJump: false, chances: {} } : null;
}

/**
 * Where the holding [holdingId] could sell what it holds (`2026-11-28-market-grid-ux.md` criterion 6): every market of
 * a good it holds at its own system, and for a ship that can still jump, at every system it can reach.
 * `{ system, goods, units, price, percent, best }`, dearest first per good; [percent] is null at its own system, and
 * [best] marks each good's dearest.
 */
export function outlets(game, viewerId, holdingId) {
	const holding = holdingFor(game, viewerId, holdingId);
	if (!holding) {
		return [];
	}
	const places = [holding.system, ...(holding.canJump ? Object.keys(holding.chances).filter(id => id !== holding.system) : [])];
	return MARKET_GOODS.filter(goods => holding.cargo[goods] > 0).flatMap(goods => {
		const found = places.flatMap(systemId => {
			const entry = game.board.find(system => system.id === systemId)?.market.find(m => m.goods === goods);
			return entry ? [{
				system: systemId,
				goods,
				units: holding.cargo[goods],
				price: entry.price,
				percent: systemId === holding.system ? null : holding.chances[systemId].percent,
			}] : [];
		}).sort((a, b) => b.price - a.price);
		return found.map((outlet, index) => ({ ...outlet, best: index === 0 }));
	});
}

/**
 * The outlets of [holdingId] in words, for the line under the holdings strip: "Wayfarer: FOOD 6 sells best at Beta
 * Hydri 14 (58%), then Tau Ceti 7 (here)." '' without a holding.
 */
export function outletsText(game, viewerId, holdingId) {
	const holding = holdingFor(game, viewerId, holdingId);
	if (!holding) {
		return '';
	}
	const found = outlets(game, viewerId, holdingId);
	if (!found.length) {
		return `${holding.name} holds nothing to sell.`;
	}
	const where = outlet => `${systemName(game, outlet.system)} ${outlet.price} (${outlet.percent === null ? 'here' : `${outlet.percent}%`})`;
	const parts = MARKET_GOODS.map(goods => found.filter(outlet => outlet.goods === goods)).filter(list => list.length).map(list => {
		const [best, ...rest] = list;
		return `${best.goods} ${best.units} sells best at ${where(best)}${rest.length ? `, then ${rest.map(where).join(', ')}` : ''}`;
	});
	return `${holding.name}: ${parts.join('; ')}.`;
}
