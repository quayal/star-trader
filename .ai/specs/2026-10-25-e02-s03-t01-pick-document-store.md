# E02-S03-T01 — Pick a document store and validate it meets the consistency and schema needs

## 📝 TLDR

D02 commits to "persist game state durably in a document-oriented store," but names no concrete product, and two of the brief's riskiest assumptions sit directly on top of that gap: A01 (will the store's consistency model deliver the ~1–2s cross-player visibility D01 targets) and A03 (does one JSON document per game cover every access pattern the brief names). This task picks a product, proves A01 with a real write-then-poll test, and checks the schema shape against A03's named access patterns — before E02-S03 (#21) builds the real persistence adapter on top of whatever this task decides. Although filed as E02-S03's child task, this is a sequencing inversion worth naming up front: E02-S03's own acceptance criteria cannot be met until this task's decision exists.

## 📝 Problem Statement

Nothing in the repository or the brief names an actual store product. D02's own rationale mentions "a DynamoDB-like store" as the creator's proposal at the time, but D02 itself commits only to the document-oriented shape, not a specific vendor. `[DOCUMENT]` product-brief.md, D02 and A01/A03. Today `TradeOfferBook`/`game.GameStore` (E02-S01/S02) are in-memory only — no product has been exercised against either assumption at all.

## 📝 Proposed Solution

Pick MongoDB over the brief's own DynamoDB mention, and prove A01 with a real local write-then-poll test rather than taking either store's marketing claims on faith.

Why MongoDB over DynamoDB, named explicitly since it departs from the creator's own earlier proposal:

- **Setup friction for a solo, pre-launch project.** DynamoDB needs an AWS account and, for realistic local development, the DynamoDB Local emulator; MongoDB has a first-class Testcontainers module and a generous free-tier Atlas plan for a real deployment later — the same "avoid disproportionate account/infra setup" reasoning E01-S01-T01 already applied when it picked Resend over AWS SES.
- **Consistency fits A01 more directly.** DynamoDB's default reads are eventually consistent; meeting A01 would mean remembering to set `ConsistentRead=true` (extra cost, extra latency) on every read that matters. MongoDB's default read-your-writes behavior against the primary (the only node this solo deployment needs for now) gives the ~1–2s target headroom without an opt-in flag to forget.
- **Document shape is a closer match to D02's own wording.** D02 asks for "game/player state kept as JSON"; MongoDB documents are native BSON/JSON with no translation layer, same as DynamoDB's JSON item model — no advantage either way, but no disadvantage either.

Alternative considered: PostgreSQL with a JSONB column. Rejected — it would work, but isn't what D02 describes ("a document-oriented store"); introducing a relational engine for a JSON-shaped need the creator explicitly scoped as document-oriented would be solving a decision that was already made, not revisiting it with evidence.

Alternative considered: Google Firestore. Rejected for the same account/ecosystem-lock-in reasoning as DynamoDB, with no offsetting advantage over MongoDB for this project's scale.

## 📝 Architecture

This task does **not** wire a production adapter — that is E02-S03's job, built on this task's decision. This task's own deliverable is: the dependency added to `build.gradle.kts` (`spring-boot-starter-data-mongodb` + a Testcontainers MongoDB module in `testImplementation`), a throwaway validation test proving A01, and a documented schema sketch addressing A03 (below) — both of which E02-S03 reads and builds its real `MongoGameStore` adapter against, behind the existing `GameStore` port E02-S01 already defined (no port change expected).

## 📝 Data Model

Schema sketch, checked against the brief's three named access patterns before being committed:

- **One document per game**, keyed by `Game.id` — shared state (`players`, each with `money`/`ships`) and, once E03 adds it, a `confidentialByPlayer: Map<email, ...>` nested structure for trade offers/agents/secret opportunities.
- *"List my confidential offers"* → read the one document, project `confidentialByPlayer[callerEmail]`. In-process filtering of one small per-game document, not a separate indexed collection — acceptable at the brief's own scale (2–6 players, a handful of concurrent games at a time for a pre-launch solo project), and trivially reversible if real play ever shows otherwise.
- *"Resolve trades for system+good across all players"* → read the one document, scan every player's confidential offers filtered by system+good — same reasoning: one document, in-process scan, no index needed at this scale.
- *"Look up one player's acquired secret opportunities"* → direct nested-field lookup (`confidentialByPlayer[email].secretOpportunities`) within the same document — the cheapest of the three patterns.

All three patterns are satisfiable from a single document per game without a secondary index or a separate collection — A03's concern is addressed for the fields the brief names today. This is a forward-looking check: the confidential structure itself doesn't exist until E03, so this sketch is what E02-S03/E03 build against, not a currently-populated schema.

