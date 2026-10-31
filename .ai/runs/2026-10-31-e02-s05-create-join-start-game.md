# Execution plan: E02-S05 — create, join and start a game (screens and start)

Source doc: .ai/specs/2026-10-29-e02-s05-create-join-start-game.md
Issue: #35

## Goal

A signed-in player creates a game on "Create a game" (s11), shares the lobby link (s12),
others join from it, the lobby updates by polling, and the creator starts the game: joining
closes, every player gets 300 HT and 2 empty ships at the Ports of 2 different random
systems, turn 1 Bidding begins, the start is logged, and every lobby moves to the game view.

## Scope

- `game` domain: `Ship`, `ShipLocation`, `Turn`, `TurnPhase`; `Player` gains `money` and
  `ships`; `Game` gains `turn` and `log` and a `start` rule; a `StartingSystemPicker` port with
  a fake and a random adapter; `GameStartingService`; `NotTheCreatorException`.
- `game` web adapter: `POST /api/games/{id}/start`; game responses gain each player's ships,
  the turn, the log, and a `you` block with the caller's own money (R29).
- `static/index.html` + a new `static/js/game.js` (pure helpers, unit-tested): `/#new` (s11),
  `/#game/{id}` (s12 lobby, then the game view after the start).
- `BACKWARD_COMPATIBILITY.md` §6.

## Non-goals

- The real Deck (map, phase panels; E03-S02), the Game log screen (E03-S06), the turn engine
  beyond setting turn 1 Bidding (E03-S01). The post-start view shows only what exists now:
  turn, your money, your fleet, the log, and the existing market overview.
- Ship cargo (E05). Ships start empty, so no cargo field is added yet; capacity is the R42
  constant (10).
- Hiding emails from the API (open question, see below).
- Criterion 14 (manual ngrok pre-playtest run) — a check for the user, listed as outstanding.

## Decisions and deviations, recorded

- **Money is private (R29).** Players' money is never in `players[]`; the response carries
  `you: { money }` for the caller only. Ship names and positions are public (the start log
  announces them, criterion 13).
