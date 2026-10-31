# Execution plan: E02-S03 — games survive a server restart

Source doc: .ai/specs/2026-10-25-e02-s03-persist-game-state.md
Issue: #21

## Goal

Games are stored in DynamoDB instead of memory, so a game created or joined before a
server restart is still there afterwards (D02). Locally, `./gradlew bootRun` (and so the
ngrok playtest) uses DynamoDB Local in Docker with a data volume.

## Scope

- `game/adapter/dynamodb`: `DynamoDbGameStore` (the only production `GameStore`), its
  item mapper, Spring configuration, and tests against DynamoDB Local (Testcontainers).
- Remove `game/adapter/memory/InMemoryGameStore` and its test (superseded), and T01's
  throwaway `DynamoDbConsistencyValidationIT` (superseded by the adapter tests).
- `compose.yaml` for DynamoDB Local; `bootRun` points at it by default.
- Docs: `AGENTS.md` run line, `README.md`, `BACKWARD_COMPATIBILITY.md`,
  `.ai/engineering-standards.md`.

## Non-goals

- Persisting login sessions or one-time codes: they stay in memory, so after a restart
  players sign in again and find their games still there. (Not in this story's spec.)
- Ships, money and the start step (E02-S05, #35, running in parallel); the screens.
- Creating the table in real AWS (spec Q4: a deployment prerequisite, not app code).

## Decisions and deviations from the spec, recorded

Creator's decisions, 2026-10-31:

- **Native DynamoDB attributes, not a JSON string.** One item per game: `id` (S, partition
  key), `turns` (N), `status` (S), `creatorEmail` (S), `players` (L of M with `email`,
  `name`, `corporationName`), `version` (N).
- **Every story that adds data to `Game` extends the mapper and its round-trip test in the
  same PR.** Enforced at compile time where possible: the mapper builds `Game`/`Player` with
  every argument named, and domain data classes keep no default values, so a new field
  doesn't compile until the read side maps it; the round-trip test then catches a missing
  write. Recorded in `.ai/engineering-standards.md`.
- **Local runtime: DynamoDB Local in Docker with a data volume**, never real AWS by default.
  Real AWS is a configuration change (blank endpoint override + the SDK's standard
  credential and region chain).

Implementation choices:

- **Explicit mapper over the low-level `DynamoDbClient`, not `@DynamoDbBean`.** The spec's
  enhanced-client bean needs a mutable no-argument class, which can't give the
  compile-time check above; a small `GameItemMapper` with named arguments can. The
  dependency becomes `software.amazon.awssdk:dynamodb` instead of `dynamodb-enhanced`.
- **`update` retries up to 10 times** (spec: "e.g. 5"). A game has at most 5 joiners racing,
  and each round of the optimistic loop lets at least one through, so 5 could fail a
  legitimate join under worst-case contention; 10 keeps a margin and still fails loudly.
  `mutate` may run more than once, which `GameStore.update`'s KDoc now says.
- **Local table creation in the app, local only.** When the endpoint override is set
  (DynamoDB Local), the store's configuration creates the table if it's missing. With a
  blank override (real AWS) it creates nothing (spec Q4).
- **`bootRun` starts DynamoDB Local itself** through Spring Boot's Docker Compose support
  (`developmentOnly`), and sets the endpoint override to `http://localhost:8000` unless
  `DYNAMODB_ENDPOINT_OVERRIDE` is already set. Tests and the packaged jar don't get either,
  so they never touch a local database unless a test asks for one.
- **Spring tests:** `GameControllerTest` runs against a real DynamoDB Local container
  (shared across test classes), so the wiring, the local table creation and the mapper are
  exercised end to end over HTTP; CI's ubuntu runner and the developer's machine both
  have Docker. Like T01, it's skipped with a message when Docker is missing locally and
  fails on CI. The other Spring tests never call `GameStore`; with a blank endpoint
  override the client is created but never used, so they need no database.

## Implementation Plan

### Phase 1: Adapter

- 1.1 Dependencies and configuration properties.
- 1.2 `GameItemMapper` and `DynamoDbGameStore`, with `DynamoDbGameStoreTest`; remove the in-memory store and T01's test.
- 1.3 Spring wiring (`DynamoDbConfiguration`), with `GameControllerTest` on DynamoDB Local.

### Phase 2: Local runtime and docs

- 2.1 `compose.yaml` and the `bootRun` default; check a game survives a restart.
- 2.2 Docs: `AGENTS.md`, `README.md`, `BACKWARD_COMPATIBILITY.md`, `.ai/engineering-standards.md`.

### Phase 3: Gate

- 3.1 `./gradlew build` green.

## Risks

- `./gradlew bootRun` now needs Docker running. Stated in `AGENTS.md` and `README.md`.
- Once real games exist, adding a field to the item needs the mapper to read old items
  (default the missing attribute in the mapper, not the domain). Recorded in
  `BACKWARD_COMPATIBILITY.md`.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Adapter

- [x] 1.1 Dependencies and configuration properties — 38f1663
- [x] 1.2 `GameItemMapper` and `DynamoDbGameStore`, with `DynamoDbGameStoreTest`; remove the in-memory store and T01's test — 1eb500a
- [x] 1.3 Spring wiring (`DynamoDbConfiguration`), with `GameControllerTest` on DynamoDB Local — 38fcf9e

### Phase 2: Local runtime and docs

- [x] 2.1 `compose.yaml` and the `bootRun` default; check a game survives a restart — f1d6b8f
- [x] 2.2 Docs: `AGENTS.md`, `README.md`, `BACKWARD_COMPATIBILITY.md`, `.ai/engineering-standards.md` — 46982a7

### Phase 3: Gate

- [x] 3.1 `./gradlew build` green — 4095788
