/*
 * Pure helpers for the Assets tab (creator, 2026-11-18): where the signed-in player's money sits. Everything comes from
 * `you.assets`, private to the viewer (R29), with every value worked out by the server: ships at what they would fetch
 * sold (R61), goods at today's price where they are, factories by what one turn's output is worth at today's price.
 */

export const NO_SHIPS_TEXT = 'You have no ships.';
export const NO_WAREHOUSES_TEXT = 'You have no warehouses.';
export const NO_FACTORIES_TEXT = 'You have no factories.';
export const NOT_TRADED_TEXT = 'not traded here';

const LOCATION_NAMES = { PORT: 'Port', PLANET: 'Planet', SPACE: 'in Space', SHIPYARD: 'in the shipyard' };

/**
 * Each held good as `{ units, goods, detail }`, read "3 ISO × 4 HT = 12 HT", or "2 FOOD (not traded here)" where the
 * system doesn't trade it.
 */
function goodsLines(goods) {
	return (goods ?? []).map(held => ({
		units: held.units,
		goods: held.goods,
		detail: held.price === null || held.price === undefined ? `(${NOT_TRADED_TEXT})` : `× ${held.price} HT = ${held.value} HT`,
	}));
}

/**
 * The Assets tab for [game]'s viewer, or null when nothing was sent: the summary (cash, ships, goods, factories' output a
 * turn, and their total), then one row per ship, warehouse and factory.
 */
export function assetsView(game) {
	const assets = game.you?.assets;
	if (!assets) {
		return null;
	}
	const money = game.you.money ?? 0;
	return {
		summary: [
			{ label: 'Cash', value: `${money} HT` },
			{ label: 'Ships (sale value)', value: `${assets.shipsValue} HT` },
			{ label: 'Goods (today’s prices)', value: `${assets.goodsValue} HT` },
			{ label: 'Total (cash, ships, goods)', value: `${money + assets.shipsValue + assets.goodsValue} HT` },
			{ label: 'Factories make a turn', value: `${assets.factoryOutputValue} HT` },
		],
		ships: assets.ships.map(ship => ({
			name: ship.name,
			system: ship.system,
			where: `${ship.systemName} · ${LOCATION_NAMES[ship.location] ?? ship.location}`,
			saleValue: `${ship.saleValue} HT`,
			goods: goodsLines(ship.goods),
			goodsValue: `${ship.goodsValue} HT`,
		})),
		warehouses: assets.warehouses.map(warehouse => ({
			systemName: warehouse.systemName,
			system: warehouse.system,
			blocks: `${warehouse.blocks} ${warehouse.blocks === 1 ? 'block' : 'blocks'}`,
			goods: goodsLines(warehouse.goods),
			goodsValue: `${warehouse.goodsValue} HT`,
		})),
		factories: assets.factories.map(factory => ({
			systemName: factory.systemName,
			system: factory.system,
			goods: factory.goods,
			size: `${factory.size} u a turn`,
			output: factory.price === null || factory.price === undefined
				? NOT_TRADED_TEXT
				: `${factory.size} × ${factory.price} HT = ${factory.outputValue} HT`,
		})),
		notes: {
			ships: assets.ships.length ? '' : NO_SHIPS_TEXT,
			warehouses: assets.warehouses.length ? '' : NO_WAREHOUSES_TEXT,
			factories: assets.factories.length ? '' : NO_FACTORIES_TEXT,
		},
	};
}
