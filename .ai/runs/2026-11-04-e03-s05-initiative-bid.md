# Execution plan: E03-S05 — Players bid for turn initiative

Source doc: .ai/specs/2026-10-29-e03-bidding-phase.md
Issue: #40

## Goal

During Bidding each player may place a secret money bid for turn initiative and change it until Bidding closes. When
it closes, every player pays their bid (all-pay, R27), and the initiative order is every player by bid + d2, highest
first, with ties rerolled. From Ship-jumps on, the top bar shows "Initiative: A → B → C"; nobody ever sees another
player's bid or money (R29).

## Scope

- `game` domain: `InitiativeBid` (player, amount) in `Turn.bids` (secret) and `Turn.initiative` (emails in order,
  public, empty during Bidding). `Game.placeInitiativeBid` with the S05-1 rules. Closing Bidding (the last "done")
  charges every bid, rolls the order and logs it.
- A `Dice` port (`d2()`) with `FakeDice` for tests and `RandomDice` in `adapter/random` (§4).
- `BiddingService.placeInitiativeBid` inside `GameStore.update` (atomic, A04), and `signalDone` passes the dice.
- DynamoDB: `TurnItem` stores `bids` (new `InitiativeBidItem`) and `initiative`; older items read as none.
- Web: `PUT /api/games/{id}/bidding/bid` `{ amount }` → `200` + the game. `you.bid` is the caller's own bid this
  turn (or null); `turn.initiative` lists emails in initiative order. Denied-path tests for R29.
- `static/js/initiative.js` (new, pure, unit-tested): bid validation, the bid line, the button label and the
  initiative text.
- `static/index.html`: "Bid for initiative" at the top of the Deck's right column during Bidding; the bid line in
  "Your offers this turn"; "Initiative: A → B → C" in the top bar from Ship-jumps on.
- `BACKWARD_COMPATIBILITY.md` §6 and §7.

## Non-goals

- The Game log screen (S06). The close does add its public log line, "Initiative order (bid + d2): A → B → C"
  (S06-2 names it as E03's event), without any amounts (R29, S06-3).
- What initiative is used for: turn-order pick (E04), trading order and offer tie-breaks (E05).
- Q07 stays open: a player who never bid is treated as bidding 0 and still rolls (S05-4, the spec's default).

## Decisions

- Base branch `main` (config `auto`).
- **Bid amount comes in as a `BigDecimal`**, as `turns` and `price` do, so 2.5 reaches the domain's message instead
  of being rounded. Messages (S05-1): "Enter a whole number of HT, 0 or more." (missing or fractional), "A bid can't
  be negative. Enter 0 to bid nothing.", "You have X HT. Enter X or less."
- **`PUT`, not `POST`**: placing a bid again replaces it (S05-2), so the request is idempotent.
- **Ties**: tied players roll d2 again among themselves, and only those rolls order them; repeat until no tie.
  Rolls are taken in join order, so `FakeDice` tests are deterministic.
- **Closing lives in `advancePhase(dice)`**: Bidding → Ship-jumps always charges the bids and rolls the order, so no
  path can skip it. `GameStore.update` may run the mutation again after a write conflict; it re-rolls, but only one
  result is ever saved.
- **Money goes down by the bid at the close**, not when it's placed, so a changed bid is never charged twice. The
  bid is checked against current money when placed; nothing else changes money during Bidding, so it can't go
  negative.
- **Steps 1.2–1.4 and 2.1 landed in one commit**: §12 makes the new `Turn` fields fail to compile until `TurnItem`
  reads them, and the dice reach `signalDone` through the same signature change.
- **Found in the browser walk**: a bid refused after the close kept its disabled form in the player's *next* game
  at the same turn number, because the key was the turn only. It's now keyed by game and turn. The S03 offer form
  (`closedInTurn`) has the same turn-only key; that's left for a follow-up, out of this story's scope.
- **The bid hint names the money**: "You have 300 HT. Every bidder pays their bid when Bidding closes, win or lose;
  the order is bid + d2." It's the viewer's own money, so R29 holds.
- **Review** (om-auto-review-pr on #76, c42e5de): approve, with no blockers or majors. The offer form's turn-only
  key is filed as #77. Self-approval isn't possible on the author's own PR, so the review is a comment.
- **Bids stay on `Turn` through the turn** (like offers), so "Your offers this turn" can still show the paid bid, and
  the next turn starts with none.

## Risks

- `risk-high`: money (all-pay charge) and a confidential read (R29, S05-6). Covered by domain tests on the charge and
  a denied-path controller test: another player's poll never carries the bid, and money stays in `you`.
- New stored attributes: older turns must read as no bids and no initiative (round-trip + missing-attribute tests).

## Implementation Plan

### Phase 1: Domain

- 1.1 `Dice` port, `FakeDice`, `RandomDice`.
- 1.2 `InitiativeBid`, `Turn.bids`, `Turn.initiative`, `Game.placeInitiativeBid` with the S05-1 rules, TDD.
- 1.3 Closing Bidding: all-pay charge, initiative order with rerolled ties, log line, TDD.
- 1.4 `BiddingService.placeInitiativeBid` and the dice through `signalDone`.

### Phase 2: Persistence and API

- 2.1 `InitiativeBidItem`, `TurnItem.bids` and `TurnItem.initiative` with older-item defaults and round-trip tests.
- 2.2 `PUT /api/games/{id}/bidding/bid`, `you.bid`, `turn.initiative`; denied-path tests.
- 2.3 `BACKWARD_COMPATIBILITY.md` §6 and §7.

### Phase 3: Client logic

- 3.1 `initiative.js`: bid validation, bid line, button label, initiative text, with tests.

### Phase 4: Screens

- 4.1 Deck (s01): "Bid for initiative" panel and the bid line in "Your offers this turn".
- 4.2 Top bar: "Initiative: A → B → C" from Ship-jumps on.

### Phase 5: Verification

- 5.1 Full gate (`./gradlew build`) and a browser walk with screenshots.

## Progress

PR: #76

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [x] 1.1 `Dice` port, `FakeDice`, `RandomDice` — 4c3d81d
- [x] 1.2 `InitiativeBid`, `Turn.bids`, `Turn.initiative`, `Game.placeInitiativeBid` with the S05-1 rules, TDD — e15a2bc
- [x] 1.3 Closing Bidding: all-pay charge, initiative order with rerolled ties, log line, TDD — e15a2bc
- [x] 1.4 `BiddingService.placeInitiativeBid` and the dice through `signalDone` — e15a2bc

### Phase 2: Persistence and API

- [x] 2.1 `InitiativeBidItem`, `TurnItem.bids` and `TurnItem.initiative` with older-item defaults — e15a2bc
- [x] 2.2 `PUT /api/games/{id}/bidding/bid`, `you.bid`, `turn.initiative`; denied-path tests — 74b1b49
- [x] 2.3 `BACKWARD_COMPATIBILITY.md` §6 and §7 — d03d0bc

### Phase 3: Client logic

- [x] 3.1 `initiative.js` with tests — 3676fec

### Phase 4: Screens

- [x] 4.1 Deck (s01): "Bid for initiative" panel and the bid line — 0f5da61
- [x] 4.2 Top bar: "Initiative: A → B → C" from Ship-jumps on — 0f5da61

### Phase 5: Verification

- [x] 5.1 Full gate (`./gradlew build`) and a browser walk with screenshots — 0f5da61, 2a41dde
