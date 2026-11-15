# Run: E06-S02 — Warehouses and factories in the game state, shown on System details

Source doc: .ai/specs/2026-11-15-e06-factories-warehouses.md (on PR #106, branch `spec/e06-factories-warehouses`)
Issue: #100 (epic #98)
Run type: autonomous (`om-auto-fix-issue`, feature route, one of two parallel forks; the other builds E06-S01, #99)
Based on: `origin/spec/e06-factories-warehouses`, because the spec isn't on `main` yet. This PR's diff against `main`
therefore also contains the spec PR's commits until #106 merges.

## Asked to do

Players own warehouses (blocks) and factories (system, good, size). They persist with the game and everyone sees them
on System details; System details also says which goods a system can produce, or that none can. A new game has none.
Nothing here reveals money. Don't touch the phase list (E06-S01's scope).

## What it did

- **Domain** (`game`): `Warehouse(system, blocks)` with `cargoSpaces()` (10 a block, R45); `Factory(system, goods,
  size)`; `Player` gains `warehouses` and `factories` (empty for a newcomer, R40, R50). `StarSystem.producibleGoods`
  (R46): Gamma POLY, Sigma FOOD and ISO, Mu CHIPS, Tau ISO, Beta and Epsilon none.
- **Persistence**: `WarehouseItem`, `FactoryItem`, composed by `PlayerItem`; an older item without them reads as none
  (BACKWARD_COMPATIBILITY.md §7 updated).
- **Web**: `PlayerResponseDto` carries `warehouses` and `factories` (public, no money); `SystemResponseDto` carries
  `producibleGoods` (§6 updated).
- **Frontend**: System details "Warehouses and factories" section (producible goods, a Factories table, a Warehouses
  table, "Nobody has a … here" when empty), `systemHoldings` in `js/board.js`, a small spacing rule in `components.css`.

## Decisions

- Producible goods are a property of `StarSystem`, next to the data they sit with (prices, indicators), not a lookup
  elsewhere.
- Warehouses hold only `blocks` for now. What a warehouse stores (cargo) arrives with E06-S03, which adds its field to
  `Warehouse` and `WarehouseItem` and the round trip.
- No behaviour was added to buy, build or collect: those are S03–S05. Tests and the walk seeded holdings directly.
- The section is folded by default like Market positions; it is not marked open.

## Validation

- `./gradlew build` (ktlint, Kotlin tests with Docker up, JS tests): passes; 547 Kotlin tests, 0 skipped.
- `node --test src/test/js/*.test.mjs`: 202 pass.
- Browser walk (agent-browser, local app on port 3102 with its own DynamoDB Local, `RESEND_API_KEY` blank): new game
  shows no holdings and `producibleGoods` for all six systems; holdings seeded in DynamoDB Local show for Tau Ceti
  and Sigma Draconis (desktop and 390 px wide); Epsilon Eridani shows "No factories here." and both empty lines.
  The first walk showed the Warehouses heading touching the Factories table; fixed with a spacing rule.
- Not done: the `om-auto-review-pr` and `om-auto-qa-pr` stages of the chain.

## Outcome

Done for the story's code and checks; the PR is open for review.
