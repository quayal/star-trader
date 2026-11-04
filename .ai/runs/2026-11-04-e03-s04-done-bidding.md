# Execution plan: E03-S04 — A player signals done bidding and sees who the game is waiting for

Source doc: .ai/specs/2026-10-29-e03-bidding-phase.md
Issue: #39

## Goal

During Bidding the top bar shows "I'm done bidding" until the player presses it, then "You signalled done — you can
still add offers". The heading names who the game is still waiting for: "Bidding — waiting for you, Vega Lines". When
the last player signals done, Bidding closes for everyone and the game moves to Ship-jumps.

## Scope

- The server side already exists from E03-S01: `Game.signalDoneBidding`, `BiddingService.signalDone`,
  `POST /api/games/{id}/bidding/done`, the public `turn.doneBidding` list and the close on the last signal (S04-4,
  covered by `GameTurnEngineTest`, `BiddingServiceTest`, `GameControllerTest` and `DynamoDbGameStoreTest`).
- Domain: one more test for S04-2. Being done is a readiness flag, so the player can still place an offer after
  signalling done. No code change is expected.
- `static/js/bidding.js` (new, pure, unit-tested): who the game is waiting for, the top-bar phase text, whether
  the viewer is done, and the wording for a refused or closing "done".
- `static/index.html`: the top bar gets the button or the done text (static markup, toggled on each render), and
  the heading names who the game is waiting for.

## Non-goals

- The initiative bid (S05) and the Game log (S06). S04-2's "and the initiative bid" is satisfied once S05 adds a bid
  that, like offers, is checked against the phase only.
- Any change to the API shape: `turn.doneBidding` (emails) and `players[]` are enough.
- Un-signalling done. The spec doesn't ask for it.

## Decisions

- Base branch `main` (config `auto`).
- **"you" first, then the others in join order.** The prototype README's example reads "waiting for you, Vega Lines,
  …", and the viewer is the one most able to act on their own name.
- **Shown in the shared top bar** on s01 and s02, as the prototype does for every top bar. S04 names s01, and showing
  it on System details too keeps the player from having to go back to the Deck to signal done.
- **Found in the browser walk**: `.btn { display: inline-flex }` beat the button's `hidden`, so it stayed on screen
  (the lessons 2026-10-30 gotcha again). Fixed with an explicit `[hidden]` rule; the rule is now in
  `docs/design-system.md`, with a new lessons entry.
- **Heading**: `Turn 4 of 20 · Bidding — waiting for you, Vega Lines`. The money stays in the subtitle.
- **Announcements** (changed while building): the done text "You signalled done — you can still add offers" is
  itself the live region (`role="status"`, always in the tree), so it's announced when it appears. The prototype's
  extra "Marked done for Bidding…" would only have repeated it, so it was dropped. When this player's "done" closes
  Bidding, the bar says "Everyone is done. Bidding has closed." through Ship-jumps. A `409` from a close that raced
  the click is not the player's error: the bar says "Bidding has already closed." and the game reloads. Network and
  other errors show as `.message--error`.
- Static markup, never rebuilt on poll, so focus on the button survives the 2 s poll.
- **Review** (om-auto-review-pr on #74, 6609a34): three minors fixed. The done message is now keyed by game too, the
  shared button is re-enabled when the player left mid-request, and focus moves to the message when this done closes
  Bidding. Open nit: the top bar gets about 16 px shorter when the button goes. Self-approval isn't possible on the
  author's own PR, so the review is a comment and approval is the maintainer's.

## Risks

- `risk-medium`: UI over an existing endpoint. No new confidential data: `doneBidding` was already public (D09).

## Implementation Plan

### Phase 1: Domain

- 1.1 Test: a player who signalled done can still place an offer (S04-2).

### Phase 2: Client logic

- 2.1 `bidding.js`: waiting list, phase text, done state, wording, with tests.

### Phase 3: Screen

- 3.1 Top bar: "I'm done bidding", the done text and the waiting list on s01/s02.

### Phase 4: Verification

- 4.1 Full gate (`./gradlew build`) and a browser walk with screenshots.

## Progress

PR: #74

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [x] 1.1 Test: a player who signalled done can still place an offer (S04-2) — 01e77d4

### Phase 2: Client logic

- [x] 2.1 `bidding.js`: waiting list, phase text, done state, wording, with tests — 12e3fc1, 5fcc1c6

### Phase 3: Screen

- [x] 3.1 Top bar: "I'm done bidding", the done text and the waiting list on s01/s02 — 5fcc1c6

### Phase 4: Verification

- [x] 4.1 Full gate (`./gradlew build`) and a browser walk with screenshots — 5fcc1c6
