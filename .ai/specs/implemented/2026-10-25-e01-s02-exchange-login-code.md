# E01-S02 — Player can exchange a valid one-time code to start a session

## 📝 TLDR

E01-S01 (#12) lets a player request a one-time code but gives the server no way to use it — nothing yet turns a correct code back into "this request really is player X" (D03's other half). This adds `POST /api/login/exchange-code`: a player submits the email and code they received; a valid, unexpired, matching code starts a session and returns a token the server recognizes on later requests, while a wrong or expired code is rejected and starts nothing.

## 📝 Problem Statement

`TradeOfferController` still accepts any free-text player name with no authentication — unchanged since E01-S01, because E01-S01 only covers requesting a code, not using one. `[PRODUCT]` `src/main/kotlin/dev/adriangrzebyk/trader/trading/adapter/web/TradeOfferController.kt`. D03 settles passwordless login end to end ("a one-time code is emailed; successful code exchange starts the session"), but only the emailing half exists today. Without this story, no session concept exists anywhere in the codebase, so no later endpoint can ever check "is this really player X."

## 📝 Proposed Solution

Add the exchange half of login to the existing `login` package from E01-S01 (same concept — login request and login exchange are two steps of one flow, not two concepts; engineering standards §1). A new `CodeExchangeService` validates a submitted `(email, code)` pair against the `OneTimeCodeStore` E01-S01 already introduced, checks it hasn't expired, and on success creates and stores a `Session`, returning an opaque session token. `LoginController` gains two endpoints: `POST /api/login/exchange-code` to perform the exchange, and a minimal `GET /api/login/session` that resolves a bearer token back to the signed-in player — the smallest possible read that lets this story's own acceptance criteria ("the server treats subsequent requests as that player") be demonstrated and tested, without retrofitting unrelated endpoints.

Alternative considered: making the emailed one-time code itself double as the session token (skip a separate exchange step). Rejected — the issue's acceptance criteria model two distinct actions (request, then exchange), and D03 itself describes code exchange as the event that "starts the session," implying the code and the resulting session are different values with different lifetimes: the code is short-lived and single-use, the session (per D05) is not expected to expire at all.

Alternative considered: wiring `TradeOfferController` (or any other existing endpoint) to require the new session token now. Rejected — out of scope. `TradeOfferController` returns no confidential per-player data today, so there is nothing for this story to protect yet; deciding how session, player and game are checked together against confidential reads is the brief's own separately-named assumption (A05), explicitly triggered "before building the confidential-state polling endpoint" — a later story (E05), not this one. This story's job is narrower: prove a session can be started and recognized at all.

Alternative considered: extending `OneTimeCodeStore` with a second, parallel structure for "codes pending exchange" instead of changing its existing shape. Rejected — that would create two sources of truth for the same outstanding code; extending the one store with an issue timestamp and a consume operation is simpler and is exactly what E01-S01's own last commit anticipated ("E01-S02 adds a real read method to the port when it actually needs one").

## 📝 Architecture

Extends the `login` package E01-S01 introduced:

- `OneTimeCodeStore` (existing port, extended): `store(email, code, issuedAt)` (adds the issue time), `codeFor(email): StoredCode?` (new read, returning the code and when it was issued), `consume(email)` (removes the entry so a code can't be exchanged twice). `InMemoryOneTimeCodeStore` and `FakeOneTimeCodeStore` updated to match; `LoginCodeRequestService` (existing) now needs a `Clock` to pass `issuedAt` when it calls `store(...)`.
- `Clock` (new port) — `fun now(): Instant`. Real adapter `adapter/system/SystemClock` (`@Component`, wraps `Instant.now()`); `FakeClock` for tests (settable instant), making expiry deterministic to test (engineering standards §4/§6). This is the first port of this shape in the codebase; it is the standard, minimal way to make time-based logic testable, not new scope beyond what expiry already requires.
- `SessionTokenGenerator` (new port) — `fun generate(): String`. Real adapter `adapter/random/RandomSessionTokenGenerator` (`@Component`, `UUID.randomUUID().toString()` — a 122-bit random value, unguessable, no new dependency); `FakeSessionTokenGenerator` for tests. Deliberately a separate port from E01-S01's `OneTimeCodeGenerator`: a 6-digit numeric code is meant to be short and typed by hand; a session token is meant to be unguessable and never typed — conflating the two would either weaken the session token or make the emailed code unusable.
- `Session` (new domain type) — `data class Session(val token: String, val playerName: String, val corporationName: String, val email: String)`.
- `SessionStore` (new port) — `store(session: Session)`, `find(token: String): Session?`. Real adapter `adapter/memory/InMemorySessionStore` (`@Component`, mirrors `InMemoryOneTimeCodeStore`'s `ConcurrentHashMap` shape); `FakeSessionStore` for tests.
- `InvalidCodeExchangeException` (new, mirrors `InvalidLoginRequestException`).
- `CodeExchangeService` (new domain service) — same shape as `LoginCodeRequestService` (a plain class wired by the controller, not a Spring bean): `fun exchangeCode(email: String, code: String): Session` looks up `codeFor(email)`, throws `InvalidCodeExchangeException` if there is none, if the submitted code doesn't match, or if `clock.now()` is past `issuedAt + CODE_TTL`; otherwise consumes the code, generates a token, builds and stores a `Session`, and returns it.
- `LoginController` (existing, extended): `POST /api/login/exchange-code` and `GET /api/login/session`, in the same request-validation and `ResponseStatusException` style as its existing endpoint.

No change to `TradeOfferController` or any other existing controller.

## 📝 Data Model

- `Session`: opaque random `token`, plus the identity captured at login time (`playerName`, `corporationName`, `email`). No game linkage yet — Q01 ("can one email join multiple concurrent games as different players, or is a session scoped to one game at a time?") is explicitly marked non-blocking for this story in the issue; a session here is purely a verified identity. Which game(s) it acts within is E02's concern once games exist.
- Storage: in-memory (`ConcurrentHashMap<String, Session>`), unbounded, no persistence — consistent with `InMemoryOneTimeCodeStore` today, pending D02; a server restart clears all sessions, which is an acceptable, already-existing property of the rest of the app's state, not a new gap this story introduces.
- One-time code TTL: 10 minutes, a new constant on `CodeExchangeService` (see Resolved assumptions).
- Confidentiality: unlike the one-time code (which E01-S01's placeholder adapter logs as a *named, temporary* exception, see E01-S01-T01/#13), the session token is never logged anywhere, by any component — it is a live credential, not a one-time value that's already useless after first use.

## 📝 API Contracts

`POST /api/login/exchange-code`

Request body:

```json
{ "email": "adrian@example.com", "code": "483920" }
```

- `email`, `code`: required, non-blank (same validation style as the existing `request-code` endpoint).

Response: `200 OK` on success —

```json
{ "sessionToken": "9b1f3e2a-6f3d-4c21-9a9d-6a9a7e1f8c42" }
```

Failure:
- `400 Bad Request` — missing/blank `email` or `code` field, same convention as the existing endpoint.
- `401 Unauthorized` — the code doesn't match, has already been consumed, has expired, or no code was ever requested for that email. This is the first `401` in the codebase; introduced deliberately because this is the app's first actual authentication check — a wrong or expired code is a credential failure, not a malformed request, so `400` (used for shape problems only) would be the wrong signal. The response body carries no detail about *which* of those four reasons applied, to avoid telling a caller whether an email has ever requested a code at all (same enumeration-avoidance habit E01-S01 already applied to `request-code`).

`GET /api/login/session`

Request: `Authorization: Bearer <sessionToken>` header, no body.

Response: `200 OK` —

```json
{ "playerName": "Adrian", "corporationName": "Adrian Star Traders", "email": "adrian@example.com" }
```

Failure: `401 Unauthorized` — header missing, malformed, or the token doesn't match any stored session.

## 📝 Edge Cases & Failure Scenarios

- Valid, unexpired, matching code → `200` + token; the code is consumed so it cannot be exchanged again (AC1).
- Wrong code for an email that does have one outstanding → `401`, no session (AC2).
- Correct code, but past the 10-minute TTL → `401`, no session (AC3).
- No code was ever requested for that email → `401`, same response shape as a wrong code (enumeration-avoidance, not an explicit AC but consistent with E01-S01's own stated reasoning).
- The same correct code submitted a second time, after already being exchanged once → `401` — the code was consumed on first use. Not spelled out in the issue's three ACs, but directly implied by "one-time code": without this, an overheard or logged code (see E01-S01-T01's placeholder-email caveat) would grant unlimited re-entry, not one entry.
- A player requests a second code for the same email before exchanging the first (E01-S01's existing overwrite behavior) → exchanging the *old* code afterward fails, because it's no longer the one on record; only the newest code works.
- Missing/blank `email` or `code` field in the request body → `400` (shape problem, not a credential failure).
- `GET /api/login/session` with a missing, malformed, or unrecognized bearer token → `401`.

## 📝 Risks & Impact Review

- **Breaking change to `OneTimeCodeStore`'s port signature** (`store` gains `issuedAt`; adds `codeFor`/`consume`). Safe to make here: E01-S01 (#12/#16) is still an **open, unmerged** pull request at the time of this spec — nothing in `main` depends on the port's current shape. Flagged so whoever implements this story coordinates with PR #16 rather than discovering the signature changed out from under it, rather than because it breaks anything already shipped.
- **Sessions never expire for v1.** A direct implementation of D05 ("a disconnected player can resume their exact same seat at any time via re-login, with no expiry"), not a new assumption this story introduces — cited here because it means a session token, once leaked, stays valid until the process restarts. Acceptable for a pre-launch, solo/small-group playtest per the brief's own stated purpose; worth revisiting once D02's persistence means state (including sessions, if this store is ever migrated too) survives restarts indefinitely.
- **This story does not satisfy A05.** It authenticates (proves identity) but adds no per-game authorization — there is no concept of "game" yet (E02). `GET /api/login/session` returns only the identity captured at login, never another player's data, so nothing here is itself a leak; it simply doesn't yet answer the harder question A05 names, which is explicitly scoped to "before building the confidential-state polling endpoint," a later story.
- Introducing `401 Unauthorized` as a new status code is a small, deliberate addition (see API Contracts), not an inconsistency with the existing `400`-only convention — the existing convention covers shape validation, which is all any prior endpoint ever needed to express.

## 📝 Decisions in play

- D03 — completes the passwordless login flow's second half (code exchange starts the session).
- D05 — sessions have no expiry for v1, implemented here, not merely assumed.
- N02 — unaffected; nothing here adds password or OAuth login.
- A05 — explicitly **not** resolved by this story; this story only lays the authentication primitive A05's later authorization check will sit on top of.
- Q01 — explicitly non-blocking per the issue; this story's `Session` carries no game reference, consistent with deferring that question.

## Resolved assumptions (autonomous defaults)

| # | Question | Applied default | Why | Confirm? |
|---|----------|-----------------|-----|----------|
| Q1 | How long is a one-time code valid before it expires? | 10 minutes. | Long enough to receive and type an emailed code without rushing; short enough that a leaked/overheard code (today's placeholder-email caveat from #13) has a narrow window. A constant on `CodeExchangeService`, trivially changed later. | reversible |
| Q2 | What does a session token look like? | A random `UUID` string, generated via a new `SessionTokenGenerator` port, separate from the existing 6-digit `OneTimeCodeGenerator`. | A session token must be unguessable and is never hand-typed, unlike the emailed code; reusing the 6-digit generator would produce a far too weak credential. Reversible — the port hides the format from every caller, same pattern as E01-S01's `OneTimeCodeGenerator`. | reversible |
| Q3 | Is the one-time code single-use? | Yes — `CodeExchangeService` consumes it from the store on a successful exchange, before returning. | Directly implied by "one-time code"; the issue's own name for the feature. Not treated as a discretionary default so much as a correctness requirement. | reversible |
| Q4 | Do sessions expire? | No, for v1 — matches D05 directly rather than introducing a new default. | D05 already settles this ("resume ... at any time ... with no expiry"); adding expiry here would contradict an active, creator-approved decision. | n/a — following an existing decision |
| Q5 | What HTTP status for a wrong/expired/unknown code? | `401 Unauthorized`, new to this codebase (previously only `400` existed). | This is the app's first actual authentication check; `401` is the correct, standard signal for "your credential didn't work," distinct from `400`'s "your request was malformed." Reversible — this is a brand-new endpoint with no existing client depending on a different convention. | reversible |
| Q6 | How does this story's own acceptance criterion ("subsequent requests are treated as that player") get demonstrated/tested? | A new minimal `GET /api/login/session` read, not by wiring any existing endpoint (e.g. `TradeOfferController`) to require the token. | Smallest addition that makes the AC testable; wiring unrelated endpoints to an auth check they don't yet need is explicitly out of scope (see Proposed Solution) and belongs with A05 once there is confidential state to protect. | reversible |
| Q7 | Is a session scoped to a specific game? | No — a session models only a verified identity for now. | Q01 is explicitly marked non-blocking for this story in the issue; no `Game` concept exists yet (E02). | reversible |

## 📋 Phasing

- **Phase 1 (this story, E01-S02):** `OneTimeCodeStore` extension + `Clock` port + `SessionTokenGenerator` port + `SessionStore` port + fakes + real adapters + `CodeExchangeService` + both new endpoints + tests. Independently shippable once E01-S01 (#12) is merged; nothing here depends on E01-S01-T01 (#13) — the placeholder or real email sender behind `OneTimeCodeSender` is irrelevant to exchanging a code that already exists in the store.

## 📋 Implementation Plan

### Phase 1 — Exchange a one-time code for a session

1. Extend `login/OneTimeCodeStore.kt`: `store(email, code, issuedAt: Instant)`, add `data class StoredCode(val code: String, val issuedAt: Instant)` and `fun codeFor(email: String): StoredCode?`, add `fun consume(email: String)`. Update `login/adapter/memory/InMemoryOneTimeCodeStore.kt` and `src/test/kotlin/.../login/FakeOneTimeCodeStore.kt` to match. Test: covered by Step 2's updated service test and Step 7's new exchange service test.
2. Update `login/LoginCodeRequestService.kt` to take a `Clock` dependency and call `codeStore.store(request.email, code, clock.now())`; update `login/LoginCodeRequestServiceTest.kt` to inject a `FakeClock` and assert via `codeFor(...)` instead of the old string-returning lookup; update `LoginController`'s wiring for the new `Clock` dependency.
3. Add `login/Clock.kt` (port) + `login/adapter/system/SystemClock.kt` (`@Component`) + `src/test/kotlin/.../login/FakeClock.kt` (settable `Instant`, mirrors `FakeOneTimeCodeGenerator`'s shape).
4. Add `login/SessionTokenGenerator.kt` (port) + `login/adapter/random/RandomSessionTokenGenerator.kt` (`@Component`, `UUID.randomUUID().toString()`) + `src/test/kotlin/.../login/FakeSessionTokenGenerator.kt`. Test: `RandomSessionTokenGeneratorTest` asserting two calls never collide across a large sample (mirrors `RandomOneTimeCodeGeneratorTest`'s shape check).
5. Add `login/Session.kt` and `login/SessionStore.kt` (port: `store(session)`, `find(token): Session?`) + `login/adapter/memory/InMemorySessionStore.kt` (`@Component`) + `src/test/kotlin/.../login/FakeSessionStore.kt`.
6. Add `login/InvalidCodeExchangeException.kt`.
7. Add `login/CodeExchangeService.kt` and `login/CodeExchangeServiceTest.kt` (plain JUnit, per engineering standards §7): a valid unexpired code returns a session, stores it, and consumes the code (a second exchange attempt with the same code then fails); a wrong code throws and stores no session; a correct code at exactly the TTL boundary and past it (via `FakeClock`) throws and stores no session; a correct code for an email with no outstanding code throws.
8. Add `login/adapter/web/LoginDtos.kt` additions (`ExchangeCodeRequestDto`, `SessionTokenResponseDto`, `SessionResponseDto`) and extend `login/adapter/web/LoginController.kt`: `POST /api/login/exchange-code` (validates non-blank `email`/`code` → `400`; calls `CodeExchangeService`, mapping `InvalidCodeExchangeException` → `401` via `ResponseStatusException`; success → `200` + token) and `GET /api/login/session` (parses `Authorization: Bearer <token>` via `@RequestHeader`, missing/malformed/unknown → `401`; known token → `200` + identity).
9. Add `login/adapter/web/LoginControllerTest.kt` cases (`@SpringBootTest`, `RestTestClient`, mirroring the existing style): request a code, then exchange it → `200` + a non-blank token; exchanging a wrong code → `401`; exchanging the same valid code twice → the second attempt is `401`; calling `GET /api/login/session` with the token from a successful exchange → `200` with the matching identity; calling it with a garbage token or no header → `401`. (The expiry case itself is covered precisely by Step 7's `FakeClock`-driven domain test, per engineering standards §7 — the Spring-context test is kept to wiring and the cases that need a real HTTP round trip.)
10. Run `./gradlew build` as the gate.

## 📝 Implementation notes (added when shipped, PR #54)

What shipped differs from the design above in these ways. The text above is left as planned:

- **`StoredCode` also keeps the requester's `playerName` and `corporationName`.** `Session` needs them, but the exchange only receives an email and a code. The port is `store(email, StoredCode)`, not `store(email, code, issuedAt)`.
- **`consume(email, code): Boolean` is compare-and-remove,** and there's a matching `replace(email, expected, updated)`. Two exchanges racing for one code start one session, not two (engineering standards §9).
- **At most 5 attempts per code** (`OneTimeCode.MAX_ATTEMPTS`). Each attempt is counted atomically before the code is compared, and the 5th wrong one invalidates the code. Added at review: without it, a 6-digit code could be brute-forced within its 10-minute TTL. General rate limiting for the login stays in Later in the brief.
- The 10-minute TTL that the 2026-10-28 hardening put on `InMemoryOneTimeCodeStore.CODE_TTL` moved to the domain as `OneTimeCode.TTL`.
