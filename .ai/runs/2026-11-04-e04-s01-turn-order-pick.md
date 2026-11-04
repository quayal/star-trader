# Execution plan: E04-S01 — Players pick their place in the turn order

Source doc: .ai/specs/2026-10-29-e04-ship-jumps-phase.md
Issue: #42

## Goal

When Bidding closes, Ship-jumps opens with picking: one at a time, in initiative order, each player picks a free
place (1st…Nth) in this turn's turn order. The last player gets the remaining place without being asked. Each pick
and then the full order are logged, and the Deck shows who holds each place and whose pick it is.

## Scope

- `game` domain: `TurnOrderPick(playerEmail, place)` in `Turn.turnOrderPicks` (public, in pick order, empty until
  someone picks). `Turn.turnOrder()` gives the places as emails or null (empty before Bidding closes);
  `Turn.picker()` gives whose pick it is. `Game.pickTurnOrderPlace` applies the S01 rules, auto-assigns the last
  place (S01-6), and logs the picks and the full order (S01-5).
- `ShipJumpsService.pickPlace` inside `GameStore.update` (atomic, A04, §9).
- DynamoDB: `TurnItem.turnOrderPicks` (new `TurnOrderPickItem`); older items read as none.
- Web: `POST /api/games/{id}/ship-jumps/turn-order` `{ place }` → `200` + the game; `turn.turnOrder` lists the
  places (email or null).
- `static/js/turn-order.js` (new, pure, unit-tested): ordinals, whose pick it is, the top-bar text while picking, the
  Turn order rows, the free places, "You act 2nd this turn."
- `static/index.html`: Deck left column, under the fleet: "Turn order" from Ship-jumps on, with the pick form
  (radios for free places, first pre-selected, **Confirm position**) on your pick. Top bar: "— {corp} is choosing
  their place in the turn order" / "— choose your place in the turn order".
- `BACKWARD_COMPATIBILITY.md` §6 and §7.

## Non-goals

- Jumps, "jumping now"/"done" per slot, "all jumps done", moving to Trading (E04-S02); landing (S03).
- Using the turn order anywhere but Ship-jumps (E05 uses initiative, not turn order).

## Decisions

- Base branch `main` (config `auto`).
- **Picks are stored, places are derived.** `turnOrderPicks` keeps who picked which place in pick order; the
  places list (`turn.turnOrder` in the API) is built from it and the initiative order's size.
- **Whose pick it is** is the first player in initiative order without a place, so it never needs storing. The
  client derives it the same way from `turn.initiative` and `turn.turnOrder`.
- **Refusals**: out of turn → `409` "It's {corp}'s turn to choose a place."; the order is already set → `409` "The
  turn order is already set."; Bidding still open → `409` "Ship-jumps hasn't started."; after Ship-jumps →
  `409` "Ship-jumps has closed"; a missing, fractional, out-of-range or taken place → `400` "Choose a free place in
  the turn order."
- **Log lines** (S01-5): "{corp} chose 2nd"; the auto-assigned last place "{corp} gets 4th, the only place left"
  (S01-6, proposed); then "Turn order (for this turn): A → B → C".
- **Place comes in as a `BigDecimal`**, like `turns`, `price` and `amount`, so 2.5 reaches the domain's message.
- **Steps 1.1–1.3 landed in one commit**: `Turn` gains a field, and §12 makes `TurnItem` fail to compile until it
  reads it (as in E03-S05).
- **After the order is set the phase stays Ship-jumps.** Jumps (S02) take it from there; until S02 ships, a game
  waits there, as it already does today.

- **The pick message stays in the tree** (no `display: none` when empty), so "You act 2nd this turn." is announced
  when it appears, and it takes focus after Confirm, since the form it replaces is gone. It's keyed by game and turn
  (lesson 2026-11-04).
- **Browser walk** (three players, real initiative roll): waiting for Kessler Cargo; Kim's API pick reaching Ada's
  Deck within a poll; Ada picking 2nd in the UI; Vega given 1st; the four log lines; Vega's Deck at 390 px. Seen in
  passing, not from this PR: on the map, a system with three ships clips its marker line (Tau Ceti), a follow-up.

- **Review** (om-auto-review-pr on #82): approve, no blockers or majors. One minor fixed in cdb8fc3: a replayed-update test
  for the pick, as BiddingServiceTest has for every action. Self-approval isn't possible on the author's own PR, so
  the review is a comment.

## Risks

- `risk-medium`: a new stored attribute and a new endpoint with an ordering rule. Covered by domain tests on whose
  pick it is, out-of-turn and taken-place refusals, the auto-assign and the logs; round-trip and older-item tests;
  controller tests for each status. Nothing here is confidential (all public, R26).

## Implementation Plan

### Phase 1: Domain and persistence

- 1.1 `TurnOrderPick`, `Turn.turnOrderPicks`, `turnOrder()`, `picker()` and `Game.pickTurnOrderPlace`, TDD.
- 1.2 `ShipJumpsService.pickPlace`.
- 1.3 `TurnOrderPickItem` and `TurnItem.turnOrderPicks` with the older-item default.

### Phase 2: API

- 2.1 `POST /api/games/{id}/ship-jumps/turn-order` and `turn.turnOrder`, controller tests.
- 2.2 `BACKWARD_COMPATIBILITY.md` §6 and §7.

### Phase 3: Client logic

- 3.1 `turn-order.js` and the picking top-bar text, with tests.

### Phase 4: Screen

- 4.1 Deck: Turn order panel, the pick form, and the top bar.

### Phase 5: Verification

- 5.1 Full gate (`./gradlew build`) and a browser walk with screenshots.

## Progress

PR: #82

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain and persistence

- [x] 1.1 `TurnOrderPick`, `Turn.turnOrderPicks`, `turnOrder()`, `picker()` and `Game.pickTurnOrderPlace`, TDD — da1fa65
- [x] 1.2 `ShipJumpsService.pickPlace` — da1fa65
- [x] 1.3 `TurnOrderPickItem` and `TurnItem.turnOrderPicks` with the older-item default — da1fa65

### Phase 2: API

- [x] 2.1 `POST /api/games/{id}/ship-jumps/turn-order` and `turn.turnOrder`, controller tests — e2cac84
- [x] 2.2 `BACKWARD_COMPATIBILITY.md` §6 and §7 — 2118a47

### Phase 3: Client logic

- [x] 3.1 `turn-order.js` and the picking top-bar text, with tests — 30c3382

### Phase 4: Screen

- [x] 4.1 Deck: Turn order panel, the pick form, and the top bar — dd0a8f6

### Phase 5: Verification

- [x] 5.1 Full gate (`./gradlew build`) and a browser walk with screenshots — dd0a8f6
