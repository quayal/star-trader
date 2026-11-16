# Run: E06-S05 — Building or extending a factory

Source doc: .ai/specs/2026-11-15-e06-factories-warehouses.md
Issue: #103 (epic #98)
Run type: autonomous (`om-auto-fix-issue` feature route → `om-auto-implement-spec` → `om-auto-create-pr`)
Based on: `origin/main` at edcfe5b (E06-S04 and the Infrastructure screen are in).

## Goal

After collecting, a player with a ship at a system that can produce a good adds units to a factory there on the
Infrastructure screen, for N × (5 × the good's current price there + 10) HT. The factory's size grows by N, or it is
created at N; it first produces next turn (R46, R48). A public log line says who added how many units of what where.

## Scope

- **Domain**: `ProductionCost` (the price of a unit and a total, from a good's current price), `Factory.addUnits`;
  `Player.buyFactoryUnits(system, goods, units, price)` owns the rules (1 or more units; the system can produce the
  good; a ship of theirs there; enough money); `Game.buyFactoryUnits` coordinates (Investment only, after Collect, the
  price read from the game's prices, logged without the price) and `InvestmentService` runs it in `GameStore.update`.
- **Web**: `POST /api/games/{id}/investment/factory-units` with `{ system, goods, units }`; `you.invest.factories`
  next to `systems`: one entry per good the viewer can build at a system where they have a ship, with its `size` now,
  the market `price` and the `unitCost`.
- **Frontend** (s10): an "Add factory units" panel after "Add warehouse blocks": a picker of those goods, a number of
  units, the total as they type, the server's messages. The "Everyone invests at the same time…" line stays as S04 left it.
- **Docs**: BACKWARD_COMPATIBILITY.md §6; run log.

Non-goals: Game log lines for collect (E06-S07), selling or scrapping, any stored-field change (`Factory.size` already
persists).

## Decisions (defaults, not questions)

- Same rules as S04 where the spec is silent: a ship in any location at the system counts; buying after "I'm done
  investing" is allowed (done is a readiness flag, D09).
- The price used is the one in the game's prices when the purchase is applied (it can't change during Investment, S05-2).

## Risks

- `Player`, `Game`, `GameDtos.kt` and the s10 panel are shared with S04's code: extend, don't restructure.
- The wiring in `index.html` has no unit test beyond the syntax check: verify in a browser.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [ ] 1.1 `ProductionCost`, `Factory.addUnits` and `Player.buyFactoryUnits`, with tests
- [ ] 1.2 `Game.buyFactoryUnits` (Investment, after Collect, logged) and `InvestmentService`, with tests

### Phase 2: Web

- [ ] 2.1 `POST /investment/factory-units` and `you.invest.factories`, with tests
- [ ] 2.2 BACKWARD_COMPATIBILITY.md §6

### Phase 3: The panel

- [ ] 3.1 Pure helpers and tests in `js/investment.js`
- [ ] 3.2 "Add factory units" panel on s10
- [ ] 3.3 Browser walk (two players, 390 px) and run log
