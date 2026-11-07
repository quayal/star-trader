# E02-S03 — A game's state persists so it survives a server restart

## 📝 TLDR

E02-S01/S02 create and join games, but `InMemoryGameStore` loses everything on restart — exactly the gap D02 exists to close. This spec replaces the in-memory `GameStore` adapter with a real DynamoDB-backed one (chosen and validated by E02-S03-T01, #22), so a game created before a restart or redeploy reloads with its state intact. No port change: `GameStore`'s shape (`create`, `find`, `update`) was already specified by E02-S01 for exactly this swap.

## 📝 Problem Statement

`game.adapter.memory.InMemoryGameStore` (E02-S01) is a `ConcurrentHashMap`, lost on every JVM restart. `[PRODUCT]` `game/adapter/memory/InMemoryGameStore.kt` (on E02-S01's branch). D02 requires durable, JSON-shaped persistence; nothing in the repository provides it yet. This story cannot start ahead of E02-S03-T01 (#22): which store to use, and whether its consistency model is adequate (A01), is that task's own decision, not this story's to re-litigate.

## 📝 Proposed Solution

Add `game/adapter/dynamodb/DynamoDbGameStore`, a `GameStore` implementation backed by the DynamoDB table and dependency E02-S03-T01 added, replacing `InMemoryGameStore` as the production `@Component` (mirrors exactly how E01-S01-T01 replaced `LoggingOneTimeCodeSender` — a pure adapter swap, old adapter removed, not kept alongside the new one to avoid an ambiguous Spring bean).

`create`/`find` map directly onto the `star-trader-games` table keyed by `Game.id` (`PutItem` with `attribute_not_exists(id)`, so creating over an existing id fails loudly instead of silently overwriting / `GetItem` with `ConsistentRead=true`, per E02-S03-T01's A01 proof). `update(id, mutate)` — the atomic mutator E02-S01 already specified and E02-S02 already depends on — is implemented as a retry loop around DynamoDB's native optimistic-concurrency idiom: read the item with its current `version` (consistent read), apply `mutate` in memory, write it back with `PutItem` and `ConditionExpression: version = :expectedVersion`, catching `ConditionalCheckFailedException` (another writer won the race) and retrying. This preserves engineering standards §9's atomicity guarantee across the swap from the in-memory adapter's `synchronized`/`compute` approach to a real network-backed store, which cannot hold a JVM-local lock across the write.

Alternative considered: DynamoDB's native `UpdateItem` with attribute-level update expressions instead of a read-mutate-replace-with-version-check loop. Rejected for this story — `Game.addPlayer`'s invariant (max 6 players, no duplicate email) is a cross-field business rule enforced in Kotlin, not expressible as a single declarative update expression without duplicating that logic into DynamoDB's expression language; the version-checked retry loop keeps all business logic in `Game.addPlayer` where E02-S01/S02 already put it, at the cost of an occasional retry under contention — an acceptable trade at this app's scale (2–6 players per game), and the textbook DynamoDB optimistic-locking pattern for exactly this situation.

## 📝 Architecture

- `game/adapter/dynamodb/DynamoDbGameStore` (`@Component`, implements the existing `GameStore` port) — the sole production adapter from this story onward; `game/adapter/memory/InMemoryGameStore` is removed (fully superseded, same treatment E01-S01-T01 gave `LoggingOneTimeCodeSender`).
- A `GameItem` (adapter-internal, not a domain type — translation at the package border per engineering standards §3, modeled with the AWS SDK v2 enhanced client's `@DynamoDbBean`): mirrors `Game`/`Player`/`Ship` shape plus a `version: Long` attribute used only for the optimistic-concurrency check in `update`; mapped to/from the domain `Game` inside the adapter, never leaking into `GameCreationService` or `GameController`.
- Configuration via `application.properties` + environment variables, following `login`'s own `${...}` pattern: `star-trader.dynamodb.table-name=${DYNAMODB_TABLE_NAME:star-trader-games}` and `star-trader.dynamodb.endpoint-override=${DYNAMODB_ENDPOINT_OVERRIDE:}` (blank in production, pointed at DynamoDB Local for `./gradlew bootRun` in development). AWS region and credentials use the SDK's standard default chain (environment, `~/.aws/config`, instance/task role in a real deployment) — no bespoke credential handling introduced.
- No change to `GameCreationService`, `game.Player`/`Ship`/`Game`, or `GameController` — this story is scoped entirely to the one adapter behind `GameStore`, exactly as E01-S01-T01 was scoped entirely to the adapter behind `OneTimeCodeSender`.

## 📝 Data Model

- `GameItem`: `id` (partition key, = `Game.id`), `players` (list of maps, each with `email`, `name`, `corporationName`, `money`, `ships`), `version` (incremented on every successful `update`). Matches E02-S03-T01's item-shape sketch — one item per game, no Global Secondary Index yet (nothing confidential exists to need one until E03, and E02's own access patterns are all by-id).
- No migration concern: greenfield table, no prior production data to carry forward.
- Confidentiality: email addresses are already captured by E01-S02/E02-S01; this story adds no new sensitive field, just durability for what already exists.

## 📝 API Contracts

No change. `POST /api/games`, `POST /api/games/{id}/join`, and `GET /api/games/{id}` (E02-S01/S02/S04) keep their existing contracts — this story only changes what backs `GameStore` behind them.

## 📝 Edge Cases & Failure Scenarios

- Server restarts after a game was created/joined → `DynamoDbGameStore.find` reloads it unchanged from the table (AC1).
- DynamoDB is temporarily unreachable at write time (throttled, network error) → the write fails loudly (the adapter does not swallow the exception — unlike `login`'s fire-and-forget email design, a failed game-state write must surface as a server error to the caller, since there is no "try again later, the user won't notice" equivalent for "your move wasn't saved") (AC2).
- Two players join the same game at nearly the same instant (E02-S02's own concurrency case) → the version-checked retry loop in `update` ensures both lands; a plain `PutItem` without the `ConditionExpression` would silently drop one join, which is exactly what this design avoids.
- DynamoDB reachable but the version-check retry loop never converges (pathological, sustained contention) → bounded with a small retry cap (e.g. 5 attempts) and then fails loudly rather than looping forever; at this app's 2–6-players-per-game scale this is not expected to bite in practice, but an unbounded retry would be a real hang risk worth naming rather than leaving implicit.

## 📝 Risks & Impact Review

- **In-memory `GameStore` is removed entirely.** Any test or code relying on `InMemoryGameStore` directly (none exist outside `game`'s own package; `GameCreationService`/`GameController` were written against the `GameStore` interface per E02-S01's own hexagonal design) needs no change — this is the entire point of the port/adapter split E02-S01 set up.
- **Real AWS usage begins here.** This is the first story that touches the creator's actual AWS account (DynamoDB Local covers local dev/test; a real deployment needs the table created and IAM credentials with read/write access to it — named here as a deployment-time prerequisite, not something this story's code handles).
- `ConsistentRead=true` doubles the read-capacity cost of a `GetItem` versus an eventually consistent read (per E02-S03-T01's own note) — negligible at this app's scale, but worth remembering if usage ever grows enough for DynamoDB cost to matter.
- A real DynamoDB dependency is now required to run the app with working persistence; `./gradlew build`'s existing unit/domain tests (which use `FakeGameStore`, never a real adapter) are unaffected, but this story's own adapter test needs Docker (DynamoDB Local via Testcontainers), same caveat E02-S03-T01 already named for its validation test.

## 📝 Decisions in play

- D02 — persist game state durably in a document-oriented store, with game/player state kept as JSON (owner: creator). This story is D02's actual implementation for game state.
- A01 — addressed by E02-S03-T01's validation test; this story's `update` design (version-checked retry via `ConditionExpression`) is what carries that guarantee into real concurrent-write behavior, not just the single-writer case T01 tested.

## Resolved assumptions (autonomous defaults)

| # | Question | Applied default | Why | Confirm? |
|---|----------|-----------------|-----|----------|
| Q1 | How is `GameStore.update`'s atomicity implemented against DynamoDB? | Read-mutate-replace with an optimistic-concurrency `version` attribute and a `ConditionExpression`, retried on `ConditionalCheckFailedException`. | Keeps `Game.addPlayer`'s business-rule enforcement in Kotlin (where E02-S01/S02 already put it) rather than duplicating it into a DynamoDB update expression; the standard, documented DynamoDB optimistic-locking pattern for exactly this problem. | reversible |
| Q2 | What happens when a write to DynamoDB fails? | Fails loudly (exception propagates to a `500`-level response), no silent swallow. | Unlike email delivery (E01-S01-T01's fire-and-forget design), there is no safe "the user can just retry later without noticing" story for a dropped game-state write; surfacing the failure is the only honest option. | reversible |
| Q3 | Is there a retry cap on the optimistic-concurrency loop? | Yes, a small fixed cap (e.g. 5), failing loudly past it. | Prevents a pathological, sustained-contention hang; at 2–6 players per game this is a safety margin, not an expected path. | reversible |
| Q4 | How does the table get created? | Out of scope for application code — created once via the AWS console/CLI (or an IaC tool the creator already uses), named as a deployment prerequisite. | Table provisioning is an operational/infrastructure concern, not something `DynamoDbGameStore` should do at runtime; DynamoDB Local's test setup (E02-S03-T01) creates its own table per test run, which is a different, already-covered concern. | reversible |

## 📋 Phasing

- **Phase 1 (this story, E02-S03):** `DynamoDbGameStore` adapter, config, removal of `InMemoryGameStore`, tests (including a concurrent-write test proving `update`'s guarantee survives the swap to a real store). Depends on E02-S03-T01 (#22)'s store choice and E02-S01 (#19)'s port; independently shippable once both exist, regardless of whether E02-S02 (#20) or E02-S04 (#23) have landed yet (neither changes `GameStore`'s shape).

## 📋 Implementation Plan

### Phase 1 — Real persistence for game state

1. Add `star-trader.dynamodb.table-name=${DYNAMODB_TABLE_NAME:star-trader-games}` and `star-trader.dynamodb.endpoint-override=${DYNAMODB_ENDPOINT_OVERRIDE:}` to `application.properties` (the dependency itself was already added by E02-S03-T01).
2. Add `game/adapter/dynamodb/GameItem.kt` (`@DynamoDbBean`, adapter-internal mapping type, not exported) and `game/adapter/dynamodb/DynamoDbGameStore.kt` (`@Component`, implements `GameStore`): `create` puts a `GameItem` at `version = 0` with `attribute_not_exists(id)`; `find` maps a loaded item back to `Game` (consistent read), discarding `version`; `update` reads the current item (consistent read), applies `mutate` to the mapped `Game`, attempts a version-conditioned `PutItem` incrementing `version`, retrying (capped) on `ConditionalCheckFailedException`. Remove `game/adapter/memory/InMemoryGameStore.kt` (fully superseded).
3. Add `game/adapter/dynamodb/DynamoDbGameStoreTest.kt` (DynamoDB Local via Testcontainers, per the pattern E02-S03-T01 established, not a `@SpringBootTest` — per engineering standards §7 this stays an adapter-focused test, not a domain test): `create` then `find` round-trips a game unchanged; `create` over an existing id fails; `update` applied twice sequentially reflects both mutations; two concurrent `update` calls adding different players to the same game (mirroring E02-S02's own concurrency test, now against the real adapter) both land, proving the version-checked retry loop actually works, not just the in-memory `synchronized` block E02-S02 tested against.
4. Run `LoginControllerTest`/`GameControllerTest` (from E02-S01/S02, unaffected by this swap since they depend only on the `GameStore` interface) to confirm nothing regressed.
5. Run `./gradlew build` as the gate (the Testcontainers tests require Docker; skip gracefully with a clear message if unavailable, same caveat as E02-S03-T01's own validation test).
