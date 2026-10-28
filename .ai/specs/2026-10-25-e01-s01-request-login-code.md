# E01-S01 — Player can request a one-time sign-in code

## 📝 TLDR

Today `TradeOfferController` accepts any free-text player name with no authentication at all. This spec adds the first half of passwordless login (D03): a player submits their name, star-corporation name, and email, and the server generates a one-time code and emails it to them. It does not add the second half — exchanging that code for a session — which is tracked separately as E01-S02 (issue #14).

## 📝 Problem Statement

No login or player identity exists today. `[PRODUCT]` `src/main/kotlin/dev/adriangrzebyk/trader/trading/adapter/web/TradeOfferController.kt` takes a raw `playerName` string with no verification that the caller actually is that player. The product brief (D03) settles the approach — passwordless, email one-time-code — but nothing implements any part of it yet; this is a greenfield feature.

## 📝 Proposed Solution

A new `login` package (by game concept, per `.ai/engineering-standards.md` §1) exposes `POST /api/login/request-code`. The domain service validates the submitted email, generates a one-time code, records it against that email, and sends it through a `OneTimeCodeSender` port. No session is created by this endpoint — that is E01-S02's job once the code is exchanged.

Alternative considered: folding this directly into `TradeOfferController`. Rejected — login is its own game concept (identity, not trading), and engineering standards §1 forbids grouping by technical layer instead of concept.

The real transactional email provider (riskiest assumption A02) is a separate, explicitly tracked task (E01-S01-T01, issue #13) — building it here would block this story on a vendor choice that isn't this story's concern. This spec therefore ships a `LoggingOneTimeCodeSender` as the production adapter behind the same `OneTimeCodeSender` port: it writes the code to the application log instead of delivering real email. This is a visible, temporary stand-in, not a hidden shortcut — it is called out in Risks & Impact Review below, and swapping it for a real provider is a drop-in adapter replacement (#13), no port or domain change needed.

## 📝 Architecture

New package `dev.adriangrzebyk.trader.login`, mirroring the existing `trading` package's shape (domain at the top, adapters in `login/adapter/*`):

- `LoginCodeRequest` — domain input (playerName, corporationName, email).
- `OneTimeCodeGenerator` (port) — real adapter `adapter/random/RandomOneTimeCodeGenerator` (mirrors `trading/adapter/random/RandomDiceRoller`); fake for domain tests.
- `OneTimeCodeSender` (port) — real adapter `adapter/email/LoggingOneTimeCodeSender` (placeholder pending #13); fake for domain tests.
- `OneTimeCodeStore` (port) — real adapter `adapter/memory/InMemoryOneTimeCodeStore` (mirrors `trading/adapter/memory/InMemoryTradeOfferBook` — in-memory is the existing precedent pending D02's persistence layer, not a new gap this spec introduces); fake for domain tests.
- `LoginCodeRequestService` — domain service wiring the three ports together, same shape as `TradeDesk` (a plain class constructed by the controller from injected adapters, not itself a Spring bean).
- `adapter/web/LoginController` — `@RestController`, same request-validation style as `TradeOfferController` (trim + required-field checks → `ResponseStatusException(BAD_REQUEST, ...)`).

No existing component changes. `StarTraderApplication`'s component scan already covers the new package since it's a subpackage of the application's root.

## 📝 Data Model

`OneTimeCodeStore` holds, per email address, the most recently issued code (a later request for the same email overwrites the prior one — see Resolved assumptions). No expiry or use-tracking is modeled here: those are E01-S02's concern once code *exchange* exists to need them, and adding them now would be unused state this story's acceptance criteria don't require. No persistence beyond process memory — unchanged from the rest of the codebase pending D02.

Sensitive-data note: the email address and the issued code are both confidential per the product brief's data model (R02); the in-memory store is process-local and never logged in the same statement as the code's destination (the log line SRC email target, so an operator effectively sees it, which is exactly what a temporary non-delivering adapter implies — this is the only place that is true, and it is named explicitly here and in #13's acceptance criteria as the reason #13 exists).

## 📝 API Contracts

`POST /api/login/request-code`

Request body:

```json
{ "playerName": "Adrian", "corporationName": "Adrian Star Traders", "email": "adrian@example.com" }
```

- `playerName`, `corporationName`: required, non-blank (same validation style as `TradeOfferController`'s `playerName` check).
- `email`: required, must match a basic `local@domain` shape (presence of exactly one `@` with non-empty parts on both sides — see Resolved assumptions for why nothing stricter).

Response: `202 Accepted`, empty body — the code was accepted for delivery; delivery itself is fire-and-forget from the caller's perspective (consistent with not leaking whether an email address is real, a enumeration-avoidance habit even for this pre-launch, low-stakes case).

Failure: `400 Bad Request` with a plain-text reason, for a missing/blank name or corporation name, or a malformed/missing email — mirrors `TradeOfferController`'s existing `ResponseStatusException` + `@ExceptionHandler` pattern exactly, so no new error-handling convention is introduced.

## 📝 Edge Cases & Failure Scenarios

- Malformed or missing email → `400`, no code generated, no send attempted (AC2).
- Missing/blank player name or corporation name → `400`, same as above (not an explicit AC in the issue, but the existing `TradeOfferController` convention this spec follows already treats a blank required field this way, and leaving it unvalidated would be an inconsistency, not a reduction in scope).
- Two requests for the same email before the first code is ever exchanged → the second request's code silently supersedes the first (see Resolved assumptions); this means an in-flight code can be invalidated by a second request from the same browser/tab, which is expected self-service behavior ("I didn't get the email, let me try again"), not a defect.
- `OneTimeCodeSender` failing (e.g., the logging adapter itself can't fail meaningfully, but #13's real provider will be able to) is out of scope for this story's acceptance criteria, which only requires the code to be "emailed" in today's placeholder sense; #13 owns provider-failure handling when it replaces this adapter.

## 📝 Risks & Impact Review

- **The production email adapter does not send real email.** `LoggingOneTimeCodeSender` writes the code to the application log. This is a known, intentional placeholder — not a silent gap — tracked by #13 (riskiest assumption A02 names exactly this as needing a real end-to-end send/receive test before relying on it). Shipping this story without #13 means login is not actually usable by a real player yet; it is a deliberate, reviewable sequencing choice (ship the request-side contract and tests now, swap the adapter once a provider is chosen), not a shortcut hidden from review.
- No schema or public contract already in production is touched; this is wholly new surface, so there is no migration or rollback concern.
- No dependency on E01-S01-T01 (#13) is a hard blocker for *this* story's own tests — `LoggingOneTimeCodeSender` satisfies "a code is emailed" in a way its own tests (asserting the port was invoked with the right code) can check without a real provider.

## 📝 Decisions in play

- D03 — login is passwordless: name, star-corporation name, and email; a one-time code is emailed; successful code exchange starts the session (owner: creator). This story implements the request half only.
- N02 — no password-based accounts or third-party OAuth login for v1 (owner: creator). Nothing here adds either.
- Brief Scope/Later — "abuse/rate-limit handling for the email-code login" is explicitly Later, not Now; this spec adds no rate limiting, by design, not oversight.

## Resolved assumptions (autonomous defaults)

| # | Question | Applied default | Why | Confirm? |
|---|----------|-----------------|-----|----------|
| Q1 | What does the one-time code look like (format/length)? | 6-digit numeric string (e.g. `"483920"`), generated via `kotlin.random.Random` through the `OneTimeCodeGenerator` port. | Smallest, most familiar shape for a code a player re-types from an email; no new dependency; easy to fake deterministically in tests. Reversible — the port hides the format from every caller. | reversible |
| Q2 | Can a player request a new code before using a previously issued one? | Yes — the newest request for an email overwrites that email's stored code. | Simplest state (one code per email, no list/history to manage); matches the self-service expectation of a "didn't get it, resend" flow; smallest surface (no new "invalidate" operation). | reversible |
| Q3 | How strict should email format validation be? | Minimal shape check only (one `@`, non-empty local and domain parts) — no deliverability check, no DNS/MX lookup. | Lowest blast radius; a stricter check risks false negatives before a real provider is even wired up (#13); the real bar for "does this email work" is #13's end-to-end send test (A02), not regex strictness here. | reversible |
| Q4 | Does the production adapter attempt real email delivery in this story? | No — ships a logging placeholder (`LoggingOneTimeCodeSender`); #13 replaces it with the real provider. | Keeps this story's scope to the request/validate/store/send-port contract per its own acceptance criteria, which the issue text separates from the provider-integration task; avoids this story being blocked on a vendor decision that belongs to #13. | reversible |

None of these touch security/data-scoping, an active product-brief decision, or `BACKWARD_COMPATIBILITY.md` (the repo has none yet) — no `⚠ NEEDS HUMAN CONFIRMATION` marker applies.

## 📋 Phasing

- **Phase 1 (this story, E01-S01):** domain service + ports + fakes + in-memory store adapter + logging email adapter + web endpoint + tests. Independently shippable and testable without #13 or E01-S02.

## 📋 Implementation Plan

### Phase 1 — Request a one-time sign-in code

1. Add `login/LoginCodeRequest.kt` (domain input) and `login/InvalidLoginRequestException.kt` (mirrors `trading/InvalidTradeOfferException.kt`). Test: none yet (plain data holders), covered via Step 4.
2. Add the three ports in `login/`: `OneTimeCodeGenerator`, `OneTimeCodeSender`, `OneTimeCodeStore`, plus their fakes under `src/test/kotlin/.../login/` (`FakeOneTimeCodeGenerator`, `FakeOneTimeCodeSender`, `FakeOneTimeCodeStore`), mirroring `FakeDiceRoller`'s shape. Test: fakes are exercised by Step 4's service test, not tested standalone (same as `FakeDiceRoller` today).
3. Add `login/LoginCodeRequestService.kt`: validates email shape (throws `InvalidLoginRequestException` on failure), else generates a code, stores it, and sends it — in that order, so a failed validation never reaches the store or sender.
4. Add `login/LoginCodeRequestServiceTest.kt` (plain JUnit, no Spring, per engineering standards §7): valid request stores and sends the generated code for that email; invalid email throws and neither stores nor sends anything (AC2's negative case at the domain layer).
5. Add the real adapters: `login/adapter/random/RandomOneTimeCodeGenerator.kt` (`@Component`), `login/adapter/memory/InMemoryOneTimeCodeStore.kt` (`@Component`, mirrors `InMemoryTradeOfferBook`), `login/adapter/email/LoggingOneTimeCodeSender.kt` (`@Component`, SLF4J logger). Test: exercised end-to-end by Step 7's controller test, which is what proves the real wiring works — these adapters are too thin to need isolated unit tests (consistent with `RandomDiceRoller` having no dedicated behavior test beyond `RandomDiceRollerTest`'s range check; add an equivalent range/shape check for `RandomOneTimeCodeGenerator` here).
6. Add `login/adapter/web/LoginDtos.kt` (`LoginCodeRequestDto`) and `login/adapter/web/LoginController.kt` (`@RestController`, `POST /api/login/request-code`, validation + `ResponseStatusException` + `@ExceptionHandler`, mirroring `TradeOfferController`).
7. Add `login/adapter/web/LoginControllerTest.kt` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTestClient`, mirroring `TradeOfferControllerTest`): a valid request returns `202`; a malformed-email request returns `400`; a blank-name request returns `400`.
8. Run `./gradlew build` (includes `./gradlew test`) as the validation gate.

Each step leaves the application building and green; Step 8 is the final gate before opening the PR.
