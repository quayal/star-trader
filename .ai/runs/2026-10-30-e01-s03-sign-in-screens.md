# Execution plan: E01-S03 — sign-in screens

Source doc: .ai/specs/2026-10-29-e01-s03-sign-in-screens.md
Issue: #34

## Goal

A player can sign in from a browser: enter name, corporation name and email, receive a
one-time code, enter it, and land back on the page they came from (or on "Create a game",
s11), staying signed in across reloads. The game screens send a signed-out player to the
sign-in screen and back.

## Scope

- New static page `src/main/resources/static/sign-in.html` (s10).
- New shared ES module `src/main/resources/static/js/session.js`: session token storage,
  the safe `next` link, and `requireSession()` for game screens.
- The Deck (`index.html`) and Market (`trade-offers.html`) call `requireSession()` before
  loading data (criterion 6).
- Unit tests for the JS module with Node's built-in test runner, wired into
  `./gradlew check` and CI.
- Docs: `docs/design-system.md` (new page), `BACKWARD_COMPATIBILITY.md` (sign-in screen is
  now the login API's consumer; Node for JS tests).

## Non-goals

- s11 "Create a game" and the lobby (E02-S05). With no `next` link the player goes to
  s11's route `/#new`, which today shows the Deck until E02-S05 adds it.
- Sign out, a signed-in player name in the sidebar, prefilling the Market's player name.
- Sending the session token on any other API call (no endpoint requires one yet, A05).
- Any backend change: E01-S01, E01-S02 and E01-S01-T01 already provide the API and real
  email (criterion 7 is met by #55's `ResendOneTimeCodeSender`).

## Decisions and deviations from the spec, recorded

- **Route.** The spec proposes `/#sign-in`. The app is separate static pages today, not a
  hash-routed single page, so s10 is `/sign-in.html?next=<path>`. The `next` value is
  accepted only when it is a same-origin path (starts with `/`, not `//` or `/\`), so the
  screen can't be used as an open redirect.
- **Validation stays on the server.** Step 1 posts the details and shows the server's
  E01-S01 message inline at the field it names (the server checks name, corporation,
  email in that order, which is the field order, so the first error gets focus). No rule
  is duplicated in JS. A message the page doesn't recognise is shown under the form.
- **Token storage.** `localStorage` under `starTrader.sessionToken`, so a reload keeps
  the player signed in (criterion 5). Read/write is wrapped in try/catch; with storage
  blocked the player is simply asked to sign in again.
- **Already signed in.** Opening s10 with a valid token goes straight to `next`.
- **JS tests need Node.** `./gradlew check` runs `node --test` on `src/test/js`. CI pins
  Node 22 with `actions/setup-node`. Locally the task is skipped with a warning when
  `node` isn't on the PATH, so a clean JDK-only checkout still builds (compat §5); CI
  never skips it.

## Implementation Plan

### Phase 1: Session module

- 1.1 Add `static/js/session.js` (token storage, safe `next` link, `requireSession`) with
  `node:test` unit tests.
- 1.2 Run the JS tests in `./gradlew check` and CI.

### Phase 2: Sign-in screen (s10)

- 2.1 Step 1: details form with inline E01-S01 messages and focus on the first error.
- 2.2 Step 2: code form, wrong/expired code message, Send a new code, Use a different email.
- 2.3 On success store the token and return to `next` or `/#new`; skip s10 when already
  signed in.

### Phase 3: Game screens require a session

- 3.1 Deck and Market call `requireSession()` before loading, sending a signed-out player
  to s10 with the link kept.

### Phase 4: Docs

- 4.1 Update `docs/design-system.md` and `BACKWARD_COMPATIBILITY.md`.

## Risks

- No browser test harness exists; the DOM flow is verified by driving the running app in a
  browser (screenshots on the PR), the pure logic by unit tests.
- Sessions live in memory (D02 not built): a server restart signs everyone out, which the
  gate handles by sending them to s10 with the link kept.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Session module

- [x] 1.1 Add session.js with unit tests — 10e537d
- [x] 1.2 Run the JS tests in ./gradlew check and CI — ad25f49

### Phase 2: Sign-in screen (s10)

- [x] 2.1 Step 1: details form with inline messages — 6e42fff
- [x] 2.2 Step 2: code form, resend, different email — 6e42fff
- [x] 2.3 Return to the next link on success — 6e42fff

### Phase 3: Game screens require a session

- [x] 3.1 Deck and Market require a session — 576f1bc

### Phase 4: Docs

- [x] 4.1 Update design-system and compatibility docs — e1253fb
