# Star Trader — product brief

- Date: 2026-10-14
- Mode: own; Owner: project creator (solo, unconfirmed name — repo package is `pl.adriangrzebyk.trader`); Pass: full (2 of up to 3 rounds used)
- Evidence basis: the repository's existing domain code and mockups `[PRODUCT]`, plus decisions the creator made in this session `[DOCUMENT]`. No external users, interviews or data exist yet — this is a pre-launch solo project, so most claims are either what already exists in code or decisions the creator just made, not evidence about players.
- Coverage: 20 claims — 9 sourced (interview 0, data 0, document 5, product 4, benchmark 0), 0 synthetic, 11 assumed; 0 entries on the collection plan
- Synthetic hypotheses outside Coverage: 0
- Definition of Ready signed by: project creator — ready for: implementation planning of the architecture skeleton in Scope/Now. Not ready for a "do players want this" validation pass; no evidence of player demand exists or was sought (own-idea project, pre-launch).
- Sources: `README.md`; `design/mockups/*.html`; `src/main/kotlin/pl/adriangrzebyk/trader/**`; this conversation (creator's answers, 2026-10-14).

## Decision summary

The creator is building their own idea — an online version of a 1980s trading/space board game — and already has working domain logic (star systems, goods, dice-resolved trade offers) and static UI mockups, but no multi-game, multiplayer, or persistence layer yet. The decision this session supports is the shape of that missing layer: how many concurrent games are hosted, how shared vs. confidential state is kept and synced, how players authenticate, and how simultaneous-but-timed phases resolve.

The creator chose: short-interval polling for state sync (D01), a document-store persistence layer (D02), passwordless email-code login (D03), and a timer-based auto-skip for silent players in simultaneous phases (D04). They also revealed that confidential advantages are acquired and tradeable between players during play, not dealt out once (R06), which pushes the data model toward an event/ledger shape rather than a static per-player profile.

The most consequential untested beliefs are whether confidential reads can be scoped to the right player before any endpoint ships (A05) and whether a document store's default consistency model will reliably deliver the "a few seconds" cross-player visibility the creator wants (A01) — see Riskiest assumptions, which also now covers a timer/write race in D04 (A04) and the persistence schema shape (A03), both raised by an independent skeptic pass over this draft. The next action is for the creator (or whoever architects the skeleton) to settle the session→player→game authorization check (A05) and pick a concrete store and consistency setting against the latency target (A01) before building the polling+persistence path.

## Vision

Let a small group (2–6) play a classic trading/space board game online, with the board game's own clock speed (seconds, not real-time), while many independent groups can play at once on the same deployment. `[DOCUMENT]` creator's description, this session.

## Target group and stakeholders

- Customer (pays): not decided — no monetization discussed; out of scope for this brief. `[ASSUMPTION]`
- User (uses): small private groups (2–6 people) who already know the board game and want to play it remotely. `[DOCUMENT]` creator's description.
- Stakeholders: the creator is sole decider, builder and operator at this stage.
- Decider for scope decisions: the creator.

## Problems, with evidence

- No problem evidence was sought from external players — this is an own idea built from the creator's familiarity with the original board game, not from an observed gap reported by others. `[ASSUMPTION]`
- The repository shows a real, separate gap: the existing trade-offer code (`TradeOfferBook`, `TradeOfferController`) is a single global in-memory list with no game/room boundary, so today it could not actually support "many groups playing simultaneously" as the README claims. `[PRODUCT]` `src/main/kotlin/.../service/TradeOfferBook.kt`, `src/main/kotlin/.../web/TradeOfferController.kt`.

## Product and how it stands out

- What it is: a browser-based, static-page multiplayer adaptation of a classic 6-star-system trading board game, built on Spring Boot + Kotlin. `[DOCUMENT]` creator's description; `[PRODUCT]` `build.gradle.kts`.
- What makes it different: not benchmarked against other digital adaptations of the same or similar games; no comparison was made in this session.

| reference | what it does well | where it falls short for our users | checked on | link |
|---|---|---|---|---|

Deferred — no benchmark was relevant to the architecture decisions this session covered.

## Goals and success criteria

- Business goal: ship a playable, multi-room online version of the board game that feels like the tabletop original's pace. Owner: creator. Reference R01, D01, D02, D04.
- User outcome: a group of 2–6 players can run a full game session online, each seeing shared state and only their own confidential information, without needing to manually refresh to see other players' actions. `[DOCUMENT]` creator's description.
- Primary metric, baseline today, threshold, date: not set. The creator gave a qualitative target ("1–2 seconds is fine, mimic the board game, don't need to hit refresh") rather than a numeric SLA; treat ~1–2s perceived update latency as the working target behind D01, not a measured baseline. `[DOCUMENT]`
- What must not get worse: nothing yet exists to regress — not applicable at this stage.

## Scope

- **Now:** multi-game hosting with a game/room boundary; shared-state vs. per-player-confidential-state model; polling endpoint(s) for state sync (D01); persistent per-game state in a document store (D02); passwordless email-code login and session (D03); phase engine that gates ordered actions (e.g. ship jumps) behind simultaneous-action deadlines with timer-based auto-skip (D04); a way for one player to transfer or sell confidential info to another (R06).
- **Later:** push-based sync (WebSocket/SSE) if polling proves too slow or too costly at scale (N01); direct player-to-player chat, requested by the creator as "would be nice" but not committed for v1 (R07); reconnection/resilience hardening beyond the basic auto-skip timer; abuse/rate-limit handling for the email-code login.
- **Not doing:** see Non-goals.

## Domain glossary

| Term | Meaning | Owned by | Visible to |
|---|---|---|---|
| Game / room | One in-progress match with its own set of players, state and id; the unit of persistence and polling. | server | server; implied by all players in it |
| Shared state | Market prices, who owns which ships/factories/warehouses, whose turn/phase it is — visible to every player in the game. | server | all players in the game |
| Confidential state | Trade offers, agents, secret opportunities, inside info — scoped to one player unless explicitly shared. | server, per owning player | owning player only, until shared (R06) |
| Phase | A step of a turn that is either simultaneous (all players act within a time limit, D04) or strictly ordered (e.g. ship jumps resolve in a fixed sequence). | server | all players in the game |

## Key flows

- Current state: a player can submit a trade offer and the server can resolve the best offer for a system+good with a dice roll — but only within one global, unscoped list; there is no login, no game selection, and no persistence. `[PRODUCT]` `TradeOfferController.kt`.
- Future state (proposed, not yet built):
  1. Player signs in with name, star-corporation name and email; receives a one-time code by email; code exchange starts a session (D03).
  2. Player joins or creates a game (room); game state is created/loaded from the document store (D02).
  3. During simultaneous phases, each player's client polls the game's state endpoint every few seconds; the server advances the phase once all players have acted or the phase timer expires and silent players are auto-skipped (D01, D04).
  4. During ordered phases (e.g. ship jumps), the server resolves actions in a fixed sequence rather than waiting on a simultaneous deadline.
  5. A player may spend an action to share or sell a piece of confidential info with another named player, moving it from their own confidential state into the recipient's (R06).

## Business rules

| Id | Rule | Applies to | Source | Status | Review by | Required path to change | Owner | Supersedes |
|---|---|---|---|---|---|---|---|---|
| R01 | A single deployment hosts many concurrent, independent games; all state and identity are scoped per game. | hosting, data model | `[DOCUMENT]` creator's constraints | active | unknown | creator approval | creator | none |
| R02 | Game information splits into shared state (visible to all players in a game) and per-player confidential state (trade offers, agents, secret opportunities, inside info). | data model, API | `[DOCUMENT]` creator's constraints | active | unknown | creator approval | creator | none |
| R03 | Six star systems, four tradable goods; trades are resolved by a priced buy/sell offer plus a dice roll. | game rules | `[PRODUCT]` `StarSystem.kt`, `TradeOfferController.resolve` | active | unknown | creator approval | creator | none |
| R04 | Turns mix simultaneous player actions (e.g. placing trade offers) with strictly ordered resolution steps (e.g. ship jumps); the server must gate ordered steps until the simultaneous phase closes. | phase engine | `[DOCUMENT]` creator's constraints | active | unknown | creator approval | creator | none |
| R05 | The client is a static, lightweight browser page with no animation budget; it polls the server for updates rather than holding a persistent push connection (v1). | frontend | `[DOCUMENT]` creator's constraints; D01 | active | revisit if polling proves insufficient (N01) | creator approval | creator | none |
| R06 | Confidential advantages (agents, secret opportunities, inside info) are acquired during play — bought or shared between players — not dealt out fixed at setup; sharing moves a piece of confidential info from one player's state to another's. | data model, phase engine | `[DOCUMENT]` creator's answer, this session | active | unknown | creator approval | creator | none |
| R07 | Direct player-to-player chat is a nice-to-have, not a committed requirement. | scope | `[DOCUMENT]` creator's answer, this session ("would be nice") | proposal | unknown | creator approval | creator | none |

## Non-goals

| Id | We are not building | Why | Owner | Status | Review by | Required path to change | Source | Supersedes |
|---|---|---|---|---|---|---|---|---|
| N01 | Real-time push (WebSocket/SSE) for v1 | Creator accepted 1–2s polling latency as good enough to mimic the board game; simpler to build and scale across many concurrent games | creator | active | revisit if real play shows polling feels too slow or too costly | creator approval | `[DOCUMENT]` D01 | none |
| N02 | Password-based accounts or third-party OAuth login for v1 | Creator chose email one-time-code sign-in instead | creator | active | unknown | creator approval | `[DOCUMENT]` D03 | none |
| N03 | Animated or graphics-heavy UI | Creator wants a clean static page, explicitly no expensive animations | creator | active | unknown | creator approval | `[DOCUMENT]` creator's constraints | none |

## Decisions

| Id | Date | Decision | Why | Owner | Status | Review by | Required path to change | Source | Supersedes |
|---|---|---|---|---|---|---|---|---|---|
| D01 | 2026-10-14 | Sync shared state via client polling (target ~1–2s perceived latency), not a persistent push connection, for v1. | Creator confirmed 1–2s delay is fine and matches the board game's own pace; polling is simpler to build and reason about across many concurrent games than per-player push connections; push is deferred, not ruled out. | creator | active | revisit if real play shows it's too slow (N01) | creator approval | `[DOCUMENT]` this session | none |
| D02 | 2026-10-14 | Persist game state durably in a document-oriented store, with game/player state kept as JSON. | Creator wants in-progress games to survive a restart/redeploy; proposed a DynamoDB-like store as a reasonable fit for JSON-shaped per-game state. | creator | active | revisit after A01 and A03 are checked | creator approval | `[DOCUMENT]` this session | none |
| D03 | 2026-10-14 | Login is passwordless: a form captures player name, star-corporation name and email; a one-time code is emailed; successful code exchange starts the player's session. | Creator wants to know "this request really is player X" for confidential info, without building password/account management. | creator | active | unknown | creator approval | `[DOCUMENT]` this session | none |
| D04 | 2026-10-14 | Simultaneous-action phases use a timer; a player who hasn't acted when it expires is auto-skipped so the phase can still resolve. | Creator confirmed this directly when asked what should happen if a player doesn't act in time. | creator | active | unknown | creator approval | `[DOCUMENT]` this session | none |

## Riskiest assumptions

| Id | Assumption | Importance | Evidence today | If false | Smallest test | Owner | By when | Result |
|---|---|---|---|---|---|---|---|---|
| A01 | A document store's default (often eventually-consistent) read path will still deliver the agreed ~1–2s cross-player visibility under D01's polling model. | high | None — not tested. `[ASSUMPTION]` origin: creator's proposal of a DynamoDB-like store, this session. | Players would see stale state well past the agreed feel; D01/D02 would need a stronger-consistency read path or a different store. | Build the smallest possible write-then-poll loop against the chosen store and measure actual propagation delay under concurrent writes before committing the schema. | creator | before building the polling+persistence path | untested |
| A02 | A transactional email provider will reliably deliver one-time codes so players can actually sign in. | medium | None — not tested. `[ASSUMPTION]` | Players could be locked out at the only entry point to the game. | Send and receive a real one-time code through the chosen provider end-to-end before relying on it as the sole login path. | creator | before relying on email login in a real game | untested |
| A03 | A single JSON document per game is sufficient for every needed access pattern (e.g. "list my confidential offers", "resolve trades for system+good across all players", "look up one player's acquired secret opportunities"), without needing separate indexed items. | medium | None — not tested; R06 (confidential info acquired/traded during play) adds mutation patterns beyond a static per-player profile. `[ASSUMPTION]` | The single-document model could force expensive full-document reads/writes or lose write-conflict safety when two players act at once. | Sketch the actual read/write patterns from Key flows against the single-document model before committing the schema. | creator | before committing the persistence schema | untested |
| A04 | A phase can be advanced (D04's timer-based auto-skip) safely even if a player's action arrives at the server at nearly the same instant the timer fires — i.e. the phase-advance write is atomic/idempotent, so a last-moment action is neither silently dropped nor double-counted. | medium-high | None — not tested; raised by independent skeptic review, not yet addressed by D04 or A03. `[ASSUMPTION]` | A real player's last-second trade offer or ship order could vanish, or get applied twice, undermining trust in the game. | Design the phase-advance as a single atomic conditional write (e.g. compare-and-swap on phase status) and write a test that submits an action on the same tick as timer expiry before implementing the phase engine. | creator | before implementing the phase engine (D04) | untested |
| A05 | Each poll/read of game state correctly checks the caller's session against the requested game and player before returning that player's confidential data (trade offers, agents, secret opportunities), not just that the caller is logged in. | high | None — D03 only establishes authentication (proving who the caller is); authorization of confidential reads is undecided. Raised by independent skeptic review. `[ASSUMPTION]` | A player could read another player's confidential state through the polling endpoint — a real leak, not a hypothetical, once R06 makes that info a tradeable asset. | Decide and write down the session→player→game authorization check as part of the state-polling API design, before building any endpoint that returns confidential fields. | creator | before building the confidential-state polling endpoint | untested |

## Kill criteria

Not an experiment with a go/no-go gate — the creator has already committed to building this. The relevant revisit triggers are the ones named per-decision above: D01/N01 (polling feels too slow in real play), D02/A01 (consistency model can't meet the latency target), D03/A02 (email login proves unreliable).

## Hypotheses to test

None — no synthetic walkthroughs or persona panels have been run yet.

## Open questions

| Id | Question | Blocking | Who can answer | Status |
|---|---|---|---|---|
| Q01 | Can one email join multiple concurrent games as different players, or is a session scoped to one game at a time? | no — affects session model, not the architecture skeleton | creator | open |
| Q02 | What happens if a player disconnects or closes the tab entirely mid-game (beyond the per-phase auto-skip in D04) — can they resume the same seat later? | no — affects resilience hardening (Scope/Later), not v1 skeleton | creator | open |

## Definition of Ready addendum (own)

Problems and Target group have no independent evidence — this is explicitly an own idea with no interviews, data or external validation sought, which is consistent with a pre-launch solo project but means Problems/Target group do not meet the tiers-1–5 support bar a ticket-level review would require. All Now-scope decisions (D01–D04) are confirmed by the creator, the sole decider, so the architecture skeleton is ready for implementation planning. It is not ready, and was never meant to be ready, for validating that players want this — no such test was run or requested. A03 (persistence schema) should be checked against Key flows before the schema is finalized; A01 (consistency vs. latency) should be checked before relying on D02 in real play.

## Collection plan

None. All material needed for this session's decision (the architecture skeleton) came from the repository and the creator's own choices; no external material was requested.
