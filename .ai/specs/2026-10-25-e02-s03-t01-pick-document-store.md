# E02-S03-T01 — Pick a document store and validate it meets the consistency and schema needs

## 📝 TLDR

D02 commits to "persist game state durably in a document-oriented store," but names no concrete product, and two of the brief's riskiest assumptions sit directly on top of that gap: A01 (will the store's consistency model deliver the ~1–2s cross-player visibility D01 targets) and A03 (does one JSON document per game cover every access pattern the brief names). This task picks DynamoDB — the creator's own original proposal in D02's rationale, confirmed after weighing it against the alternative — proves A01 with a real local write-then-poll test, and checks the item shape against A03's named access patterns, before E02-S03 (#21) builds the real persistence adapter on top of whatever this task decides. Although filed as E02-S03's child task, this is a sequencing inversion worth naming up front: E02-S03's own acceptance criteria cannot be met until this task's decision exists.

## 📝 Problem Statement

Nothing in the repository commits to an actual store product yet. D02's own rationale mentions "a DynamoDB-like store" as the creator's proposal at the time, but D02 itself commits only to the document-oriented shape, not a specific vendor. `[DOCUMENT]` product-brief.md, D02 and A01/A03. Today `TradeOfferBook`/`game.GameStore` (E02-S01/S02) are in-memory only — no product has been exercised against either assumption at all.

## 📝 Proposed Solution

Pick DynamoDB, confirming the creator's own original proposal in D02's rationale, and prove A01 with a real local write-then-poll test rather than taking its marketing claims on faith.

This supersedes this task's own first draft, which had picked MongoDB on setup-friction grounds. The creator has an existing AWS account and hands-on DynamoDB experience from work — exactly the friction that reasoning was trying to avoid, and it doesn't exist here. With that constraint gone, there is no remaining reason to depart from the creator's own original proposal:

- **Zero new account or platform to learn.** An AWS account already exists; DynamoDB is a known tool from the creator's day job. MongoDB would have meant a brand-new platform (Atlas account, a different query/consistency model) for no offsetting benefit once the "avoid AWS setup" reasoning no longer applies.
- **Consistency fits A01 directly, with one explicit step.** DynamoDB's default reads are eventually consistent, but `GetItem`/`Query` support `ConsistentRead=true` for a strongly consistent read against the primary key — every read this app does (always by `Game.id`) qualifies. This is an opt-in flag to remember, not a limitation to work around; set once in the adapter, it applies to every read.
- **Document shape matches D02's own wording.** D02 asks for "game/player state kept as JSON"; a DynamoDB item is naturally JSON-shaped (nested maps/lists), no translation layer needed.
- **Genuinely free at this project's scale.** DynamoDB's on-demand pricing has a perpetual free tier (25 GB storage, a meaningful chunk of free read/write capacity every month) — more than enough for a pre-launch solo project with a handful of concurrent games.

Alternative considered: MongoDB (this task's own first draft). Rejected on reconsideration — its advantage was entirely "no AWS account needed," which the creator already has; keeping it would mean learning a new platform for no remaining benefit.

Alternative considered: PostgreSQL with a JSONB column. Rejected — it would work, but isn't what D02 describes ("a document-oriented store"); introducing a relational engine for a JSON-shaped need the creator explicitly scoped as document-oriented would be solving a decision that was already made, not revisiting it with evidence.

## 📝 Architecture

This task does **not** wire a production adapter — that is E02-S03's job, built on this task's decision. This task's own deliverable is: the dependency added to `build.gradle.kts` (`software.amazon.awssdk:dynamodb-enhanced`, the AWS SDK v2 enhanced client — no Spring Boot starter needed, same "raw SDK client, no extra framework glue" approach E01-S01-T01 took for Resend), a throwaway validation test proving A01, and a documented item shape addressing A03 (below) — both of which E02-S03 reads and builds its real `DynamoDbGameStore` adapter against, behind the existing `GameStore` port E02-S01 already defined (no port change expected).

## 📝 Data Model

Item shape, checked against the brief's three named access patterns before being committed:

- **One table (`star-trader-games`), one item per game**, partition key `id` (= `Game.id`) — shared state (`players`, a list of maps each with `money`/`ships`) and, once E03 adds it, a `confidentialByPlayer` nested map keyed by email for trade offers/agents/secret opportunities.
- *"List my confidential offers"* → `GetItem` by `id` (consistent read), project `confidentialByPlayer[callerEmail]` in application code. One item, one read, no secondary index — acceptable at the brief's own scale (2–6 players, a handful of concurrent games at a time for a pre-launch solo project), and trivially reversible if real play ever shows otherwise.
- *"Resolve trades for system+good across all players"* → same `GetItem`, scan every player's confidential offers in application code filtered by system+good — same reasoning: one item, in-process scan, no GSI needed at this scale.
- *"Look up one player's acquired secret opportunities"* → direct nested-field lookup (`confidentialByPlayer[email].secretOpportunities`) within the same item — the cheapest of the three patterns.

All three patterns are satisfiable from a single item per game, read by primary key with a consistent read, without a Global Secondary Index — A03's concern is addressed for the fields the brief names today. This is a forward-looking check: the confidential structure itself doesn't exist until E03, so this sketch is what E02-S03/E03 build against, not a currently-populated schema.

## 📝 API Contracts

None — this task adds no endpoint. `GameStore`'s existing contract (E02-S01: `create`, `find`, `update`) is unchanged; E02-S03 implements it against DynamoDB.

## 📝 Edge Cases & Failure Scenarios

- The chosen store becomes unreachable at startup or at write time → explicitly E02-S03's concern (it owns the real adapter's failure handling), not this task's; this task's own validation test runs against a local, task-controlled instance.
- A future access pattern needs a query other than "by game id" (e.g. "all games a player is in") → would need a Global Secondary Index, not covered by this task's single-table, primary-key-only design; named here as a condition that would require revisiting this task's own schema sketch, not assumed away. Nothing in E02's current scope needs it.

