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

- [x] 1.1 `ProductionCost`, `Factory.addUnits` and `Player.buyFactoryUnits`, with tests — 976beac
- [x] 1.2 `Game.buyFactoryUnits` (Investment, after Collect, logged) and `InvestmentService`, with tests — 367c73a

### Phase 2: Web

- [x] 2.1 `POST /investment/factory-units` and `you.invest.factories`, with tests — 1b0ecb8
- [x] 2.2 BACKWARD_COMPATIBILITY.md §6 — 1c41cc0

### Phase 3: The panel

- [x] 3.1 Pure helpers and tests in `js/investment.js` — 33ea33e
- [x] 3.2 "Add factory units" panel on s10 — 7a89b4c
- [x] 3.3 Browser walk (two players, 390 px) and run log — 934e437

## Outcome (run log)

Status: complete, pending review and CI.

### What it did

- **Domain**: `ProductionCost` (a unit costs 5 × the good's price + 10 HT, totals as `Long`), `Factory.addUnits`, `Player.buyFactoryUnits(system, goods, units, prices)` owning the rules (1 or more units; the system can produce the good; a ship of theirs there; enough money; adds to the factory or creates it, no cap), `Game.buyFactoryUnits` (Investment only, after Collect, the price read from the game's prices, a public log line without the price), `InvestmentService.buyFactoryUnits` in `GameStore.update` (A04).
- **Web**: `POST /api/games/{id}/investment/factory-units` with `{ system, goods, units }`; `you.invest.factories` = `[{ system, systemName, goods, size, price, unitCost }]` next to `systems`. BACKWARD_COMPATIBILITY.md §6. No stored-field change (`Factory.size` already persists).
- **Frontend** (Infrastructure screen): an "Add factory units" panel after "Add warehouse blocks": a picker of the goods the viewer's systems can produce (with what they make now), a number of units, the total with the unit cost and the good's price as they type, the server's messages. S04's "Everyone invests at the same time…" line is kept, not repeated.

### Decisions

- **A first factory built this turn must not block the builder (R48).** A player with no factory when Investment opened had nothing to collect. If building one made them "have factories" without having collected, `signalDoneInvesting` would refuse them ("Collect your output first.") and `canInvest` would turn false, and collecting would make the new factory produce this turn. `Game.buyFactoryUnits` therefore records them in `turn.collected`, so they can go on, keep buying and are not asked to collect. Tested in the game, service and endpoint tests and in the browser walk. `turn.collected` thus also lists a player who built their first factory this turn.
- `Player.buyFactoryUnits` takes the game's `MarketPrices` and reads the price after the producibility check: prices exist only for goods a system trades, so asking first would fail for a good it doesn't.
- Same defaults as S04: a ship in any location at the system counts; buying after "done" is allowed (D09).

### Validation

- `./gradlew build`: passes, 705 Kotlin tests (0 skipped) and 279 JS tests.
- Tests added: cost, player rules (create, extend, any location, can't produce, no ship, money, no cap, overflow, bad amounts), game flow (current price, log without the price, before and after Collect, the first-factory case, after "done", outside Investment, non-member), service (stored, once under a replaying store, after close), DTO shape and prices, endpoint statuses, JS helpers.
- Browser walk (headless Chrome over DevTools; the jar on its own port with its own DynamoDB Local; two players, ships and Ada's factory seeded with `UpdateItem`, the phase moved to Investment the same way): the panel is absent for Ada before Collect and present at once for Bob; only POLY at Gamma Leporis is offered (Beta Hydri makes nothing); the unit cost is 5 × the price + 10 and the price equals the Deck's; too many units and a fraction show the right errors with focus; 3 units take their cost and the picker says "makes 3 a turn"; Bob gets no collect panel and can still be done; the log line has no price; other players see the factory (public) and no money; Ada extends 6 → 8 after Confirm with no extra cargo this turn; the panel stays after "done" and she can still build; the shared line appears once; no overflow at 390 px; the panel goes when Investment closes. Assertions use `offsetParent`.
- Not seen: a screen reader, a real mouse or touch. The next turn's Collect with the larger factory is covered by the domain rules, not walked.

## Revision: investing needs no ship (creator, 2026-11-16, after the first push)

The first version required a ship at the system for both factory units and, from E06-S04, warehouse blocks. The creator
ruled that investing needs no ship, and no existing factory for factory units, and that a factory is bought from System
and Goods dropdowns like an offer. Changed on this branch, in this order:

- **Spec and brief**: R45 and R46, S04-1/2, S05-1/5, the states checklist, the "Invest" step and the open-questions
  default now say so; the brief's R45 and R46 rows and its 2026-11-16 note too; BACKWARD_COMPATIBILITY.md §6.
- **Domain and web**: the ship check is gone from `Player.buyFactoryUnits` and `Player.buyWarehouseBlocks`;
  `you.invest.systems` lists all six systems and `you.invest.factories` every producible system and good pair, whatever
  the viewer has there (map order: Gamma POLY, Tau ISO, Mu CHIPS, Sigma FOOD, Sigma ISO).
- **Frontend**: the factory panel has a System dropdown (the four systems that produce something) and a Goods dropdown
  that follows it, each good showing what the viewer makes now. Helpers `factorySystems`, `factoriesAt`,
  `goodsOptionLabel`. The warehouse panel's System dropdown now lists all six.
- Tests rewritten where they encoded the ship rule (the "no ship" refusals became "no ship needed" successes).
- Browser walk repeated with Bob having ships only at Beta Hydri and Epsilon Eridani: he built ISO at Sigma Draconis
  and a warehouse at Gamma Leporis with no ship at either; dropdown behaviour, errors, picks surviving polls and
  no overflow at 390 px all pass. `./gradlew build`: 707 Kotlin tests (0 skipped), 281 JS tests.
- E06-S04 (#120, merged) had the ship rule; this PR corrects it. #102 is closed, so its change is noted there by comment.
