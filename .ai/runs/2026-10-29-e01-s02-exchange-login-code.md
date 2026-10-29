# Execution plan: E01-S02 — exchange a one-time code for a session

Source doc: .ai/specs/2026-10-25-e01-s02-exchange-login-code.md
Issue: #14

## Goal

A player who received a one-time code can exchange it for a session token
(`POST /api/login/exchange-code`), and the server recognizes that token on later
requests (`GET /api/login/session`), so later stories (E01-S03 sign-in screens,
E02 games) have a real identity to build on.

## Scope

- `login` package only: domain (`OneTimeCodeStore`, `LoginCodeRequestService`,
  new `Clock`, `SessionTokenGenerator`, `Session`, `SessionStore`,
  `CodeExchangeService`, `InvalidCodeExchangeException`), its adapters
  (`memory`, `random`, `system`, `web`) and tests.

## Non-goals

- Wiring any other endpoint (e.g. `TradeOfferController`) to require a session (A05, later story).
- Session expiry (D05: none in v1), game-scoped sessions (Q01), real email delivery (#13).
- Sign-in screens (E01-S03, #34).

## Deviation from the spec, recorded

The spec predates the 2026-10-28 hardening change, which already gave
`InMemoryOneTimeCodeStore` a 10-minute `CODE_TTL` and a `java.time.Clock`, purging
expired entries on each `store`. To keep one source of truth for the TTL and keep
expiry testable in the domain (engineering standards §4, §7):

- The TTL moves to the domain as `OneTimeCode.TTL` (10 minutes, unchanged), used by
  `CodeExchangeService` to reject expired codes and by the in-memory store to purge.
- The store no longer reads time itself: `store(email, code, issuedAt)` receives the
  time from the domain `Clock` port, and purges entries older than `issuedAt - TTL`.
- Domain `Clock` port as the spec names it; `SystemClock` adapter wraps `Instant.now()`.

## Implementation Plan

### Phase 1: Ports and fakes

1. `Clock` port + `SystemClock` adapter + `FakeClock`; `OneTimeCode.TTL`.
2. Extend `OneTimeCodeStore` (`store(email, code, issuedAt)`, `codeFor`, `consume`,
   `StoredCode`); update `InMemoryOneTimeCodeStore` (purge via `issuedAt`) and
   `FakeOneTimeCodeStore`; `LoginCodeRequestService` takes a `Clock`; update its tests
   and `LoginController` wiring.
3. `SessionTokenGenerator` port + `RandomSessionTokenGenerator` + fake + test.
4. `Session`, `SessionStore` port + `InMemorySessionStore` + `FakeSessionStore`.

### Phase 2: Exchange in the domain

5. `InvalidCodeExchangeException` + `CodeExchangeService` (TDD): valid code → session
   stored and code consumed; wrong code, expired code (at and past TTL), unknown
   email, second use → rejected, no session.

### Phase 3: HTTP

6. DTOs + `POST /api/login/exchange-code` (400 shape, 401 credential) and
   `GET /api/login/session` (bearer token; 401 missing/malformed/unknown).
7. `LoginControllerTest` cases: request → exchange → 200 + token; wrong code 401;
   reuse 401; session read 200 with identity; garbage/missing token 401.
8. Full gate: `./gradlew build`.

## Risks

- `risk-high` (login sessions): session token must never be logged; failure
  responses must not reveal which reason applied (enumeration avoidance).
- The controller test needs the issued code; it reads it from the store bean
  (`OneTimeCodeStore.codeFor`) rather than parsing logs.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Ports and fakes

- [ ] 1.1 Clock port, SystemClock, FakeClock, OneTimeCode.TTL
- [ ] 1.2 Extend OneTimeCodeStore and pass issuedAt from LoginCodeRequestService
- [ ] 1.3 SessionTokenGenerator port, random adapter, fake
- [ ] 1.4 Session, SessionStore port, in-memory adapter, fake

### Phase 2: Exchange in the domain

- [ ] 2.1 CodeExchangeService with domain tests

### Phase 3: HTTP

- [ ] 3.1 Exchange and session endpoints
- [ ] 3.2 Controller tests for exchange and session
- [ ] 3.3 Full validation gate
