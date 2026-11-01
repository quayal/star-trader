# Execution plan: E03-S01 — a game runs turns of three phases

Source doc: .ai/specs/2026-10-29-e03-bidding-phase.md
Issue: #36

## Goal

A running game is always in one turn and one phase, Bidding → Ship-jumps → Trading → the next
turn's Bidding, and never moves on by a clock (D09). Bidding closes the moment the last player
signals done; a done signal that races the close is either applied or rejected with "Bidding has
closed", never applied twice or lost (A04). Every client sees the new phase on its next poll.

## Scope

- `game` domain: `TurnPhase` gains `SHIP_JUMPS` and `TRADING`; `Turn` gains `doneBidding` (emails of
  players who signalled done, public per S04-3); `Game.advancePhase()` (the cycle, and after the last
  turn's Trading the game is `FINISHED`); `Game.signalDoneBidding(email)` (idempotent; closes Bidding
  when everyone is done); `PhaseClosedException`; `BiddingService`.
- `game` web adapter: `POST /api/games/{id}/bidding/done`; `turn` in the game shape gains `doneBidding`.
- `game` DynamoDB adapter: `TurnItem` stores `doneBidding`, defaulting to empty for older items.
- `static/js/game.js`: phase names for Ship-jumps and Trading (the top bar already reads `turn.phase`
  and the game view already polls every 2 s, which covers S01-5).
- `BACKWARD_COMPATIBILITY.md` §6 and §7.

## Non-goals

- The "I'm done bidding" button and "waiting for …" names (E03-S04), offers (S03), initiative bids
  and the roll at close (S05), the Game log screen (S06), the Deck/System details (S02).
- What ends Ship-jumps and Trading: E04 and E05 call `advancePhase()`; this story only gives them the
  cycle. No endpoint advances those phases yet.
- The Game over screen (s09, E05-S08). Finishing the game after the last turn's Trading is in the
  engine so that "Turn N of M" means something; the screen is not.

## Decisions

- Base branch `main`, not the config's `auto`: `origin/auto` doesn't exist and every E02 PR targeted
  `main`. The config should be corrected in its own change.
- `advancePhase()` is the only way a phase changes, and it runs inside `GameStore.update`, so the
  optimistic version check makes every transition atomic (standards §9). No scheduler (D09).
- Done is a flag, not a commit (R17): a repeated signal from a player already done is a no-op, so a
  retried request can't double-close. A signal after the close is rejected with `409` "Bidding has
  closed", so the player is told (A04).

## Implementation Plan

### Phase 1: Turn engine in the domain

1. `TurnPhase` gains `SHIP_JUMPS`, `TRADING`; `Turn` gains `doneBidding`; `Game.advancePhase()` with
   tests for the full cycle and the finish after the last turn.
2. `Game.signalDoneBidding(email)` + `PhaseClosedException` with tests: idempotent, closes on the last
   player, rejected when not running, not a member, or after the close.
3. `BiddingService.signalDone` with fake-store tests, including the replayed-mutate case.

### Phase 2: Adapters

1. `TurnItem` stores `doneBidding` (empty for older items) with its round-trip test.
2. `POST /api/games/{id}/bidding/done` and `turn.doneBidding` in the response, with controller tests
   (200, 401, 403, 404, 409) and a concurrent "everyone signals done at once" test on DynamoDB Local.
3. Frontend phase names + JS unit test.
4. `BACKWARD_COMPATIBILITY.md` §6 and §7.

### Phase 3: Verify

1. Full gate `./gradlew build` and `./gradlew jsTest`; play a game through the API locally and
   screenshot the phase in the top bar.

## Risks

- Adding a stored attribute: older items have no `doneBidding`; `TurnItem` defaults it (§12, BC §7).
- The concurrent close test relies on DynamoDB Local (Testcontainers); skipped locally without Docker,
  always run in CI.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Turn engine in the domain

- [ ] 1.1 Phases and advancePhase
- [ ] 1.2 signalDoneBidding and PhaseClosedException
- [ ] 1.3 BiddingService

### Phase 2: Adapters

- [ ] 2.1 TurnItem stores doneBidding
- [ ] 2.2 Done-bidding endpoint and response field
- [ ] 2.3 Frontend phase names
- [ ] 2.4 Backward-compatibility notes

### Phase 3: Verify

- [ ] 3.1 Full gate and local play-through