## 📝 Risks & Impact Review

- **This task validates consistency on a local DynamoDB Local instance, not real AWS infrastructure.** A01's actual target is cross-player visibility in real play against the deployed service; a local emulator is the smallest honest proxy available before any real deployment exists, and DynamoDB Local's consistency behavior for a single-partition `ConsistentRead=true` `GetItem` matches the real service's documented guarantee closely enough to trust for this check. The brief's own kill-criteria section already names "D02/A01 (consistency model can't meet the latency target)" as a standing revisit trigger; this task doesn't close that trigger forever, just for the assumption as currently testable.
- **`ConsistentRead=true` costs roughly double the read capacity of an eventually consistent read**, and is not available on Global Secondary Index queries (not a concern yet — this task's design uses none). Worth remembering if a future epic adds a GSI-backed access pattern and assumes the same consistency guarantee without re-checking it.
- No schema or contract already in production is touched — this is new, pre-implementation validation work.

## 📝 Decisions in play

- D02 — persist game state durably in a document-oriented store, with game/player state kept as JSON (owner: creator). This task confirms DynamoDB, the creator's own original proposal, as the concrete implementation.
- A01 — a document store's default consistency model must still deliver the ~1–2s cross-player visibility target (owner: creator). Addressed by this task's write-then-poll test (Step 2 below), using `ConsistentRead=true`.
- A03 — one JSON document per game must be sufficient for every needed access pattern (owner: creator). Addressed by the item-shape sketch above.

## Resolved assumptions (autonomous defaults)

| # | Question | Applied default | Why | Confirm? |
|---|----------|-----------------|-----|----------|
| Q1 | Which document store? | DynamoDB — the creator's own original proposal in D02, confirmed after the creator noted an existing AWS account and DynamoDB experience. | The setup-friction reasoning that first favored MongoDB doesn't apply to this creator; with it gone, there is no remaining reason to depart from the original proposal, and `ConsistentRead=true` closes the consistency gap directly. | creator-confirmed |
| Q2 | How is A01 tested given no production deployment exists yet? | A local DynamoDB Local write-then-poll test under concurrent writes, using `ConsistentRead=true`, not a real cross-region measurement. | The smallest honest test available today; real AWS deployment behavior should be re-checked once hosting is live, which this task names rather than hides. | reversible |
| Q3 | Does this task wire the real `GameStore` adapter into the app? | No — only the dependency, the validation test, and the item-shape sketch; E02-S03 (#21) builds the real adapter. | Keeps this task's scope to the decision and its proof, matching how the issue itself splits "pick/validate" (this task) from "build the real adapter" (the parent story). | reversible |

## 📋 Phasing

- **Phase 1 (this task):** dependency added, write-then-poll validation test, item-shape sketch documented. No production wiring. Must land before E02-S03's own implementation plan can proceed, despite the tracker's parent/child arrow pointing the other way.

## 📋 Implementation Plan

### Phase 1 — Pick and validate the store

1. Add `software.amazon.awssdk:dynamodb-enhanced` to `implementation` in `build.gradle.kts`. For local/test use, run DynamoDB Local via Testcontainers' generic container support against the official `amazon/dynamodb-local` image (no official Testcontainers module exists for it, but a `GenericContainer` wrapping that image is the standard pattern). No application code changes yet.
2. Add a throwaway `game/adapter/dynamodb/DynamoDbConsistencyValidationIT.kt` (not wired to `GameStore`, deleted or superseded once E02-S03 lands its real adapter test coverage): starts the DynamoDB Local container, creates the `star-trader-games` table, writes a game-shaped item from one thread, immediately reads it back with `ConsistentRead=true` from a second, separate client, and repeats under several concurrent writer/reader pairs, asserting every read observes its own write within the ~1–2s target (in practice, near-instantaneous locally — the test's point is to prove the property holds under concurrency with `ConsistentRead=true` set, not to measure network latency that doesn't exist locally).
3. Document the item-shape sketch (above) in this spec — no code artifact needed since the confidential structure it reasons about doesn't exist until E03.
4. Run `./gradlew build` (the Testcontainers test requires Docker; skip gracefully with a clear message if unavailable, matching how `ResendOneTimeCodeSenderLiveIT` skips without `RESEND_API_KEY` — a missing prerequisite should never fail the whole gate) as the validation gate, and record in the PR description that A01's local proof passed (with `ConsistentRead=true`) and A03's item-shape check is documented above.
