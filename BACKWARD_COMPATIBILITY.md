# Backward compatibility

What this project treats as a protected contract surface, and how a change to one must be handled. Review skills check changes against this file; implementation skills warn when a change isn't compliant.

## Context: this is a pre-launch, single-maintainer project

Per `.ai/specs/product-brief.md`, there are no real players or external clients yet, and the current API is explicitly **expected** to be replaced as multi-game hosting (R01), authentication (D03) and persistence (D02) land. Treat the surfaces below as "don't break without noticing", not "frozen forever" — but still require every breaking change to be a deliberate, stated decision (ideally recorded in the product brief's Decisions table), not an accident.

## Protected surfaces today

### 1. Game rules (highest protection)

`StarSystem`, `Goods`, `OfferType`, and the resolution logic in `TradeDesk` / `BestOfferCalculator` / `SDCalculator` / `DiceRoller` encode the original board game's actual rules (business rule R03: six star systems, four tradable goods, dice-resolved trades). These are the closest thing this project has to a stable contract, because they're fixed by the board game itself, not by this codebase's current stage.

- **Breaking change**: altering a star system's name/goods, a good's identity, the dice mechanic, or the offer-matching/pricing logic in a way that changes a game outcome for the same inputs.
- **Required path**: must come with a test proving the new behavior against the original game's rule, and a note in the PR body explaining why the rule changed (new house rule, bug fix against the original, etc.) — not silently "while I was in there."

### 2. HTTP API — `/api/*` (`TradeOfferController`)

Current endpoints, all under `/api`:

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/options` | — | `TradeOfferOptions` (star systems, offer types, price bounds) |
| GET | `/trade-offers` | — | `List<TradeOfferView>` — every offer, unscoped |
| POST | `/trade-offers` | `TradeOfferRequest` | `201` + `TradeOfferView`, or `400` with a plain-text reason |
| GET | `/trade-offers/resolve` | `turn`, `system`, `goods` query params | `TransactionView`, or `404` with a plain-text reason |

- **Breaking change**: removing/renaming a field in `TradeOfferView`, `TradeOfferRequest`, `TradeOfferOptions`, or `TransactionView`; changing a field's type or meaning; changing a success/error status code; removing an endpoint.
- **Required path**: none required yet (no external client exists), but any such change must be called out explicitly in the PR body ("Decisions touched" per `SDLC.md`), since the static frontend in `src/main/resources/static` is the one consumer and must be updated in the same PR.
- **Known planned break**: this entire surface is global/unscoped (no game id), which product brief problem statement and engineering standard #11 both flag as not meeting R01. The multi-game work in Scope/Now is expected to replace these endpoints with game-scoped equivalents (e.g. `/api/games/{gameId}/trade-offers`) — that is anticipated, tracked work, not a violation to block on today, but once a game-scoped surface ships, *that* becomes the protected one and this table must be updated in the same PR.

### 3. Login and session — `/api/login/*` (`LoginController`)

The passwordless login flow (D03). The sign-in screens (E01-S03) and every later endpoint that needs to know "this request is player X" depend on it.

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/login/request-code` | `{ playerName, corporationName, email }` | `202`, or `400` for a blank name or malformed email |
| POST | `/login/exchange-code` | `{ email, code }` | `200` + `{ sessionToken }`; `400` for a blank field; `401` for a wrong, expired, reused or never-requested code, or once the code's 5 attempts are used up |
| GET | `/login/session` | `Authorization: Bearer <sessionToken>` | `200` + `{ playerName, corporationName, email }`; `401` for a missing, malformed or unknown token |

- **Session lifecycle**: a session token is an opaque string and never expires (D05); sessions live in memory until the server restarts (D02 persistence is #21). A one-time code is valid for 10 minutes and for at most 5 attempts, and is used up by a successful exchange.
- **Breaking change**: renaming or removing a field above; changing a status code; changing how the token is sent (bearer header → cookie, say); giving sessions an expiry; letting a `401` reveal *which* reason applied.
- **In-repo consumers**: `static/sign-in.html` (request and exchange the code) and `static/js/session.js` (keeps the token in `localStorage` as `starTrader.sessionToken` and checks it with `GET /login/session` on every game screen). The sign-in screen maps E01-S01's 400 messages to its fields by their exact text (`static/js/sign-in.js`), so rewording one of those messages must update that map too.
- **Required path**: update the in-repo consumers above in the same PR, and state the change in the PR body's "Decisions touched". Session expiry would also need a superseding entry for D05.

### 4. Frontend/backend contract

`src/main/resources/static/index.html` is served as-is by Spring Boot's static resource handling and talks to `/api/*`. A change to the API response shape or the static file's expectations of it must keep both sides in sync within one PR — there is no versioning or feature-detection between them today.

### 5. Build and tooling contract

- `./gradlew build` is what CI (`.github/workflows/*.yml`) and this repo's validation gate (`SDLC.md`) run; it must keep working from a clean checkout with JDK 17.
- The frontend's JavaScript unit tests (`jsTest`, part of `check`) need Node 22+ on the PATH. CI installs it and never skips them; a local checkout without `node` skips them with a warning, so JDK 17 alone still builds.
- ktlint style rules (`build.gradle.kts`) are enforced as part of `check`; a change to `.editorconfig`/ktlint config that relaxes a rule project-wide is a deliberate decision, not a drive-by fix for one file.

### 6. Games — `/api/games` (`GameController`)

The game-scoped surface R01 asks for (E02). Each game has its own id; nothing in one game is reachable through another.

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/games` | `Authorization: Bearer <sessionToken>`, `{ turns }` | `201` + `{ id, turns, status, creatorEmail, players: [{ email, name, corporationName }] }`; `400` "Enter a whole number of turns from 1 to 50." for a missing, fractional or out-of-range length; `401` for a missing, malformed or unknown token |
| POST | `/games/{id}/join` | `Authorization: Bearer <sessionToken>`, no body | `200` + the same game shape, with the caller added to `players`; `404` "This game doesn't exist"; `409` "This game is full", "This game has already started" or "This player is already in the game"; `401` for a missing, malformed or unknown token |

- `status` is `LOBBY`, `RUNNING` or `FINISHED`; a new game is `LOBBY`. Players carry no money or ships until the game starts (E02-S05).
- Games live in memory until the server restarts (D02 persistence is #21).
- **Breaking change**: renaming or removing a field above; changing a status code; changing the `status` values; rewording a join `404`/`409` message, which E02-S05's lobby screen shows as is.
- **In-repo consumers**: none yet — the create and lobby screens arrive with E02-S05.

## Not yet applicable (no contract exists)

These will become protected surfaces once they exist, per the product brief's Scope/Now — call this file out for an update in the PR that first introduces each:

- **Persistence schema** — no document store exists yet (D02); once it does, the per-game document shape becomes a protected surface needing a migration/versioning story.
- **Confidential-data authorization** — the session→player→game check from product-brief assumption A05 doesn't exist yet; once it does, loosening it is always a blocker-severity review finding (see `CODE_REVIEW.md`), never a plain compatibility note.

## Required path for any breaking change, in general

1. State the break explicitly in the PR body (what surface, what changes, who/what consumes it).
2. Update every in-repo consumer in the same PR (currently: the static frontend).
3. If the surface is tied to a product-brief business rule or decision, add a superseding entry there per `SDLC.md`'s "Amending this process" / the product brief's own change-control table.
4. Prefer additive changes (new field, new endpoint) over renaming/removing while any real consumer might exist.
