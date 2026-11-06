# Execution plan: E04-S04 — Other players see the Ship-jumps phase progress live

Source doc: .ai/specs/2026-10-29-e04-ship-jumps-phase.md
Issue: #45
Stacked on: #84 (E04-S03), base branch `feat/e04-s03-landing` until #83 and #84 merge

## Goal

Picks, jumps and landings by other players reach every screen within a poll, without a refresh (S04-1, D01), for
every player, screen-reader users included. Offers placed during Bidding stay visible to their owner under "Your
next jump" (S04-2).

## What already works (checked before planning)

- **S04-1 on screen**: picks (E04-S01), jumps (S02) and landings (S03) change `turn` and `players[].ships`, which every
  poll redraws: the map, Your fleet, Turn order, the top bar, s02 and the Game log. Seen in the S02 and S03 walks
  (Vega's jump on Ada's map within a poll; Grace finishing → Ada's slot).
- **S04-2**: "Your offers this turn" already sits under "Your next jump" in the right column during Ship-jumps, fed by
  `you.offers`, which stays through the turn (BACKWARD_COMPATIBILITY §6).

## What's missing (this run)

- **Nothing is announced.** No live region says that another player picked, jumped or landed, or that it has become
  your pick or your jumps. A screen-reader user waiting on others gets no signal; the prototype announces these
  ("{corp} finished jumping.", "Waiting for your jumps.").
- **No `.flash` on a polled change** (design system: "A value changed after a poll → add `.flash` once").
- **No test pins S04-2**: nothing checks that the owner's offers are still in `you.offers` after jumps and landings,
  and absent from another player's view.

## Scope

- `static/js/progress.js` (new, pure, unit-tested): what to announce after a poll, from the new public log lines
  since the most this player has seen in this game, plus "It's your turn to choose your place in the turn order." /
  "It's your turn to jump." when it becomes theirs.
- `static/index.html`: a visually hidden polite live region, set only from polls; one `.flash` on Turn order rows a
  poll changed.
- `GameControllerTest`: offers stay their owner's through Ship-jumps (S04-2).

## Non-goals

- Server push or a shorter poll (D01 keeps polling).
- Announcing Bidding or Trading progress beyond what their log lines already say.

## Decisions

- **Announce from the public log.** It already words every pick, jump and landing for everyone (R26, S03-3) and never
  carries anything confidential, so nothing new is exposed.
- **High-water mark, not the previous poll.** The page tracks the most log lines it has shown per game, raised by the
  player's own action responses too, so their own steps and a stale in-flight poll are never announced as news.
- **At most the last three new lines**, so coming back to a busy game isn't a flood; the first load of a game
  announces nothing.
- **Browser walk** (two players; app on 3100, fresh DynamoDB Local on 8100), reading Ada's live region after each
  poll while Grace acted through the API: the first load said nothing; Bidding closing → "Initiative order (bid +
  d2): Vega Lines → Orion Freight."; Grace's pick → her pick, Ada's automatic 2nd and the full order (the two changed
  Turn order rows flashed); Grace's jump and landing → both lines; Grace finishing → "It's your turn to jump.". Ada's
  own jump and landing in the UI were not announced back on the next poll. The live region is visually hidden, so
  the PR evidence is this transcript plus one Deck screenshot.
- **Review** (om-auto-review-pr on #85): approve, no blockers or majors. Three minors fixed in 87f252c: two stacked doc
  comments on `render` merged; the live region cleared when the page switches games (lesson 2026-11-04); the
  region cleared before new text, so identical words in a row are read again. Rechecked in the browser. Self-approval
  isn't possible on the author's own PR, so the review is a comment.

## Risks

- `risk-low`: client-only and additive, apart from one new controller test. Covered by JS unit tests and a browser walk
  reading the live region.

## Implementation Plan

### Phase 1: Web

- 1.1 `progress.js` with unit tests.
- 1.2 Live region and Turn order flash on the Deck.
- 1.3 S04-2 controller test.
- 1.4 Browser walk.

## Progress

PR: #85

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Web

- [x] 1.1 progress.js — 5c89d16
- [x] 1.2 Live region and Turn order flash — 1bfd307
- [x] 1.3 S04-2 controller test — fc2da4d
- [x] 1.4 Browser walk — (walk only, no code)
