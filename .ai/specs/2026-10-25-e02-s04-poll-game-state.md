# E02-S04 — A player can poll their game's current shared state

## 📝 TLDR

E02-S01/S02 let players create and join a game, but nothing lets a player see its state afterward except the response of the create/join call itself, which goes stale the moment anyone else acts. This adds `GET /api/games/{id}`: a signed-in member of the game polls its current shared state (its players, and each one's money and ships) without needing to manually refresh (D01); a caller who isn't a member of that game gets nothing back — the session→player→game authorization check the brief's highest-risk assumption (A05) calls for, built here before any confidential field exists to protect.

## 📝 Problem Statement

No polling endpoint for game state exists yet; the closest thing today, `/api/trade-offers`, is global and unscoped, with no notion of membership at all. `[PRODUCT]` `src/main/kotlin/dev/adriangrzebyk/trader/trading/adapter/web/TradeOfferController.kt`. The brief's A05 names exactly this gap: "each poll/read of game state correctly checks the caller's session against the requested game and player before returning that player's confidential data... not just that the caller is logged in" — untested today because no such endpoint exists to test.

## 📝 Proposed Solution

Add `GET /api/games/{id}` to the existing `GameController`. It resolves the caller the same way E02-S01/S02's endpoints already do (session token → `login.SessionStore`), loads the game via `GameStore.find(id)`, and returns its shared state only if the caller's email is among the game's players — otherwise the request is rejected outright, with no partial or redacted response.

Alternative considered: returning a redacted/empty-ish response (e.g. `200` with no player data) to a non-member instead of rejecting the request. Rejected — that would still confirm the game exists and respond successfully to someone with no business reading it; an outright rejection is the simpler, more defensible default and matches A05's own phrasing ("correctly checks... before returning").

Alternative considered: building this story's confidentiality proof against a real confidential field. Rejected — no confidential field exists yet at this point in the backlog (R02's confidential half starts with Bidding, E03); inventing one here to exercise the check would be scope creep this story doesn't need. This story proves the *membership* check A05 requires; the *member-sees-only-their-own-confidential-data* half of A05 completes once E03 introduces the first confidential field and reuses this same check.

## 📝 Architecture

No new package, no new port, no new domain type. Extends `GameController` with one read endpoint, reusing `GameStore.find` and the same caller-resolution path E02-S01/S02 already established:

- `GameController`'s new endpoint: resolve caller via `Authorization: Bearer <token>` → `login.SessionStore` (`401` on a missing/unknown token, same as every other endpoint on this controller); `GameStore.find(id)` (`404` if no such game); if the resolved session's email is not among `game.players.map { it.email }`, reject (`403` — see Risks & Impact Review for why this is a new status, distinct from E02-S02's `409`); otherwise return `200` + the same `GameView` shape E02-S01/S02 already return.

## 📝 Data Model

No new fields. "Shared state" for this story is exactly `GameView`'s existing shape (players, each with money and ships) — there is nothing confidential to split out yet (see Proposed Solution).

## 📝 API Contracts

`GET /api/games/{id}`

Request: `Authorization: Bearer <token>` header, no body.

Response: `200 OK` — the same `GameView` shape E02-S01's `POST /api/games` and E02-S02's join endpoint already return: `{ "id": ..., "players": [...] }`.

Failure:
- `401 Unauthorized` — missing/unknown token.
- `404 Not Found` — no game exists with `{id}`.
- `403 Forbidden` — the caller is authenticated but is not a member of this game.

## 📝 Edge Cases & Failure Scenarios

- A member polls their game → sees the current roster, money, and ships as persisted (AC1).
- A signed-in but non-member player polls a real game id → `403`, nothing about the game is returned (AC2 — this is A05's check).
- An unauthenticated caller (missing/bad token) polls any game id → `401`, same as every other endpoint on this controller — not called out separately in the issue's two ACs, but required for internal consistency with E02-S01/S02's own convention and with A05's own framing ("not just that the caller is logged in" presupposes a logged-in check exists first).
- A nonexistent game id, polled by any signed-in player → `404` — membership can't be checked against a game that doesn't exist, so this takes priority over a `403`.

## 📝 Risks & Impact Review

- **Introduces `403 Forbidden`, a new status for this app** (previously `400`/`401`/`409`). Deliberate and necessary: `401` already means "I don't know who you are" (used consistently since E01-S02); a member-only check on an authenticated-but-wrong caller is a distinct failure (`403`, "I know who you are, you're just not allowed here") — collapsing it into `401` or `409` would blur a real distinction this story needs for its own negative test to mean anything.
- **This story only partially discharges A05.** It proves the session→player→game membership check works, but has no confidential field yet to prove the other half (a member seeing only their *own* confidential state, not another member's). Named explicitly so a future reader doesn't mistake this story for "A05 is now fully resolved" — it completes once E03 adds the first confidential field and is checked against this same mechanism.
- No existing contract changes — `GET /api/games/{id}` is wholly new surface, additive to E02-S01/S02's existing endpoints.
- Per `SDLC.md`'s risk-high gate table (this story is `risk-high` — data scoping), the denied-path test (a non-member rejected) is required evidence before merge, not optional — Step 3 below is that test, not a nice-to-have.

## 📝 Decisions in play

- D01 — sync shared state via client polling, target ~1–2s perceived latency (owner: creator). This story is the first endpoint that exists to be polled this way.
- R02 — game information splits into shared state and per-player confidential state (owner: creator). This story builds the shared half and the membership-check scaffold the confidential half will reuse.
- A05 — session→player→game authorization must be checked before returning game state, not just that the caller is logged in (owner: creator). Partially discharged — see Risks & Impact Review.

## Resolved assumptions (autonomous defaults)

| # | Question | Applied default | Why | Confirm? |
|---|----------|-----------------|-----|----------|
| Q1 | What status for an authenticated non-member? | `403 Forbidden`, distinct from `401`/`409`. | Matches the actual failure kind; reversible, new endpoint, no existing client depends on a different convention. | reversible |
| Q2 | Does a non-member get any response data at all (e.g. just the game's existence)? | No — `403` with no body/fields about the game. | A05's own phrasing ("before returning that player's... data") argues for an all-or-nothing check; confirming existence to a non-member is itself a (small) information leak this story has no reason to accept. | reversible |

## 📋 Phasing

- **Phase 1 (this story, E02-S04):** the read endpoint and its membership check, plus the denied-path test the risk-high gate requires. Depends on E02-S01 (#19) and E01-S02 (#14); independently shippable once they exist. Does not depend on E02-S02 (#20) or E02-S03 (#21) — a single-player game is enough to prove both the happy path and the membership check.

## 📋 Implementation Plan

### Phase 1 — Poll a game's shared state

1. Extend `game/adapter/web/GameController.kt` with `GET /api/games/{id}`: resolve caller (`401`), `GameStore.find(id)` (`404` if null), membership check against `game.players` (`403` if the caller's email isn't present), else `200` + `GameView`.
2. Extend `game/adapter/web/GameControllerTest.kt`: create a game, poll it with the creator's own token → `200` with the expected player/money/ship data; poll a nonexistent game id → `404`; poll a real game with a *different* signed-in player's token (one who never joined it) → `403`, and assert the response body carries no game data; poll with no `Authorization` header → `401`.
3. Run `./gradlew build` as the gate — this is the risk-high denied-path evidence `SDLC.md` requires before this story's PR can merge.
