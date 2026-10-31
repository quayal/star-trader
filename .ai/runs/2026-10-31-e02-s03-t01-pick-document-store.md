# Execution plan: E02-S03-T01 — pick a document store and validate it

Source doc: .ai/specs/2026-10-25-e02-s03-t01-pick-document-store.md
Issue: #22

## Goal

Commit to DynamoDB for game state (D02), add the AWS SDK dependency, and prove A01 with
a write-then-read test against DynamoDB Local: every strongly consistent read sees the
write before it, under several concurrent writer/reader pairs.

## Scope

- `build.gradle.kts`: the AWS SDK v2 BOM and `dynamodb-enhanced` (`implementation`);
  Testcontainers and its JUnit 5 module (`testImplementation`).
- `game/adapter/dynamodb/DynamoDbConsistencyValidationIT.kt`: a temporary validation test,
  wired to nothing; superseded by E02-S03's adapter tests.
- The A03 item shape is already in the spec's Data Model section; no code for it.

## Non-goals

- Any production adapter, Spring bean or `application.properties` entry (E02-S03, #21).
- `game/adapter/web`, `BACKWARD_COMPATIBILITY.md` and the polling work (E02-S04, #23,
  running in parallel).
- Real AWS: no credentials, table or IAM.

## Decisions, recorded

- **Store: DynamoDB**, as the spec decided (creator-confirmed, Q1).
- **Runtime for the local server and the ngrok playtest: DynamoDB Local in Docker with a
  data volume, not real AWS** (creator's decision, 2026-10-31). Real AWS stays a later
  config change (blank endpoint override plus credentials). E02-S03 wires this; T01 only
  proves the store.
- **Pinned versions:** AWS SDK BOM `2.56.0` (2026-10-14); `amazon/dynamodb-local:3.3.2`
  (2026-10-15). Testcontainers comes from Spring Boot 4.1.1's dependency management
  (`2.0.5`, artifacts `testcontainers` and `testcontainers-junit-jupiter`), like every
  other Boot-managed dependency here.
- **Skip rule:** without Docker the test is skipped with a message, like
  `ResendOneTimeCodeSenderLiveIT` without its key. Exception: on CI (`CI` set), missing
  Docker fails the test, so CI can never skip A01's proof without anyone noticing.
  GitHub's `ubuntu-latest` runners have Docker.
- **Dummy credentials and region** (`local`/`local`, `eu-central-1`) are set on the test's
  own client; nothing reads the developer's AWS config.

## Implementation Plan

### Phase 1: Dependency

- 1.1 Add the AWS SDK and Testcontainers dependencies; confirm they resolve.

### Phase 2: Validation

- 2.1 `DynamoDbConsistencyValidationIT`: write-then-read with `ConsistentRead=true` under concurrent writer/reader pairs against DynamoDB Local.

### Phase 3: Gate

- 3.1 `./gradlew build` green, with the container test actually running locally.

## Risks

- DynamoDB Local is not the real service; the spec names this, and A01 should be
  rechecked if the game ever moves to real AWS.
- The first test run pulls a ~500 MB image; CI time grows by that pull.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Dependency

- [ ] 1.1 Add the AWS SDK and Testcontainers dependencies; confirm they resolve

### Phase 2: Validation

- [ ] 2.1 `DynamoDbConsistencyValidationIT`: write-then-read with `ConsistentRead=true` under concurrent writer/reader pairs against DynamoDB Local

### Phase 3: Gate

- [ ] 3.1 `./gradlew build` green, with the container test actually running locally
