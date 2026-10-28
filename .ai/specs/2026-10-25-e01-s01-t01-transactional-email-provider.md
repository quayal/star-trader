# E01-S01-T01 — Integrate a transactional email provider to deliver the one-time code

## 📝 TLDR

E01-S01 (#12) shipped the request-side of passwordless login (D03), but its production `OneTimeCodeSender` adapter (`LoggingOneTimeCodeSender`) only writes the code to the application log — a named, temporary placeholder pending this task. This task swaps in a real transactional-email adapter behind the existing `OneTimeCodeSender` port so a player actually receives their one-time code by email, and adds the live send/receive test the brief's riskiest login assumption (A02) calls for before relying on email as the sole way in.

## 📝 Problem Statement

`LoggingOneTimeCodeSender`'s own doc comment says it plainly: "Placeholder adapter pending E01-S01-T01 (#13): logs the one-time code instead of emailing it." `[PRODUCT]` `src/main/kotlin/dev/adriangrzebyk/trader/login/adapter/email/LoggingOneTimeCodeSender.kt` (on the still-open `feat/issue-12-request-login-code` branch, PR #16). The product brief names this exact gap as its medium-severity, untested assumption A02: "A transactional email provider will reliably deliver one-time codes so players can actually sign in," with the stated risk "players could be locked out at the only entry point to the game," and the stated test: "send and receive a real one-time code through the chosen provider end-to-end before relying on it as the sole login path." Nothing in the repository does that yet.

## 📝 Proposed Solution

Pick [Resend](https://resend.com) as the transactional email provider and add a real `OneTimeCodeSender` adapter behind the port E01-S01 already defined — a pure adapter swap, no domain or port change, exactly as E01-S01's own spec anticipated ("swapping it for a real provider is a drop-in adapter replacement (#13), no port or domain change needed").

Why Resend over the alternatives considered:

- **Spring Mail / SMTP** — rejected. Needs an SMTP-capable mailbox/account and the `spring-boot-starter-mail` dependency; disproportionate machinery for sending one short text email per call.
- **AWS SES** — rejected. Needs an AWS account plus a production-access request out of SES's sandbox before it can send to arbitrary recipients; too much setup for a solo pre-launch project's first provider.
- **SendGrid / Postmark / Mailgun** — roughly equivalent single-POST REST APIs to Resend; any would work behind the same port. Resend is chosen for its simplicity (one POST, API-key auth, no SDK needed).

The creator already owns a domain, so this task includes verifying it in Resend (an operational DNS step, not code — see Implementation Plan Step 0) and sending from an address on it. That makes the live end-to-end test below a real proof of A02: delivery to an arbitrary real inbox, not just the account owner's own address, which is as far as Resend's unverified sandbox mode alone would otherwise allow.

The chosen adapter must not break the existing `./gradlew build` gate, which runs on every push with no secrets configured (`.github/workflows/ci.yml` — just `./gradlew build`, no secrets). So the adapter degrades to today's logging behavior when no API key is configured, rather than failing every build and every `@SpringBootTest` that constructs `LoginController`.

## 📝 Architecture

New adapter `login/adapter/email/ResendOneTimeCodeSender` (`@Component`), implementing the existing `OneTimeCodeSender` port — the only adapter behind that port in production; `LoggingOneTimeCodeSender` is removed, not kept alongside it (two `@Component` beans for the same port would make `LoginController`'s constructor injection ambiguous, and the whole point of this task is to replace the placeholder, not keep it live).

- Uses Spring's `RestClient` (already available via `spring-boot-starter-web`; no new dependency) to `POST https://api.resend.com/emails` with `Authorization: Bearer <apiKey>` and a small JSON body (`from`, `to`, `subject`, `text`).
- Configuration via `application.properties` + environment variables, following the existing `server.port`/env-override style already in that file:
  - `star-trader.email.resend.api-key=${RESEND_API_KEY:}` — the secret; never committed with a real value.
  - `star-trader.email.resend.from=${RESEND_FROM_ADDRESS:}` — an address on the creator's now-verified Resend domain (e.g. `login@yourdomain.com`); no usable default is shipped since it's specific to the creator's own domain.
- When `api-key` or `from` is blank (local dev, CI, any environment without both configured), the adapter logs the code exactly as `LoggingOneTimeCodeSender` did today, instead of calling Resend. This keeps `./gradlew build`, `LoginControllerTest`, and anyone's local `bootRun` without Resend configured working unchanged.
- When a send to Resend fails (non-2xx response, network error), the adapter logs the failure and does not throw — `LoginCodeRequestService.requestCode()` and `LoginController`'s `202 Accepted` contract are unchanged by this task; the fire-and-forget, no-enumeration design E01-S01 already chose stays exactly as it is. A silently-failed send is exactly A02's named risk, which is why it is surfaced through logs for an operator rather than exposed to the caller (exposing it would mean leaking whether an email address is valid, which E01-S01 already rejected).

No change to `LoginCodeRequestService`, `LoginCodeRequest`, `OneTimeCodeStore`, `OneTimeCodeGenerator`, or `LoginController`'s request/response contract — this task is scoped entirely to the one adapter behind `OneTimeCodeSender`.

## 📝 Data Model

No new entities. One new secret (`RESEND_API_KEY`) and one new non-secret config value (`RESEND_FROM_ADDRESS`), both environment-variable-backed per the existing `application.properties` convention. The API key is never logged, and `application.properties` only ever holds the `${RESEND_API_KEY:}` placeholder — the real value lives outside the repository (a local shell env var for manual runs; a CI secret only if the live test below is ever promoted into CI, which this task does not do). Domain verification itself (adding Resend's SPF/DKIM/CNAME records to the creator's own DNS) is an operational step outside the repository entirely — no code or config change represents "the domain is verified," it's just a precondition for `RESEND_FROM_ADDRESS` to actually work.

## 📝 API Contracts

No change. `POST /api/login/request-code` keeps its existing `202`/`400` contract from E01-S01 — this task only changes what happens behind that endpoint once validation passes.

## 📝 Edge Cases & Failure Scenarios

- No `RESEND_API_KEY` or `RESEND_FROM_ADDRESS` configured (local dev, CI, any `@SpringBootTest`) → the adapter logs the code instead of calling Resend, same visible behavior as E01-S01 shipped, so nothing that works today stops working.
- Resend rejects the call (bad key, rate limit, malformed recipient) → logged as an error server-side; the HTTP caller still receives `202` per the existing contract; the player genuinely does not receive a code. This is A02's risk made concrete — mitigated by this task's own live test (below), not eliminated by it.
- Domain verification hasn't finished propagating yet (DNS records added but not yet validated by Resend) → sends fail the same way as any other rejected call above (logged, `202` to the caller) until verification completes; this is a deploy-sequencing concern (verify first, deploy second), not a code gap.
- No retry on a transient failure — a player who doesn't receive a code can simply request a new one (E01-S01's existing "didn't get it, try again" self-service design already covers this; this task adds no queueing or retry logic, which would be speculative given nothing today needs it).

## 📝 Risks & Impact Review

- Domain verification (DNS records in the creator's own registrar/host) is a manual, one-time prerequisite this task depends on but doesn't automate — if it's undone or the domain's DNS changes later, sends start failing (logged, `202` to the caller) until it's fixed. Worth a line in deployment notes, not a code safeguard.
- API key is a secret, handled per standard environment-variable hygiene (never committed, never logged); no new secret-storage mechanism is introduced.
- No retry/backoff is added — an accepted, bounded gap (see Edge Cases), not an oversight; adding one now would be speculative given no evidence yet that transient failures are common enough to need it (engineering standards §0, §5).
- The live end-to-end test this task adds (below) is deliberately excluded from the default `./gradlew build` / CI run — it requires a real secret and makes a live external call, which would make every push flaky/dependent on a third party and require a committed or CI-configured secret the project doesn't have today. It runs only when a developer deliberately supplies `RESEND_API_KEY` locally, which is sufficient to satisfy A02's "exercised end-to-end" requirement without changing what CI guarantees on every push.

## 📝 Decisions in play

- A02 — this task is the assumption's own named mitigation ("send and receive a real one-time code through the chosen provider end-to-end"); it is addressed, not fully retired, until someone actually runs the live test in this task with a real inbox (owner: creator).
- D03 — completes the "a one-time code is emailed" half of passwordless login with a real send path.
- N02 — unaffected; nothing here adds password or OAuth login.

## Resolved assumptions (autonomous defaults)

| # | Question | Applied default | Why | Confirm? |
|---|----------|-----------------|-----|----------|
| Q1 | Which transactional email provider? | Resend. | Simplest REST API (one POST, API-key auth), no SMTP/JavaMail dependency. Reversible — swapping to another REST-based provider later is another adapter behind the same port. | reversible |
| Q2 | How does the app keep working in environments without a Resend account (CI, local dev)? | The same adapter falls back to logging the code when the API key is blank, instead of adding a second bean/profile. | Keeps `./gradlew build` and existing `@SpringBootTest`s green with no secret configured; avoids bean-selection complexity (Spring profiles, `@ConditionalOnProperty`) that nothing yet needs. | reversible |
| Q3 | What happens when Resend itself fails to send? | Log the failure; keep returning `202` from `POST /api/login/request-code` regardless, matching E01-S01's existing fire-and-forget, no-enumeration contract. | Consistent with E01-S01's deliberate choice not to let this endpoint reveal whether an email address is valid; a different contract here would be a new, undiscussed behavior change to that endpoint. | reversible |
| Q4 | Does the real send/receive test run in CI? | No — gated behind an environment variable (`RESEND_API_KEY`) so it only runs when a developer supplies a real key locally. | `ci.yml` has no secrets today; a live third-party call on every push would make CI flaky and would require provisioning a CI secret this project doesn't have yet. Satisfies A02's "exercised end-to-end" without weakening what CI guarantees. | reversible |
| Q5 | Send from the creator's own verified domain, or stay on Resend's unverified sandbox sender? | Verify the creator's own domain as part of this task (creator confirmed they already own one) and send from an address on it. | Lets the live test prove delivery to an arbitrary real inbox, not just the Resend account owner's own address — a stronger, more honest discharge of A02 than the sandbox-only path would give. | creator-confirmed |

## 📋 Phasing

- **Phase 1 (this task):** real `ResendOneTimeCodeSender` adapter, config, unit test against a mocked HTTP server, and the opt-in live end-to-end test. Independently shippable — no change to any other story's code.

## 📋 Implementation Plan

### Phase 1 — Real email delivery for the one-time code

0. **Operational, not code:** in the Resend dashboard, add the creator's domain and add the SPF/DKIM/CNAME records it provides to that domain's DNS; wait for Resend to mark it verified. Done once, outside the repository — nothing in later steps depends on *how* this was done, only that `RESEND_FROM_ADDRESS` (Step 1) is a real address on a domain Resend shows as verified.
1. Add `star-trader.email.resend.api-key=${RESEND_API_KEY:}` and `star-trader.email.resend.from=${RESEND_FROM_ADDRESS:}` to `application.properties`. Test: none yet (config only), covered by Step 3.
2. Add `login/adapter/email/ResendOneTimeCodeSender.kt` (`@Component`), reading the two properties via `@Value`, building one `RestClient` for `https://api.resend.com`. When either property is blank, log the code (same message `LoggingOneTimeCodeSender` used); otherwise `POST /emails` with `{from, to: email, subject, text}` containing the code, catching any exception or non-2xx response into a logged error, never throwing. Remove `login/adapter/email/LoggingOneTimeCodeSender.kt` (fully superseded).
3. Add `login/adapter/email/ResendOneTimeCodeSenderTest.kt` (plain JUnit, Spring's `MockRestServiceServer` bound to the adapter's `RestClient` — no network, no Spring context boot per engineering standards §7): configured properties send the expected POST body and headers on success; a simulated non-2xx response is swallowed (no exception) and logged; either property blank makes no HTTP call at all (falls back to the logging path).
4. Run `LoginControllerTest` (from E01-S01/#16) unchanged and confirm it still passes with no Resend properties set in the test environment — proves Step 2's fallback preserves today's behavior for existing tests.
5. Add `login/adapter/email/ResendOneTimeCodeSenderLiveIT.kt`, annotated `@EnabledIfEnvironmentVariable(named = "RESEND_API_KEY", matches = ".+")`, constructing the real adapter (reading `RESEND_API_KEY`/`RESEND_FROM_ADDRESS` from the environment) and sending a real code to a recipient read from a `RESEND_TEST_RECIPIENT_EMAIL` env var (never hardcoded — now that the domain is verified, this can be any real inbox, not just the Resend account owner's), asserting Resend responds success (an email id). This is the literal A02 proof; it is skipped (not failed) whenever `RESEND_API_KEY` isn't set, so `./gradlew build` stays green without the secret.
6. Run `./gradlew build` as the gate (the live test skips without the env var, as designed). Separately, with `RESEND_API_KEY`, `RESEND_FROM_ADDRESS` and `RESEND_TEST_RECIPIENT_EMAIL` set locally (after Step 0's domain verification completes), run `./gradlew test --tests "*ResendOneTimeCodeSenderLiveIT*"` once and confirm a real code is received at that inbox — the manual step that actually discharges A02, recorded in the PR description since it can't run unattended in CI.
