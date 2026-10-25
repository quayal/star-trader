# E02-S03 — A game's state persists so it survives a server restart

## 📝 TLDR

E02-S01/S02 create and join games, but `InMemoryGameStore` loses everything on restart — exactly the gap D02 exists to close. This spec replaces the in-memory `GameStore` adapter with a real MongoDB-backed one (chosen and validated by E02-S03-T01, #22), so a game created before a restart or redeploy reloads with its state intact. No port change: `GameStore`'s shape (`create`, `find`, `update`) was already specified by E02-S01 for exactly this swap.

## 📝 Problem Statement

`game.adapter.memory.InMemoryGameStore` (E02-S01) is a `ConcurrentHashMap`, lost on every JVM restart. `[PRODUCT]` `game/adapter/memory/InMemoryGameStore.kt` (on E02-S01's branch). D02 requires durable, JSON-shaped persistence; nothing in the repository provides it yet. This story cannot start ahead of E02-S03-T01 (#22): which store to use, and whether its consistency model is adequate (A01), is that task's own decision, not this story's to re-litigate.

## 📝 Proposed Solution

Add `game/adapter/mongo/MongoGameStore`, a `GameStore` implementation backed by the MongoDB instance and dependency E02-S03-T01 added, replacing `InMemoryGameStore` as the production `@Component` (mirrors exactly how E01-S01-T01 replaced `LoggingOneTimeCodeSender` — a pure adapter swap, old adapter removed, not kept alongside the new one to avoid an ambiguous Spring bean).

`create`/`find` map directly onto a MongoDB collection keyed by `Game.id` (insert-one / find-by-id). `update(id, mutate)` — the atomic mutator E02-S01 already specified and E02-S02 already depends on — is implemented as a retry loop around MongoDB's optimistic concurrency: read the document with its current version, apply `mutate` in memory, write it back with a filter matching the version just read (`findOneAndReplace` with a version-field filter), retrying on a no-match (another writer won the race) until it succeeds. This preserves engineering standards §9's atomicity guarantee across the swap from the in-memory adapter's `synchronized`/`compute` approach to a real network-backed store, which cannot hold a JVM-local lock across the write.

Alternative considered: MongoDB's native `findOneAndUpdate` with field-level update operators instead of a read-mutate-replace-with-version-check loop. Rejected for this story — `Game.addPlayer`'s invariant (max 6 players, no duplicate email) is a cross-field business rule enforced in Kotlin, not expressible as a single declarative Mongo update operator without duplicating that logic into a Mongo query; the version-checked retry loop keeps all business logic in `Game.addPlayer` where E02-S01/S02 already put it, at the cost of an occasional retry under contention — an acceptable trade at this app's scale (2–6 players per game).

## 📝 Architecture

- `game/adapter/mongo/MongoGameStore` (`@Component`, implements the existing `GameStore` port) — the sole production adapter from this story onward; `game/adapter/memory/InMemoryGameStore` is removed (fully superseded, same treatment E01-S01-T01 gave `LoggingOneTimeCodeSender`).
- A `GameDocument` (adapter-internal, not a domain type — translation at the package border per engineering standards §3): mirrors `Game`/`Player`/`Ship` shape plus a `version: Long` field used only for the optimistic-concurrency check in `update`; mapped to/from the domain `Game` inside the adapter, never leaking into `GameCreationService` or `GameController`.
- Configuration via `application.properties` + environment variable, following `login`'s own `${...}` pattern: `spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/star-trader}` — a sane local default so `./gradlew bootRun` keeps working against a local MongoDB without any required env var, while a real deployment overrides it.
- No change to `GameCreationService`, `game.Player`/`Ship`/`Game`, or `GameController` — this story is scoped entirely to the one adapter behind `GameStore`, exactly as E01-S01-T01 was scoped entirely to the adapter behind `OneTimeCodeSender`.

## 📝 Data Model

- `GameDocument`: `_id` (= `Game.id`), `players` (array, each with `email`, `name`, `corporationName`, `money`, `ships`), `version` (incremented on every successful `update`). Matches E02-S03-T01's schema sketch — one document per game, no secondary collection yet (nothing confidential exists to need one until E03).
- No migration concern: greenfield collection, no prior production data to carry forward.
- Confidentiality: email addresses are already captured by E01-S02/E02-S01; this story adds no new sensitive field, just durability for what already exists.

## 📝 API Contracts

No change. `POST /api/games`, `POST /api/games/{id}/join`, and `GET /api/games/{id}` (E02-S01/S02/S04) keep their existing contracts — this story only changes what backs `GameStore` behind them.

## 📝 Edge Cases & Failure Scenarios

- Server restarts after a game was created/joined → `MongoGameStore.find` reloads it unchanged from the collection (AC1).
- MongoDB is temporarily unreachable at write time → the write fails loudly (the adapter does not swallow the exception — unlike `login`'s fire-and-forget email design, a failed game-state write must surface as a server error to the caller, since there is no "try again later, the user won't notice" equivalent for "your move wasn't saved") (AC2).
- Two players join the same game at nearly the same instant (E02-S02's own concurrency case) → the version-checked retry loop in `update` ensures both lands; a naive "last write wins" replace without the version filter would silently drop one join, which is exactly what this design avoids.
- MongoDB reachable but the version-check retry loop never converges (pathological, sustained contention) → bounded with a small retry cap (e.g. 5 attempts) and then fails loudly rather than looping forever; at this app's 2–6-players-per-game scale this is not expected to bite in practice, but an unbounded retry would be a real hang risk worth naming rather than leaving implicit.

## 📝 Risks & Impact Review

- **In-memory `GameStore` is removed entirely.** Any test or code relying on `InMemoryGameStore` directly (none exist outside `game`'s own package; `GameCreationService`/`GameController` were written against the `GameStore` interface per E02-S01's own hexagonal design) needs no change — this is the entire point of the port/adapter split E02-S01 set up.
- **Depends on E02-S03-T01's (#22) still-unconfirmed provider choice** (⚠ marked in that task's own spec). If that confirmation changes the store, this story's `MongoGameStore` becomes a differently-named adapter behind the same unchanged `GameStore` port — a redo of this story's adapter, not of E02-S01/S02's domain code.
- A real MongoDB dependency is now required to run the app with working persistence; `./gradlew build`'s existing unit/domain tests (which use `FakeGameStore`, never a real adapter) are unaffected, but this story's own adapter test needs Docker (Testcontainers), same caveat E02-S03-T01 already named for its validation test.

## 📝 Decisions in play

- D02 — persist game state durably in a document-oriented store, with game/player state kept as JSON (owner: creator). This story is D02's actual implementation for game state.
- A01 — addressed by E02-S03-T01's validation test; this story's `update` design (version-checked retry) is what carries that guarantee into real concurrent-write behavior, not just the single-writer case T01 tested.

## Resolved assumptions (autonomous defaults)

| # | Question | Applied default | Why | Confirm? |
|---|----------|-----------------|-----|----------|
| Q1 | How is `GameStore.update`'s atomicity implemented against MongoDB? | Read-mutate-replace with an optimistic-concurrency version field, retried on conflict. | Keeps `Game.addPlayer`'s business-rule enforcement in Kotlin (where E02-S01/S02 already put it) rather than duplicating it into a Mongo update operator; a standard, well-understood pattern for this exact problem. | reversible |
| Q2 | What happens when a write to MongoDB fails? | Fails loudly (exception propagates to a `500`-level response), no silent swallow. | Unlike email delivery (E01-S01-T01's fire-and-forget design), there is no safe "the user can just retry later without noticing" story for a dropped game-state write; surfacing the failure is the only honest option. | reversible |
| Q3 | Is there a retry cap on the optimistic-concurrency loop? | Yes, a small fixed cap (e.g. 5), failing loudly past it. | Prevents a pathological, sustained-contention hang; at 2–6 players per game this is a safety margin, not an expected path. | reversible |

## 📋 Phasing

- **Phase 1 (this story, E02-S03):** `MongoGameStore` adapter, config, removal of `InMemoryGameStore`, tests (including a concurrent-write test proving `update`'s guarantee survives the swap to a real store). Depends on E02-S03-T01 (#22)'s store choice and E02-S01 (#19)'s port; independently shippable once both exist, regardless of whether E02-S02 (#20) or E02-S04 (#23) have landed yet (neither changes `GameStore`'s shape).

## 📋 Implementation Plan

### Phase 1 — Real persistence for game state

1. Add `spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/star-trader}` to `application.properties` (the dependency itself was already added by E02-S03-T01).
2. Add `game/adapter/mongo/GameDocument.kt` (adapter-internal mapping type, not exported) and `game/adapter/mongo/MongoGameStore.kt` (`@Component`, implements `GameStore`): `create` inserts a `GameDocument` at `version = 0`; `find` maps a loaded document back to `Game`, discarding `version`; `update` reads the current document, applies `mutate` to the mapped `Game`, attempts a version-filtered replace incrementing `version`, retrying (capped) on a no-match. Remove `game/adapter/memory/InMemoryGameStore.kt` (fully superseded).
3. Add `game/adapter/mongo/MongoGameStoreTest.kt` (Testcontainers MongoDB, per the pattern E02-S03-T01 established, not a `@SpringBootTest` — per engineering standards §7 this stays an adapter-focused test, not a domain test): `create` then `find` round-trips a game unchanged; `update` applied twice sequentially reflects both mutations; two concurrent `update` calls adding different players to the same game (mirroring E02-S02's own concurrency test, now against the real adapter) both land, proving the version-checked retry loop actually works, not just the in-memory `synchronized` block E02-S02 tested against.
4. Run `LoginControllerTest`/`GameControllerTest` (from E02-S01/S02, unaffected by this swap since they depend only on the `GameStore` interface) to confirm nothing regressed.
5. Run `./gradlew build` as the gate (the Testcontainers tests require Docker; skip gracefully with a clear message if unavailable, same caveat as E02-S03-T01's own validation test).
