/*
 * Pure helpers for the Assets tab (creator, 2026-11-18): where the signed-in player's money sits. Everything comes from
 * `you.assets`, private to the viewer (R29), with every value worked out by the server: ships at what they would fetch
 * sold (R61); goods, by type and where each lot is, at the good's mean price across the systems that trade it today, and
 * factories by one turn's output at that mean price. The ships themselves are on the Fleet tab.
 */

export const NO_GOODS_TEXT = 'You hold no goods.';
export const NO_WAREHOUSES_TEXT = 'You have no warehouses.';
export const NO_FACTORIES_TEXT = 'You have no factories.';
export const NOT_TRADED_TEXT = 'traded nowhere';

/** "12 HT avg", or "traded nowhere" for a good no system trades (its mean is then 0). */
function averageText(averagePrice) {
	return averagePrice ? `${averagePrice} HT avg` : NOT_TRADED_TEXT;
}

/**
 * The Assets tab for [game]'s viewer, or null when nothing was sent: the summary (cash, ships, goods, their total, and
 * the factories' output a turn), then one card per good with a line per ship or warehouse holding it, then the
 * warehouses and factories.
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
			{ label: 'Goods (mean prices)', value: `${assets.goodsValue} HT` },
			{ label: 'Total (cash, ships, goods)', value: `${money + assets.shipsValue + assets.goodsValue} HT` },
			{ label: 'Factories make a turn', value: `${assets.factoryOutputValue} HT` },
		],
		goods: assets.goods.map(stock => ({
			goods: stock.goods,
			units: `${stock.units} u`,
			value: `${stock.value} HT`,
			price: averageText(stock.averagePrice),
			lots: stock.lots.map(lot => ({
				units: `${lot.units} u`,
				ship: lot.ship,
				system: lot.system,
				systemName: lot.systemName,
				holder: lot.ship ?? 'Warehouse',
			})),
		})),
		warehouses: assets.warehouses.map(warehouse => ({
			systemName: warehouse.systemName,
			system: warehouse.system,
			blocks: `${warehouse.blocks} ${warehouse.blocks === 1 ? 'block' : 'blocks'}`,
		})),
		factories: assets.factories.map(factory => ({
			systemName: factory.systemName,
			system: factory.system,
			goods: factory.goods,
			size: `${factory.size} u a turn`,
			output: factory.averagePrice
				? `${factory.size} × ${factory.averagePrice} HT avg = ${factory.outputValue} HT`
				: NOT_TRADED_TEXT,
		})),
		notes: {
			goods: assets.goods.length ? '' : NO_GOODS_TEXT,
			warehouses: assets.warehouses.length ? '' : NO_WAREHOUSES_TEXT,
			factories: assets.factories.length ? '' : NO_FACTORIES_TEXT,
		},
	};
}
