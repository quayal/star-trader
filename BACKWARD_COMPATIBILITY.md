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

- **Session lifecycle**: a session token is an opaque string and never expires (D05); sessions live in memory and are lost when the server restarts (E02-S03 persisted games only), so players sign in again after a restart. A one-time code is valid for 10 minutes and for at most 5 attempts, and is used up by a successful exchange.
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
| POST | `/games` | `Authorization: Bearer <sessionToken>`, `{ turns }` | `201` + the game shape below, built for the caller; `400` "Enter a whole number of turns from 1 to 50." for a missing, fractional or out-of-range length; `401` for a missing, malformed or unknown token |
| POST | `/games/{id}/join` | `Authorization: Bearer <sessionToken>`, no body | `200` + the same game shape, with the caller added to `players`; `404` "This game doesn't exist"; `409` "This game is full", "This game has already started" or "This player is already in the game"; `401` for a missing, malformed or unknown token |
| GET | `/games/{id}` | `Authorization: Bearer <sessionToken>` | `200` + the same game shape, for any player in the game; `403` "You are not a player in this game" for a signed-in caller who isn't (no game data in the body); `404` "This game doesn't exist"; `401` for a missing, malformed or unknown token |
| POST | `/games/{id}/start` | `Authorization: Bearer <sessionToken>`, no body | `200` + the started game; `403` "Only the creator can start the game" for anyone else; `409` "At least 2 players are needed to start" or "This game has already started"; `404` "This game doesn't exist"; `401` for a missing, malformed or unknown token |

**Game shape** (every `200`/`201` above): `{ id, turns, status, creatorEmail, players: [{ email, name, corporationName, ships: [{ name, system, systemName, location }] }], turn: { number, phase } | null, log: [string], you: { money } | null }`. `players[]` is public and never carries money (R29); `you` is the caller's own money. `system` is a `StarSystem` constant (e.g. `TAU_CETI`), `location` is `PORT`, `PLANET` or `SPACE`, `phase` is `BIDDING`.

- `status` is `LOBBY`, `RUNNING` or `FINISHED`; a new game is `LOBBY` with `turn: null`, empty `log`, no ships and 0 money. The start (E02-S05) sets 300 HT, two ships at Ports and `turn: { number: 1, phase: BIDDING }`, and adds two log lines.
- Games are stored in DynamoDB and survive a server restart (E02-S03); see §7 for the stored shape.
- **Breaking change**: renaming or removing a field above; changing a status code; changing the `status` values; rewording a join or poll `403`/`404`/`409` message, which E02-S05's lobby screen shows as is; letting the poll return anything to a non-player (A05); putting any player's money outside `you` (R29).
- **In-repo consumers**: `static/index.html` (s11 create, s12 lobby, the game view) through `static/js/game.js`, which reads every field above and shows the `403`/`404`/`409` messages as is.

### 7. Persistence schema — the DynamoDB games table (`game/adapter/dynamodb`)

Stored data outlives the code that wrote it, so the item shape is a contract with every future version of the app.

| Attribute | Type | Meaning |
|---|---|---|
| `id` | S, partition key | the game id |
| `turns` | N | game length |
| `status` | S | `LOBBY`, `RUNNING` or `FINISHED` |
| `creatorEmail` | S | the creator's email |
| `players` | L of M | each `{ email: S, name: S, corporationName: S }` |
| `version` | N | optimistic-concurrency counter, +1 on every save |

- Table `star-trader-games` (`DYNAMODB_TABLE_NAME`), on-demand billing, no secondary index. In real AWS it's provisioned outside the app.
- **Additive change** (a new attribute, a new field in a player map): safe once the mapper defaults it when reading an older item.
- **Breaking change**: renaming or removing an attribute, changing its type or the meaning of a value, changing the key. Needs a migration of stored items, or — before any game worth keeping exists — an explicit note in the PR that local data must be cleared (`docker compose down -v`).
- **Required path**: change `GameItemMapper` and `GameItemMapperTest` together (engineering standards §12), and state the change in the PR body.

## Not yet applicable (no contract exists)

These will become protected surfaces once they exist, per the product brief's Scope/Now — call this file out for an update in the PR that first introduces each:

- **Confidential-data authorization** — the session→player→game check from product-brief assumption A05 doesn't exist yet; once it does, loosening it is always a blocker-severity review finding (see `CODE_REVIEW.md`), never a plain compatibility note.

## Required path for any breaking change, in general

1. State the break explicitly in the PR body (what surface, what changes, who/what consumes it).
2. Update every in-repo consumer in the same PR (currently: the static frontend).
3. If the surface is tied to a product-brief business rule or decision, add a superseding entry there per `SDLC.md`'s "Amending this process" / the product brief's own change-control table.
4. Prefer additive changes (new field, new endpoint) over renaming/removing while any real consumer might exist.
