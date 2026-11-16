# Run: E06-S04 — Buying warehouse blocks

Source doc: .ai/specs/2026-11-15-e06-factories-warehouses.md
Issue: #102 (epic #98)
Run type: autonomous (`om-auto-fix-issue` feature route → `om-auto-implement-spec` → `om-auto-create-pr`)
Based on: `origin/main` at bf06543 (E06-S08, the Infrastructure screen, is in).

## Goal

After collecting, a player with a ship at a system buys warehouse blocks there at 20 HT each, on the Infrastructure screen.
The blocks are added to their warehouse there, or create it, and first count next turn (R45, R48). Money is taken; a
public Game log line says who added how many where; the screen says that everyone invests at the same time.

## Scope

- **Domain**: `Warehouse.addBlocks` and the cost per block; `Player.buyWarehouseBlocks(system, blocks)` owns the rules
  (a whole number of blocks, 1 or more; a ship of theirs at that system; enough money); `Game.buyWarehouseBlocks`
  coordinates (Investment only; a player with factories must have collected first, S03-5) and logs.
- **Web**: `POST /api/games/{id}/investment/warehouse-blocks` with `{ system, blocks }`; `you.invest` (private to the
  viewer): `{ blockCost, systems: [{ system, systemName, blocks }] }`, the systems where they have a ship and what they
  own there, null outside Investment and while they still have to collect.
- **Frontend** (Infrastructure screen, s10): an "Add warehouse blocks" panel after Collect, one row per system with a
  ship, a number of blocks, the total cost, and the "Everyone invests at the same time…" line while Investment is open.
- **Docs**: BACKWARD_COMPATIBILITY.md §6; run log.

Non-goals: factory units (E06-S05), Game log lines for collect (E06-S07), any stored-field change (`Warehouse.blocks`
already persists).

## Decisions (defaults, not questions)

- "A ship at that system" is any ship of theirs there, in Port, Planet or Space; the spec doesn't narrow it to Port, and
  buying needs no loading. Easy to tighten if the playtest says otherwise.
- Buying after pressing "I'm done investing" is allowed: done is a readiness flag and Investment closes only when
  everyone is done (D09), as Bidding does (R17). The panel stays while Investment is open.

## Risks

- `Player`, `Game` and `Warehouse` are touched by E06-S05 too; this lands first and S05 rebases.
- The screen's wiring in `index.html` has no unit test beyond the syntax check: verify in a browser.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [ ] 1.1 `Warehouse.addBlocks` and `Player.buyWarehouseBlocks`, with tests
- [ ] 1.2 `Game.buyWarehouseBlocks` (Investment only, after Collect, logged) and `InvestmentService`, with tests

### Phase 2: Web

- [ ] 2.1 `POST /investment/warehouse-blocks` and `you.invest`, with tests
- [ ] 2.2 BACKWARD_COMPATIBILITY.md §6

### Phase 3: The panel

- [ ] 3.1 Pure helpers and tests in `js/investment.js`
- [ ] 3.2 "Add warehouse blocks" panel and the shared-Investment line on s10
- [ ] 3.3 Browser walk (two players, 390 px) and run log
