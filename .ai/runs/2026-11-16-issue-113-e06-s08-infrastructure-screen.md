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

- [x] 2.1 Nav item, panel markup and routing for s10; the Collect panel moves into it — 5d79e6b
- [x] 2.2 The done controls move into s10; the top bar keeps Bidding's and links to s10 while collecting — 5d79e6b
- [x] 2.3 Redirect when Investment starts; read-only view outside Investment; own warehouses and factories — 5d79e6b

### Phase 3: Verify and document

- [x] 3.1 Browser walk (two players, seeded factories, 390 px) and run log — 71b7d06

## Outcome (run log)

Status: complete, pending review and CI.

### What it did

- **Routing and helpers**: `#game/{id}/infrastructure` (s10) and `infrastructureLink` in `js/game.js`; `investmentJustStarted`, `infrastructureState`, `infrastructureStatusText`, `ownWarehouseRows` and `ownFactoryRows` in `js/investment.js`, all with unit tests.
- **Screen**: a nav item "Infrastructure" beside Trading, and one panel holding the Collect panel (moved off the Deck, same behaviour), a status line, the "I'm done investing" button (moved off the top bar), and the viewer's own warehouses and factories. Outside Investment it says "Investment opens after Trading." and shows no controls.
- **Redirect**: every player is taken to s10 when Investment starts (once; going back to the Deck sticks).
- **Top bar**: keeps Bidding's button; on the Deck and System details a player who still has to collect sees "Collect your output first." with a link to s10.
- No server change, no new endpoint or stored field: `you.warehouses`, `you.collect`, `players[].factories` and `turn.doneInvesting` carry everything.

### Decisions

- The Investment done button, its message and focus handling are a separate small path (`signalDoneInvesting`) instead of one shared `signalDone` with a switch: the two have different elements, messages and focus targets now.
- After Investment closes the next turn is in Bidding and the player **stays on s10** (read-only, "Investment opens after Trading."); nothing in the spec asks for a redirect to the Deck, and Trading/Game log behave the same. A follow-up could take players back to the Deck when Bidding opens.
- The viewer's own factories are read from the public `players[]` entry by email; system names come from `board`.

### Validation

- `./gradlew build`: passes, 637 Kotlin tests (0 skipped) and 258 JS tests.
- Browser walk (headless Chrome over the DevTools protocol, since agent-browser isn't installed; the jar on port 3141 with its own DynamoDB Local; two players, Ada's factory and warehouse seeded in DynamoDB Local with `UpdateItem`, the phase moved to Investment the same way): taken to s10 when Investment starts with the heading focused; Collect panel on s10 and absent from the Deck; "Collect your output first." with the link on the Deck; Deck stays put across polls; Confirm stores 2 ISO beside 8 POLY and the done button appears with focus; the other player (no factories) sees the button at once and none of Ada's warehouse; done names who is awaited; closed state is read-only with the assets listed; Bidding's button still on the Deck; no horizontal overflow at 390 px.
- The walk's first run showed a real bug: a container rule `display: grid` beat `[hidden]` so the Collect panel stayed visible after Confirm while `.hidden` was true. Fixed (`.panel[hidden]`), and the walk now asserts `offsetParent`. Lesson added.
- Not seen: a screen reader, a real mouse or touch.
