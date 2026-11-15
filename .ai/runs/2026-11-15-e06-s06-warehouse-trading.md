# Run: E06-S06 — A warehouse lets you trade without a ship

Source doc: .ai/specs/2026-11-15-e06-factories-warehouses.md (on `main` since #106)
Issue: #104 (epic #98)
Run type: autonomous (`om-auto-fix-issue`, feature route, one of two parallel forks; the other builds E06-S03, #101)
Based on: `origin/main` after #106 (spec), #107 (E06-S02 state) and #108 (E06-S01 Investment phase) merged.

## Asked to do

A warehouse at a system counts as presence there for Trading, including wholesale (R49). The trader chooses which of
their ships and which warehouse load and unload, and may split the amount. A sell is capped by what those hold; POLY
and ISO beyond free space are lost; CHIPS and FOOD fit whenever any ship or warehouse is chosen; with neither a ship
nor a warehouse at the system, bought goods are lost as before (R23). Risk-high: it changes shipped Trading rules (R23, R42).

## What it did

- **Domain**: `Warehouse` gains `cargo` (private to its owner like a ship's) and loads and unloads like a ship
  (`holding`, `unitsThatFit`, `load`, `unload`, R42). It also enforces its own rule: at least one block. `Player`:
  `warehouseAt`, a sell's cap now counts the warehouse (R10, R49), and `trade(…, warehouseUnits)` splits the amount
  across ships and warehouse, adding up to it whenever either exists. With neither, nothing is chosen and a buy is lost (R23).
  `Game.trade`, `Game.wholesale` and `TradingService` pass `warehouseUnits` (default 0, so nothing else changed).
- **Persistence**: `WarehouseItem` stores `cargo`; an item saved before reads as empty (BACKWARD_COMPATIBILITY.md §7).
- **API**: `POST /trading/trade` and `/trading/wholesale` accept an optional `warehouse` (units); `you.warehouses`
  gives the viewer their own warehouses' cargo (`players[].warehouses` stays public without cargo, S02-7, A05).
- **Frontend**: the amount form lists the warehouse after the ships in port, as one more place to split across
  (`js/trading.js`), with warehouse wording in the legend, the loss warning, the "nothing to sell" text and the checks.
- **Docs**: BACKWARD_COMPATIBILITY.md §6 and §7. It also restores the §7 rows for `players[].warehouses` and
  `players[].factories` and the item list in "Required path", which #107's merge with `main` lost (see lesson 2026-11-15).

## Decisions

- Warehouse units travel apart from `ships` (a `warehouse` field), not as a pseudo-ship, so a ship's name can never be
  confused with the warehouse and an old client keeps working unchanged.
- A player has at most one warehouse per system (R45), so the warehouse needs no name in the request.
- New wording (for example "ships and warehouse") appears only when the player has a warehouse there, so no existing
  message or test changed.
- `Warehouse` repeats a few small cargo methods of `Ship` rather than sharing a type with it. Two classes is cheaper than
  rewiring `Ship`, its item and every test; if a third place needs them, extract a cargo type then.
- E06-S02's note said cargo would arrive with S03. It was needed here first; S03 (collecting into a warehouse) can use
  `Warehouse.load` as it is.

## Validation

- `./gradlew build` (ktlint, Kotlin tests with Docker up, JS tests): passes; 591 Kotlin tests, 0 skipped; 225 JS tests.
- New tests: `WarehouseTest`, `WarehouseItemTest`, `PlayerTest` (warehouse splits, caps, losses, refusals),
  `GameTradingTest`, `GamePriceAndWholesaleTest`, `GameResponseDtoTest` (cargo only to its owner), `TradeRequestDtoTest`,
  `GameControllerTest` (a real HTTP trade into a warehouse with no ship there, against DynamoDB Local; another player never
  sees the cargo; a fractional `warehouse` is a 400), and `trading.test.mjs` (the form).

## Not verified

- The amount form was not looked at in a browser: no browser provider was installed in this environment. Its logic is
  covered by the JS tests, and `index.html`'s inline script was not changed.

## Outcome

Done: PR opened (see the issue). Review and QA outcome recorded on the PR.
