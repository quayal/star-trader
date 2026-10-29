# E02-S05 — Create, join and start a game (screens and start)

**Brief:** `.ai/specs/product-brief.md` — R01, R39, R40, R41, R42, D09, D10.
**Builds on:** E02-S01 (create, `POST /api/games`), E02-S02 (join, `POST /api/games/{id}/join`), E02-S04 (poll, `GET /api/games/{id}`), E01-S03 (sign-in screens). Screen IDs continue the gameplay specs' list (`2026-10-29-e03-bidding-phase.md`).
**Design:** every screen uses `docs/design-system.md`, `static/css/tokens.css` and `components.css` (D10).

## 📝 TLDR

Players need a way into a game. The creator picks a length in turns and creates the game. They share its link, and others join while the game waits in a lobby. When the creator presses **Start game**, joining closes, each player's 2 empty ships are placed at the Ports of 2 different random systems, everyone has 300 HT, and turn 1's Bidding begins. Starting resources and placement move from create/join (E02-S01/S02) to this start step.

## 📝 Screens

| ID | Screen | Route (proposed) |
|---|---|---|
| s11 | Create a game | `/#new` |
| s12 | Lobby — waiting for players | `/#game/{id}` before the start |

After the start, `/#game/{id}` shows the Deck (s01). s10 (sign in) is E01-S03.

## 📋 Acceptance criteria

### Create (s11)

1. [s11] [R41] A signed-in player sees "Create a game" with **Length (turns)**: whole number 1–50 (proposed bounds), default 20. Invalid → inline error with focus: "Enter a whole number of turns from 1 to 50."
2. [s11] [R41] **Create game** creates the game with that length and the creator as its first player, then opens the lobby (s12).
3. [API] `POST /api/games` takes `{ "turns": 20 }`. E02-S01's response gains `turns`, `status` (`LOBBY` | `RUNNING` | `FINISHED`) and `creatorEmail`.

### Lobby and join (s12)

4. [s12] Shows the game's link to copy ("Send this link to the players"), the length ("5 turns"), and the players who have joined (corporation names), with "2 to 6 players".
5. [s12] Someone signed in who opens the link and isn't in the game sees **Join this game**. Joining uses E02-S02 and adds them to the list. Someone not signed in goes to s10 first and comes back to the link afterwards.
6. [s12] [D01] Every lobby polls, so new players appear without a refresh.
7. [s12] [D09] Players other than the creator see "Waiting for {creator corp} to start the game".

### Start

8. [s12] [R41] Only the creator sees **Start game**. With fewer than 2 players it is disabled, with the reason next to it: "At least 2 players are needed to start" (proposed minimum).
9. [—] [R41] Starting closes joining: `POST /api/games/{id}/join` returns `409` with "This game has already started".
10. [—] [R40] At the start every player gets 300 HT and 2 empty ships. Each ship is placed at the Port of a random system; a player's two ships are in different systems. Other players' ships may share a system. Each ship has 10 cargo spaces (R42). Nobody holds a market position.
11. [—] [R41] The game moves to turn 1, Bidding, and every client in the lobby goes to the Deck (s01) on its next poll.
12. [API] `POST /api/games/{id}/start` — creator only (`403` otherwise), `409` if already started or fewer than 2 players. Random placement goes through a port with a fake, so tests can fix it (engineering standards §4).
13. [s04] The first log entries are "Game started: {n} players, {turns} turns" and each ship's starting system: "Starting positions: Orion Freight — Wayfarer at Tau Ceti, Lantern at Beta Hydri; …".

## 📝 States checklist

| State | Screen | Covered by |
|---|---|---|
| Invalid length | s11 | 1 |
| Lobby with only the creator | s12 | 4, 8 |
| Joining via link while signed out | s12 → s10 → s12 | 5 |
| Full game (6 players) | s12 | E02-S02 `409`; show "This game is full" |
| Link to a game that already started, for a non-member | s12 | 9; show "This game has already started" |
| Link to an unknown game | s12 | "This game doesn't exist" with a link to s11 |
| Creator closes the tab before starting | s12 | D09: the lobby just waits |

## 📝 Decisions in play

R39/R41 (length chosen at creation, start by creator), R40 (300 HT, 2 ships, random Ports, empty, no positions), R42 (10 cargo spaces per ship), R01 (games isolated), D09 (no timers), D10 (design system).

## ❓ Open questions

None. Bounds 1–50 for length and a 2-player minimum are proposed defaults.

## 📋 Phasing and dependencies

- Depends on E01-S02 (session), E01-S03 (sign-in screens), E02-S01, E02-S02, E02-S04.
- Changes E02-S01/S02 as noted at the top of those specs: no money or ships at create/join; resources and placement happen at start.
- E03-S01 (turn engine) starts from the `RUNNING` state this story sets.
