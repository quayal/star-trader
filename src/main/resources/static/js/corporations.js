/*
 * Pure helpers for the Corporations tab (E11-S01, D20): every public fact about every player in one place. Built only
 * from the public `players[]` (ships' names and positions R57, market positions R28, warehouses and factories R50,
 * connections R81), never from `you`, so money (R29) and a ship's hull, modules, crew and cargo (R57) can't show up here.
 */

import { SHIPYARD, LOCATIONS, TIERS } from './board.js';
import { eliminatedText } from './control.js';
import { reputationText } from './reputation.js';

/** What a card shows for a section with nothing in it. */
export const NO_TEXT = {
	ships: 'No ships',
	marketPositions: 'No market positions',
	warehouses: 'No warehouses',
	factories: 'No factories',
	agents: 'No agents',
};

const LOCATION_NAMES = Object.fromEntries([...LOCATIONS, SHIPYARD].map(location => [location.id, location.name]));

/**
 * One card per corporation, in the order players joined, with the viewer's ([viewerId]) first and marked `you`. Each
 * card has its reputation (`27 / 40`, R85), its connections (`Political 3 · Business 4 · Criminal 0`, and `5 points to place` or null), its ships (`Wayfarer — Tau Ceti, Port`, by name), market positions (`{ tier, goods, systemName }`, Dictator
 * first, then by system and good), warehouses (`Tau Ceti — 3 blocks, 30 cargo spaces`) and factories (`{ goods,
 * systemName, text }`, the text following the coloured good: ` at Sigma Draconis — 4 u per turn`). An empty section is
 * an empty list; [NO_TEXT] says what to show instead.
 */
export function corporationCards(game, viewerId) {
	const systemName = id => game.board?.find(system => system.id === id)?.name ?? id;
	const players = game.players ?? [];
	const ordered = [...players.filter(player => player.id === viewerId), ...players.filter(player => player.id !== viewerId)];
	return ordered.map(player => ({
		id: player.id,
		corporationName: player.corporationName,
		playerName: player.name,
		you: player.id === viewerId,
		ships: (player.ships ?? [])
			.slice()
			.sort((a, b) => a.name.localeCompare(b.name))
			.map(ship => `${ship.name} — ${ship.systemName}, ${LOCATION_NAMES[ship.location] ?? ship.location}`),
		marketPositions: (player.marketPositions ?? [])
			.map(position => ({ rank: TIERS.findIndex(tier => tier.id === position.tier), position, systemName: systemName(position.system) }))
			.sort((a, b) => a.rank - b.rank || a.systemName.localeCompare(b.systemName) || a.position.goods.localeCompare(b.position.goods))
			.map(({ position, systemName: name }) => ({
				tier: TIERS.find(tier => tier.id === position.tier)?.name ?? position.tier,
				goods: position.goods,
				systemName: name,
			})),
		warehouses: (player.warehouses ?? [])
			.map(warehouse => ({ name: systemName(warehouse.system), warehouse }))
			.sort((a, b) => a.name.localeCompare(b.name))
			.map(({ name, warehouse }) =>
				`${name} — ${warehouse.blocks} ${warehouse.blocks === 1 ? 'block' : 'blocks'}, ${warehouse.cargoSpaces} cargo spaces`),
		factories: (player.factories ?? [])
			.map(factory => ({
				goods: factory.goods,
				systemName: systemName(factory.system),
				text: ` at ${systemName(factory.system)} — ${factory.size} u per turn` + (factory.disabledTurns ? `, disabled, ${factory.disabledTurns} more ${factory.disabledTurns === 1 ? 'turn' : 'turns'}` : ''),
			}))
			.sort((a, b) => a.systemName.localeCompare(b.systemName) || a.goods.localeCompare(b.goods)),
		eliminated: eliminatedText(player),
		reputation: reputationText(player.reputation),
		connections: connectionsText(player.connections),
		connectionPoints: connectionPointsText(player.connectionPoints),
	}));
}

/** E12-S01-10: "Political 3 · Business 4 · Criminal 0" (R81); every player has all three, 0 until placed. */
export function connectionsText(connections) {
	const level = kind => connections?.[kind] ?? 0;
	return `Political ${level('political')} \u00b7 Business ${level('business')} \u00b7 Criminal ${level('criminal')}`;
}

/** "5 points to place" while a player still has starting points to place (R82), else null. */
export function connectionPointsText(points) {
	return points > 0 ? `${points} ${points === 1 ? 'point' : 'points'} to place` : null;
}