- **Ship names** come from a fixed list of 12 in join order (2 per player, at most 6
  players): Wayfarer, Lantern, Meridian, Halcyon, Bright Reach, Corvid, Ironside, Tallow,
  Ostrava, Kestrel, Solace, Pilgrim (the first nine are the prototype's). Players don't name
  ships anywhere in the spec.
- **Star systems:** `game` uses `trading.StarSystem` (the board's six systems, a public enum
  of `trading`) rather than a second copy of the board.
- **Lobby players** are created with 0 money and no ships via `Player.newcomer(...)` — a named
  factory, not default values in the data class.
- **Start rules live in `Game.start`**, run inside the atomic `GameStore.update`, with the
  picker passed in, so a join racing the start can't leave a player unplaced. Order of checks:
  not the creator → `403` "Only the creator can start the game"; started → `409` "This game
  has already started"; fewer than 2 → `409` "At least 2 players are needed to start".
- **Non-member opening a lobby link:** `GET` is `403` for non-members (E02-S04), so the lobby
  shows only **Join this game** to them; a started game answers the join with E02-S02's
  `409` "This game has already started", shown as is.
- **Lobby shows corporation names only.** Emails still come back from the API; whether they
  should reach the browser at all is left as an open question on the PR.
- **Polling interval** 2 s (D01's 1–2 s target), only while a game view is open.

## Implementation Plan

### Phase 1: Domain

- 1.1 `Ship`, `ShipLocation`, `Turn`, `TurnPhase`; `Player` gains money and ships; `Game` gains turn and log
- 1.2 `StartingSystemPicker` port, fake, and random adapter with tests
- 1.3 `Game.start` (creator only, lobby only, ≥ 2 players, 300 HT, placement, turn 1 Bidding, log) with tests
- 1.4 `GameStartingService` with tests

### Phase 2: Web

- 2.1 Viewer-scoped game response: ships, turn, log, `you.money`
- 2.2 `POST /api/games/{id}/start` with controller tests
- 2.3 Record the changes in `BACKWARD_COMPATIBILITY.md` §6

### Phase 3: Screens

- 3.1 `js/game.js` helpers (route, length validation, lobby view model) with JS tests
- 3.2 s11 Create a game at `/#new`
- 3.3 s12 lobby: link, players, join, polling, start
- 3.4 Game view after the start: turn, money, fleet, log

### Phase 4: Integrate and gate

- 4.1 Merge latest main; extend the DynamoDB mapper if E02-S03 has landed, else note it
- 4.2 `./gradlew build` green and UI checked in a browser

### Phase 5: Follow-up — "Your games" (resumed 2026-10-31, user-approved design)

The user found that clicking "New game" or "Deck" leaves a lobby with nothing leading back.
Browser-side only, no server change: every game the player opens as a member (a `200` from
`GET /api/games/{id}`) is remembered in `localStorage` (`starTrader.games`, most recent first,
at most 10, every access in try/catch). "Deck" (`/`) opens the most recent remembered game when
there is one. The New game screen lists "Your games" (status, length, corporation names, a link
to `/#game/{id}`), fetching each with the existing `GET`; an id that answers `403` or `404` is
dropped, which also covers a second player using the same browser. No games: the section is hidden.
Persistence: the user chose merge order #66 first; #65 will then map money, ships, turn and log,
so this PR needs no mapper work.

- 5.1 `js/your-games.js` storage helpers (remember, dedupe, cap, forget, storage blocked) and row text, with JS tests
- 5.2 "Your games" on the New game screen and Deck opening the latest game
- 5.3 Gate, browser QA and evidence for the new flow

## Risks

- `risk-high`: the response now splits public and private data (R29); controller tests must
  prove another player's money never appears.
- Overlap with E02-S03 (#21), running in parallel: whichever lands second maps the new fields.

## Outcome

Done; PR #66 is ready for review. All 13 steps are done, and the gate is green (144 Kotlin tests, 37 JS).
Every flow was driven in a real browser with three signed-in players, and 9 screenshots are on the PR.
The review (a comment, since the agent doesn't approve its own PR) found two minor issues, both fixed in
8a8e63d: duplicate poll loops after reopening a game, and the Start button's disabled reason not announced.
E02-S03 (#65) was still open, so mapping the new fields is left to whichever PR lands second; noted on
the PR. Still with the user: approval (risk-high), manual QA, criterion 14 (ngrok run) and the email
question. While posting the screenshots, zsh's `path` variable wiped `$PATH`; recorded in `.ai/lessons.md`.

## Outcome of the "Your games" follow-up (resumed run)

Done. Added `js/your-games.js` with 7 JS tests, the "Your games" list on New game, and Deck reopening
the latest game. The self-review found one major issue, fixed in this run: with another player's games still
remembered in the browser, Deck jumped to one and offered "Join this game". Deck now checks membership with
GET first. Browser QA covered 7 checks, plus the re-check after the fix; screenshots are on the PR. Persistence:
the user chose merge order #66 first, so #65 maps the new fields.

## Progress

PR: #66

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [x] 1.1 `Ship`, `ShipLocation`, `Turn`, `TurnPhase`; `Player` gains money and ships; `Game` gains turn and log — 2546afc
- [x] 1.2 `StartingSystemPicker` port, fake, and random adapter with tests — 6033464
- [x] 1.3 `Game.start` (creator only, lobby only, ≥ 2 players, 300 HT, placement, turn 1 Bidding, log) with tests — 8bc0efc
- [x] 1.4 `GameStartingService` with tests — 2980cb4

### Phase 2: Web

- [x] 2.1 Viewer-scoped game response: ships, turn, log, `you.money` — cefa719
- [x] 2.2 `POST /api/games/{id}/start` with controller tests — e06c704
- [x] 2.3 Record the changes in `BACKWARD_COMPATIBILITY.md` §6 — 9efc02d

### Phase 3: Screens

- [x] 3.1 `js/game.js` helpers (route, length validation, lobby view model) with JS tests — e0438fa
- [x] 3.2 s11 Create a game at `/#new` — c1710a6
- [x] 3.3 s12 lobby: link, players, join, polling, start — c1710a6
- [x] 3.4 Game view after the start: turn, money, fleet, log — c1710a6

### Phase 4: Integrate and gate

- [x] 4.1 Merge latest main; extend the DynamoDB mapper if E02-S03 has landed, else note it — no-op: main unchanged, E02-S03 still open as #65
- [x] 4.2 `./gradlew build` green and UI checked in a browser — cb50aef

### Phase 5: Follow-up — "Your games"

- [x] 5.1 `js/your-games.js` storage helpers (remember, dedupe, cap, forget, storage blocked) and row text, with JS tests — 247e878
- [x] 5.2 "Your games" on the New game screen and Deck opening the latest game — cef781f
- [x] 5.3 Gate, browser QA and evidence for the new flow — cef781f (gate green: 144 Kotlin, 44 JS; browser QA 7 checks)
