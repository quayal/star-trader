# Backward compatibility

What this project treats as a protected contract surface, and how a change to one must be handled. Review skills check changes against this file; implementation skills warn when a change isn't compliant.

## Context: this is a pre-launch, single-maintainer project

Per `.ai/specs/product-brief.md`, there are no real players or external clients yet, and the current API is explicitly **expected** to be replaced as multi-game hosting (R01), authentication (D03) and persistence (D02) land. Treat the surfaces below as "don't break without noticing", not "frozen forever" — but still require every breaking change to be a deliberate, stated decision (ideally recorded in the product brief's Decisions table), not an accident.

## Protected surfaces today

### 1. Game rules (highest protection)

`StarSystem`, `Goods`, `OfferType`, and the resolution logic in `TradeDesk` / `BestOfferCalculator` / `SDCalculator` / `DiceRoller` encode the original board game's actual rules (business rule R03: six star systems, four tradable goods, dice-resolved trades). These are the closest thing this project has to a stable contract, because they're fixed by the board game itself, not by this codebase's current stage.

- **Breaking change**: altering a star system's name/goods, a good's identity, the dice mechanic, or the offer-matching/pricing logic in a way that changes a game outcome for the same inputs.
- **Required path**: must come with a test proving the new behavior against the original game's rule, and a note in the PR body explaining why the rule changed (new house rule, bug fix against the original, etc.) — not silently "while I was in there."

### 2. HTTP API — `/api/options` (`TradeOfferController`)

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/options` | — | `TradeOfferOptions` (star systems with their fixed market, offer types, price bounds) |

- Consumers: the home "Market Overview" in `static/index.html` and `static/design-system.html`.
- **Breaking change**: removing/renaming a field in `TradeOfferOptions`, `StarSystemOption` or `GoodsMarket`; changing a field's type or meaning; removing the endpoint.
- **Required path**: update both consumers in the same PR and call the change out in the PR body.
- **Removed in E03-S03**: the unscoped `GET /trade-offers` (listed every player's offers to anyone, against R17/A05), `POST /trade-offers` (no game, no session) and `GET /trade-offers/resolve`, with `static/trade-offers.html`, their only consumer. Offers are placed per game with `POST /api/games/{id}/offers` (§6), the replacement this section anticipated.

### 3. Login and session — `/api/login/*` (`LoginController`)

The passwordless login flow (D03). The sign-in screens (E01-S03) and every later endpoint that needs to know "this request is player X" depend on it.

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/login/request-code` | `{ playerName, corporationName, email }` | `202`, or `400` for a blank name or malformed email |
| POST | `/login/exchange-code` | `{ email, code }` | `200` + `{ sessionToken }`; `400` for a blank field; `401` for a wrong, expired, reused or never-requested code, or once the code's 5 attempts are used up |
| GET | `/login/session` | `Authorization: Bearer <sessionToken>` | `200` + `{ playerName, corporationName, email }`; `401` for a missing, malformed or unknown token |

- **Session lifecycle**: a session token is an opaque string and never expires (D05); sessions are stored in the games table (§7) and survive a server restart, so a token kept in the browser stays valid (2026-11-02). A one-time code is valid for 10 minutes and for at most 5 attempts, and is used up by a successful exchange.
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
| POST | `/games/{id}/bidding/done` | `Authorization: Bearer <sessionToken>`, no body | `200` + the game; the caller is added to `turn.doneBidding` (a repeat changes nothing), and when they're the last player, Bidding closes and `turn.phase` is `SHIP_JUMPS` (E03-S01); `409` "Bidding has closed" once it has (A04), "This game hasn't started" or "This game is over"; `403` "You are not a player in this game"; `404` "This game doesn't exist"; `401` for a missing, malformed or unknown token |
| POST | `/games/{id}/offers` | `Authorization: Bearer <sessionToken>`, `{ system, goods, type, price }` (`StarSystem`, `Goods` and `BUY`/`SELL` names; `price` a number) | `201` + the game, with the offer in the caller's `you.offers` (E03-S03); `400` "Choose a star system.", "Choose a good.", "Choose Buy or Sell.", "Price must be a whole number from 1 to 20." (also for a missing or fractional price), "You already have an offer on {GOODS} at {System} this turn." (R33), "{GOODS} isn't traded at {System}." or "You're Dictator of {GOODS} here — you place that offer when it resolves in Trading." (R38); `409` "Bidding has closed" once it has (A04), "This game hasn't started" or "This game is over"; `403` "You are not a player in this game"; `404` "This game doesn't exist"; `401` for a missing, malformed or unknown token |
| PUT | `/games/{id}/bidding/bid` | `Authorization: Bearer <sessionToken>`, `{ amount }` (a number) | `200` + the game, with the caller's bid in `you.bid`; placing it again replaces it (E03-S05); `400` "Enter a whole number of HT, 0 or more." (also for a missing or fractional amount), "A bid can't be negative. Enter 0 to bid nothing." or "You have {money} HT. Enter {money} or less."; `409` "Bidding has closed" once it has (A04), "This game hasn't started" or "This game is over"; `403` "You are not a player in this game"; `404` "This game doesn't exist"; `401` for a missing, malformed or unknown token |
| POST | `/games/{id}/ship-jumps/turn-order` | `Authorization: Bearer <sessionToken>`, `{ place }` (a number, 1 = first) | `200` + the game, with the caller's email at `turn.turnOrder[place - 1]`; when only one player is left to pick, they get the remaining place at once (E04-S01); `400` "Choose a free place in the turn order." (also for a missing or fractional place); `409` "It's {corporation}'s turn to choose a place." out of turn, "The turn order is already set.", "Ship-jumps hasn't started." during Bidding, "Ship-jumps has closed" after it, "This game hasn't started" or "This game is over"; `403` "You are not a player in this game"; `404` "This game doesn't exist"; `401` for a missing, malformed or unknown token |
| POST | `/games/{id}/start` | `Authorization: Bearer <sessionToken>`, no body | `200` + the started game; `403` "Only the creator can start the game" for anyone else; `409` "At least 2 players are needed to start" or "This game has already started"; `404` "This game doesn't exist"; `401` for a missing, malformed or unknown token |

**Game shape** (every `200`/`201` above): `{ id, turns, status, creatorEmail, players: [{ email, name, corporationName, ships: [{ name, system, systemName, location }], marketPositions: [{ system, goods, tier }] }], turn: { number, phase, doneBidding: [email], initiative: [email], turnOrder: [email | null] } | null, log: [{ turn, text }], board: [{ id, name, portClass, legalSystem, police, security, market: [{ goods, price, indicator }] }], you: { money, ships: [{ name, cargo: { GOODS: units }, cargoUsed, cargoSpaces }], offers: [{ system, systemName, goods, type, price }], bid: N | null } | null }`. `players[]` is public and never carries money (R29) or cargo (S02-7); `you` is the caller's own money and their own ships' cargo. `system` is a `StarSystem` constant (e.g. `TAU_CETI`), `location` is `PORT`, `PLANET` or `SPACE`, `phase` is `BIDDING`, `SHIP_JUMPS` or `TRADING`. `doneBidding` lists the emails of players who signalled done in this turn's Bidding (public, D09) and is empty in other phases. `board` lists the six systems (`id` is the `StarSystem` constant) with their R31 location statistics and market, goods in name order; `indicator` is the S/D number, which the client steps into a market label (D08). `marketPositions` is public (R28): `tier` is `RETAILER`, `WHOLESALER` or `DICTATOR`, and a player holds none until they earn one. `you.ships` follows `players[]`'s order for the caller's ships; `cargo` lists only goods carried (so an empty ship has `{}`), `cargoUsed` counts POLY and ISO units (R42) and `cargoSpaces` is 10 (E03-S02). `you.offers` is the caller's own offers of this turn in the order placed (`type` is `BUY` or `SELL`); it stays through Ship-jumps and Trading and is empty again in the next turn's Bidding (E03-S03). `you.bid` is the caller's own initiative bid this turn, or null if they haven't placed one; it stays through the turn and is null again in the next turn's Bidding. When Bidding closes, every player's money goes down by their bid (all-pay, R27), `turn.initiative` lists every player's email in initiative order (public, empty during Bidding) and `log` gains "Initiative order (bid + d2): {corporation} → …", without amounts (E03-S05). `turn.turnOrder` is this turn's turn order (public, R26): one entry per place, first to last, each the email of the player who picked it or null while free. It's empty during Bidding and has one entry per player from Ship-jumps on; players pick in initiative order, so whose pick it is is the first `turn.initiative` email not yet in `turnOrder`. Each pick adds "{corporation} chose 2nd" to `log`, the last player's "{corporation} gets 3rd, the only place left", then "Turn order (for this turn): {corporation} → …" (E04-S01). `log` is the game's public events, oldest first; each `turn` is the turn it happened in, which the Game log screen (s04) groups by (E03-S06). It never carries an offer, a bid, money or anything else confidential (R17, R29). It changed from `[string]` in E03-S06, with `index.html` updated in the same PR.

- `status` is `LOBBY`, `RUNNING` or `FINISHED`; a new game is `LOBBY` with `turn: null`, empty `log`, no ships and 0 money. The start (E02-S05) sets 300 HT, two ships at Ports and `turn: { number: 1, phase: BIDDING }`, and adds two log lines under turn 1. Phases then run Bidding → Ship-jumps → Trading → the next turn's Bidding; after the last turn's Trading `status` is `FINISHED` and `turn` keeps the last turn (E03-S01).
- Games are stored in DynamoDB and survive a server restart (E02-S03); see §7 for the stored shape.
- **Breaking change**: renaming or removing a field above; changing a status code; changing the `status` or `phase` values; rewording "Bidding has closed", which the game screens show as is; rewording a join or poll `403`/`404`/`409` message, which E02-S05's lobby screen shows as is; letting the poll return anything to a non-player (A05); putting any player's money, cargo, offers or bid outside `you` (R29, S02-7, R17); rewording an offer or bid `400` message, which the forms show as is.
- **In-repo consumers**: `static/index.html` (s11 create, s12 lobby, the game view) through `static/js/game.js` and `static/js/board.js`, which read every field above and shows the `403`/`404`/`409` messages as is.

### 7. Persistence schema — the DynamoDB games table (`game/adapter/dynamodb`)

Stored data outlives the code that wrote it, so the item shape is a contract with every future version of the app.

| Attribute | Type | Meaning |
|---|---|---|
| `id` | S, partition key | the game id (a UUID; ids containing `#` are other kinds of item, see sessions below) |
| `turns` | N | game length |
| `status` | S | `LOBBY`, `RUNNING` or `FINISHED` |
| `creatorEmail` | S | the creator's email |
| `players` | L of M | each `{ email: S, name: S, corporationName: S, money: N, ships: L of M, marketPositions: L of M }`; `money` is 0 and `ships` empty until the start |
| `players[].ships` | L of M | each `{ name: S, system: S, location: S, cargo: M }`; `system` is a `StarSystem` constant (e.g. `TAU_CETI`), `location` is `PORT`, `PLANET` or `SPACE`; `cargo` maps a `Goods` constant to its units (N), added in E03-S02 and read as empty when missing from an older item |
| `players[].marketPositions` | L of M | each `{ system: S, goods: S, tier: S }` (`StarSystem`, `Goods` and `MarketTier` constants); added in E03-S02 and read as empty when missing from an older item |
| `turn` | M or NULL | `{ number: N, phase: S, doneBidding: L of S, offers: L of M, bids: L of M, initiative: L of S, turnOrderPicks: L of M }` once the game has started (`phase` is `BIDDING`, `SHIP_JUMPS` or `TRADING`; `doneBidding` holds emails, added in E03-S01 and read as empty when missing from an older item); NULL before |
| `turn.offers` | L of M | each `{ playerEmail: S, system: S, goods: S, type: S, price: N }` (`StarSystem`, `Goods` and `OfferType` constants), in the order placed; secret to its player (R17). Added in E03-S03 and read as empty when missing from an older item |
| `turn.bids` | L of M | each `{ playerEmail: S, amount: N }`, at most one per player; secret to its player (R29). Added in E03-S05 and read as empty when missing from an older item |
| `turn.initiative` | L of S | every player's email in initiative order, set when Bidding closes (R27). Added in E03-S05 and read as empty when missing from an older item |
| `turn.turnOrderPicks` | L of M | each `{ playerEmail: S, place: N }` (`place` 1 = first), in pick order; public (R26). Added in E04-S01 and read as empty when missing from an older item |
| `log` | L of M | public events, oldest first, each `{ turn: N, text: S }` (`LogEntryItem`), `turn` being the turn it happened in. Before E03-S06 each element was a plain S; an element that's still an S reads as turn 1 |
| `version` | N | optimistic-concurrency counter, +1 on every save |

- Table `star-trader-games` (`DYNAMODB_TABLE_NAME`), on-demand billing, no secondary index. In real AWS it's provisioned outside the app.
- **Additive change** (a new attribute, a new field in a player map): safe once its item class defaults it when reading an older item.
- **Breaking change**: renaming or removing an attribute, changing its type or the meaning of a value (including renaming a `StarSystem`, `ShipLocation`, `GameStatus`, `TurnPhase`, `Goods`, `MarketTier` or `OfferType` constant, since they're stored by name), changing the key. Needs a migration of stored items, or — before any game worth keeping exists — an explicit note in the PR that local data must be cleared (`docker compose down -v`).
- **Sessions** share the table, one item per login session (`login/adapter/dynamodb/SessionItem`): `id` is `session#` plus the SHA-256 (hex) of the session token, so the token itself is never stored; `playerName`, `corporationName` and `email` are S. Changing how the id is derived from the token signs every player out, so it needs the same care as changing the key.
- **Required path**: change the type's item class (`GameItem`, `PlayerItem`, `ShipItem`, `MarketPositionItem`, `TurnItem`, `OfferItem`, `InitiativeBidItem`, `TurnOrderPickItem`, `LogEntryItem`, `SessionItem`) and its round-trip test together (engineering standards §12), and state the change in the PR body.

## Not yet applicable (no contract exists)

These will become protected surfaces once they exist, per the product brief's Scope/Now — call this file out for an update in the PR that first introduces each:

- **Confidential-data authorization** — the session→player→game check from product-brief assumption A05 doesn't exist yet; once it does, loosening it is always a blocker-severity review finding (see `CODE_REVIEW.md`), never a plain compatibility note.

## Required path for any breaking change, in general

1. State the break explicitly in the PR body (what surface, what changes, who/what consumes it).
2. Update every in-repo consumer in the same PR (currently: the static frontend).
3. If the surface is tied to a product-brief business rule or decision, add a superseding entry there per `SDLC.md`'s "Amending this process" / the product brief's own change-control table.
4. Prefer additive changes (new field, new endpoint) over renaming/removing while any real consumer might exist.
