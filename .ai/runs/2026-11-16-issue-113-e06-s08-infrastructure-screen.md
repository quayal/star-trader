# Run: E06-S08 — An Infrastructure screen for the whole Investment phase

Source doc: .ai/specs/2026-11-15-e06-factories-warehouses.md
Issue: #113 (epic #98)
Decision: D12 in `.ai/specs/product-brief.md`
Run type: autonomous (`om-auto-fix-issue` feature route → `om-auto-implement-spec` → `om-auto-create-pr`)
Based on: `origin/main` at 0fd63bb (E06-S03 and the S08 spec are in).

## Goal

All of Investment is on one **Infrastructure** screen (s10): the Collect panel (moved off the Deck), the "Done investing"
button, and the player's own warehouses and factories, read-only the rest of the game. Everyone is taken there when
Investment starts. The Deck carries no Investment controls.

## Scope

Frontend only: `index.html`, `js/game.js`, `js/investment.js` and their JS tests. No new endpoint or stored field:
`you.warehouses`, `you.collect`, `players[].factories` and `turn.doneInvesting` already carry everything (S03).

Non-goals: the buying panels (E06-S04, E06-S05) and the "Everyone invests at the same time" line they add; Game log
lines (S07); any server change.

## Implementation Plan

- **Routing helpers** (`js/game.js`): route `#game/{id}/infrastructure` → view `infrastructure`; `infrastructureLink(id)`.
- **Investment helpers** (`js/investment.js`): `investmentJustStarted(previous, game)` (as `tradingJustStarted`), rows for
  the viewer's own warehouses and factories, and the top bar's "collect first" line.
- **Markup** (`index.html`): nav item "Infrastructure" beside Trading; an `infrastructure-panel` holding the moved Collect
  panel, the done controls, "Investment opens after Trading" outside Investment, and the owned warehouses/factories.
- **Wiring**: showView/route/aria-current/focus, the redirect when Investment starts (once, never pulling back), the
  render branch; the top bar stops carrying the Investment done button and links to the screen while the player has to
  collect; the Deck loses the Collect panel.
- **Docs**: `docs/design-system.md` mention if a pattern is new; run log.

## Risks

- The done button, its message and focus handling move from the shared top bar: keep Bidding unchanged.
- `index.html` is large and its module script isn't covered by unit tests beyond a syntax check, so verify in a browser
  (seeded factories per PR #110's QA note).

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Pure helpers and tests

- [x] 1.1 Route and link for the Infrastructure screen in `js/game.js`, with tests — 06a3bee
- [x] 1.2 `investmentJustStarted`, owned warehouse and factory rows, and the collect-first line in `js/investment.js`, with tests — 49cc650

### Phase 2: The screen

- [ ] 2.1 Nav item, panel markup and routing for s10; the Collect panel moves into it
- [ ] 2.2 The done controls move into s10; the top bar keeps Bidding's and links to s10 while collecting
- [ ] 2.3 Redirect when Investment starts; read-only view outside Investment; own warehouses and factories

### Phase 3: Verify and document

- [ ] 3.1 Browser walk (two players, seeded factories, 390 px) and run log
