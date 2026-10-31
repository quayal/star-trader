# Execution plan: E02-S04 — poll a game's state

Source doc: .ai/specs/2026-10-25-e02-s04-poll-game-state.md
Issue: #23

## Goal

A signed-in member of a game can read its current shared state with
`GET /api/games/{id}` (D01 polling). Anyone who isn't a member gets nothing about the
game: the session → player → game check A05 asks for, built before any confidential
field exists.

## Scope

- `game` domain: `Game.hasPlayer(email)`, a `NotAGameMemberException` and a
  `GamePollingService` that finds the game (`GameNotFoundException` when absent) and
  refuses a caller who isn't one of its players.
- `game` web adapter: the read endpoint on `GameController`, with tests covering every
  denied path (`401`, `404`, `403`) and the allowed ones (creator and joined player).
- `BACKWARD_COMPATIBILITY.md` §6: the new row.

## Non-goals

- Money, ships and the start step (E02-S05, #35); the lobby screen that polls (E02-S05).
- Per-player confidential state and the "see only your own" half of A05 (E03).
- Persistence (E02-S03, #21; running in parallel, adapter-only).
- Any change to `GameStore`, its adapters, `trading` or `login`.

## Decisions and deviations from the spec, recorded

- **Response shape follows E02-S05, not the spec's "money and ships".** Players have
  neither until the start step, so `200` returns the existing `GameResponseDto`
  (`id`, `turns`, `status`, `creatorEmail`, `players[email, name, corporationName]`),
  the same shape create and join return.
- **The membership rule lives in the domain.** `GamePollingService` decides 404 vs 403;
  the controller only maps exceptions to statuses (business rules in domain services).
- **`403` body is the plain message "You are not a player in this game"**, which names
  nothing about the game (spec Q2: no game data for a non-member). `404` reuses
  "This game doesn't exist".
- **`404` before `403`**, as the spec's edge cases say. Game ids are random UUIDs, so
  telling a non-member that an id exists reveals nothing they could guess.

## Implementation Plan

### Phase 1: Domain

- 1.1 `Game.hasPlayer`, `NotAGameMemberException` and `GamePollingService`, with tests.

### Phase 2: Web

- 2.1 `GET /api/games/{id}` on `GameController`, with `GameControllerTest` (risk-high denied-path evidence).
- 2.2 Record the endpoint in `BACKWARD_COMPATIBILITY.md` §6.

### Phase 3: Gate

- 3.1 `./gradlew build` green.

## Risks

- `risk-high` (data scoping): the `403` test for a signed-in non-member is required
  evidence, together with a joined (non-creator) member being allowed, so the check is
  proven to be membership, not "is the creator".

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [ ] 1.1 `Game.hasPlayer`, `NotAGameMemberException` and `GamePollingService`, with tests

### Phase 2: Web

- [ ] 2.1 `GET /api/games/{id}` on `GameController`, with `GameControllerTest`
- [ ] 2.2 Record the endpoint in `BACKWARD_COMPATIBILITY.md` §6

### Phase 3: Gate

- [ ] 3.1 `./gradlew build` green
