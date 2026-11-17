# Move goods between ships in the same port, on the Fleet tab

Status: in progress · creator's request, 2026-11-17

## Why

The creator wants goods moved between two of a player's ships in the same port, not only between a ship and the
warehouse there. R58 said "Not ship to ship"; the creator lifts that. With ships involved either way, moving goods
belongs with the ships, so the Move goods form leaves Infrastructure for the Fleet tab, like buying a ship did (D18).

## What changes

- **R58 (revised).** A player may move goods between any two of their own holders in the same port: two of their ships
  in port at the same system, or a ship in port and their warehouse at that system, either way. Everything else in R58
  stands: any time in a running game, whatever the phase; a whole number of units of one good; refused, never lost,
  when the source holds fewer or the destination lacks free cargo space (R42); free, private (R29), not logged.
- **API.** `POST /api/games/{id}/cargo/moves` takes `{ from, to, goods, units }`: `from` and `to` are each a ship name,
  or left out for the warehouse at that port. Replaces `{ ship, goods, units, to: SHIP|WAREHOUSE }`; the only consumer
  is in-repo (`js/cargo.js`), updated in the same PR. New `400`s: "Choose two different ships." (same ship both ends),
  "{A} and {B} aren't in the same port." Missing both ships: "Choose one of your ships." (as before).
- **Fleet tab.** The Move goods panel sits under the ships, above Buy a ship, whenever the game is running. From:
  every ship in port and every warehouse at a port where one of the player's ships is, as long as that port has
  another holder to move to. To: the other holders in the same port. Then Goods and Units, as before. With nothing to
  move between: "None of your ships is in port with another of your ships or your warehouse."
- **Infrastructure.** The panel goes; while the player has a warehouse, a link in its place: "Move goods on the Fleet
  tab". The warehouses table stays. Fleet's old link to Infrastructure goes.
- **Brief.** R58 updated in place with the revision noted; D19 records the form moving to Fleet (refines D12, D18).

## Placement (§13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| What a holder holds, what fits, load/unload, its name in messages | one ship's or warehouse's cargo | 4 | `Ship`, `Warehouse`, through a shared `CargoHold` interface |
| Which ships/warehouse the two ends are; both ships in port, in the same port; a warehouse there | the player's ships and warehouses | 4 | `Player.moveCargo(from, to, goods, units)` |
| Units ≥ 1, source holds enough, destination has room | the two holds | 4 | `Player.moveCargo` via `CargoHold` |
| A move only in a running game, by a member | game status, membership | 7 | `Game.moveCargo` (exists) |
| Request names → domain ends | request JSON | 1 | `MoveCargoRequestDto.from()/to()` → `CargoHolder` |
| Which holders the form offers, hints | `you.ships`, `you.warehouses`, public positions | 9 | `js/cargo.js`, unit-tested; server still decides |

`CargoHolder` (domain): `Warehouse` or `Ship(name)`, the end of a move as the player names it. It replaces
`CargoDestination`.

## Verification

- `PlayerMoveCargoTest`: ship→ship in the same port; refused across ports, same ship, ship not in port, too few, no room.
- `GameControllerTest`: a ship-to-ship move over HTTP.
- `cargo.test.mjs` for the new holder lists.
- `./gradlew check`; browser walk: Fleet shows the form and moves goods between two ships and with the warehouse;
  Infrastructure shows the link.