## 📝 API Contracts

None — this task adds no endpoint. `GameStore`'s existing contract (E02-S01: `create`, `find`, `update`) is unchanged; E02-S03 implements it against MongoDB.

## 📝 Edge Cases & Failure Scenarios

- The chosen store becomes unreachable at startup or at write time → explicitly E02-S03's concern (it owns the real adapter's failure handling), not this task's; this task's own validation test runs against a local, task-controlled instance.
- A future deployment adds MongoDB read replicas → would reintroduce exactly the eventual-consistency risk this task avoided by staying on a single primary; named here as a condition that would require re-running this task's own write-then-poll test, not assumed away.

## 📝 Risks & Impact Review

- **This task validates consistency on a local, single-node instance, not a real multi-region production deployment.** A01's actual target is cross-player visibility in real play; a local test is the smallest honest proxy available before any real deployment exists; a production MongoDB tier is unlikely to look like a single local node, so A01 should be re-checked once a real hosting choice exists — the brief's own kill-criteria section already names "D02/A01 (consistency model can't meet the latency target)" as a standing revisit trigger, and this task doesn't close that trigger forever, just for the assumption as currently testable.
- **Departs from the creator's own DynamoDB mention in D02's rationale.** D02 itself commits only to the document-oriented shape, not the vendor, so this is a reversible implementation choice within an unchanged decision — flagged for confirmation anyway since it's a visible departure from what the creator specifically named, not a silent substitution.
- No schema or contract already in production is touched — this is new, pre-implementation validation work.

## 📝 Decisions in play

- D02 — persist game state durably in a document-oriented store, with game/player state kept as JSON (owner: creator). This task picks MongoDB as the concrete implementation.
- A01 — a document store's default consistency model must still deliver the ~1–2s cross-player visibility target (owner: creator). Addressed by this task's write-then-poll test (Step 2 below).
- A03 — one JSON document per game must be sufficient for every needed access pattern (owner: creator). Addressed by the schema sketch above.

## Resolved assumptions (autonomous defaults)

| # | Question | Applied default | Why | Confirm? |
|---|----------|-----------------|-----|----------|
| Q1 | Which document store? | MongoDB, not the brief's own DynamoDB mention. | Lower setup friction for a solo pre-launch project (Testcontainers + free Atlas tier vs. an AWS account + DynamoDB Local), and default read-your-writes consistency fits A01 without an opt-in flag. | ⚠ NEEDS HUMAN CONFIRMATION |
| Q2 | How is A01 tested given no production deployment exists yet? | A local, single-node write-then-poll test under concurrent writes, not a real cross-region measurement. | The smallest honest test available today; a real deployment's consistency should be re-checked once hosting is chosen, which this task names rather than hides. | reversible |
| Q3 | Does this task wire the real `GameStore` adapter into the app? | No — only the dependency, the validation test, and the schema sketch; E02-S03 (#21) builds the real adapter. | Keeps this task's scope to the decision and its proof, matching how the issue itself splits "pick/validate" (this task) from "build the real adapter" (the parent story). | reversible |

## 📋 Phasing

- **Phase 1 (this task):** dependency added, write-then-poll validation test, schema sketch documented. No production wiring. Must land before E02-S03's own implementation plan can proceed, despite the tracker's parent/child arrow pointing the other way.

## 📋 Implementation Plan

### Phase 1 — Pick and validate the store

1. Add `org.springframework.boot:spring-boot-starter-data-mongodb` to `implementation` and a MongoDB Testcontainers module (`org.testcontainers:mongodb`) to `testImplementation` in `build.gradle.kts`. No application code changes yet.
2. Add a throwaway `game/adapter/mongo/MongoConsistencyValidationIT.kt` (not wired to `GameStore`, deleted or superseded once E02-S03 lands its real adapter test coverage): starts a Testcontainers MongoDB instance, writes a game-shaped document from one thread, immediately reads it back from a second, separate `MongoClient` connection, and repeats under several concurrent writer/reader pairs, asserting every read observes its own write within the ~1–2s target (in practice, near-instantaneous on a local primary — the test's point is to prove the property holds under concurrency, not to measure network latency that doesn't exist locally).
3. Document the schema sketch (above) in this spec — no code artifact needed since the confidential structure it reasons about doesn't exist until E03.
4. Run `./gradlew build` (the Testcontainers test requires Docker; skip gracefully with a clear message if unavailable, matching how `ResendOneTimeCodeSenderLiveIT` skips without `RESEND_API_KEY` — a missing prerequisite should never fail the whole gate) as the validation gate, and record in the PR description that A01's local proof passed and A03's schema check is documented above.
