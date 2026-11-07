# Login sessions survive a server restart

## 📝 TLDR

Games have lived in DynamoDB since E02-S03, but login sessions still live in `InMemorySessionStore`, so every restart or
redeploy signs every player out. This spec stores sessions in the same DynamoDB table as games, so a token the browser
already keeps (`localStorage`, `static/js/session.js`) stays valid across restarts. No API or frontend change.

## 📝 Problem Statement

`login/adapter/memory/InMemorySessionStore` is a `ConcurrentHashMap`, lost on every JVM restart. `[PRODUCT]` After a
restart the browser still holds its token, `GET /api/login/session` answers `401`, and the player must request and type a
new email code. The creator hit this while testing PR #70 (2026-11-02) and asked for a more durable session.

## 📝 Decisions

- **Same table as games** (creator, 2026-11-02). Each session is one item in `star-trader-games` (`DYNAMODB_TABLE_NAME`),
  so nothing new is provisioned in AWS. Considered: a separate sessions table, cleaner but one more table to create
  outside the app before a deploy.
- **The token is not stored.** The item's key is `session#` + the SHA-256 of the token (hex), so a copy of the table
  can't be replayed as live sessions. A UUID token has 122 random bits, so an unsalted hash is enough.
- **No expiry, as today.** `BACKWARD_COMPATIBILITY.md` §3 says a token never expires (D05) and lists "giving sessions an
  expiry" as a breaking change. This spec only makes sessions durable; an expiry or sign-out would be its own decision.
- **Port unchanged.** `SessionStore` (`store`, `find`) stays as it is; `DynamoDbSessionStore` replaces
  `InMemorySessionStore` as the only production adapter (the same swap E02-S03 made for games). `FakeSessionStore`
  stays for domain tests (engineering standards §4).

## 📝 Architecture

- `login/adapter/dynamodb/DynamoDbSessionStore` implements `SessionStore` over the shared `DynamoDbClient`.
  `find` is a consistent read, so a session is usable the moment the code exchange returns.
- `login/adapter/dynamodb/SessionItem`: `SessionItem(session)` and `SessionItem(attributes).toSession(token)`
  (engineering standards §5, §12). Attributes: `id` (`session#<sha256>`), `playerName`, `corporationName`, `email`.
- `login/adapter/dynamodb/SessionStoreConfiguration` builds the bean from the same `star-trader.dynamodb.table-name`.
  Table creation on DynamoDB Local stays in `game/adapter/dynamodb/DynamoDbConfiguration`; the table has only the `id`
  hash key, which sessions share.
- `DynamoDbGameStore.find` treats an id that isn't a game id (contains `#`) as not found, so `GET /api/games/session%23…`
  is a `404`, never a session item parsed as a game. Game ids are UUIDs and never contain `#`.

## 📝 Acceptance criteria

1. A player who signed in before a server restart is still signed in after it: `GET /api/login/session` with the same
   token answers `200` with the same name, corporation and email.
2. The stored item doesn't contain the token.
3. An unknown token still answers `401`, as before.
4. `GET /api/games/{id}` for a session item's id answers `404`.
5. `./gradlew build` passes; the DynamoDB-backed tests run against DynamoDB Local (Testcontainers).

## 📝 Compatibility

Additive. The HTTP contract is unchanged; the table gains a second item kind (`session#…` ids). Sessions created before
this ships were in memory and are gone after the deploy that ships it, so players sign in once more, then never again on
a restart. `BACKWARD_COMPATIBILITY.md` §3 and §7 and `AGENTS.md` (Run locally) are updated to say sessions persist.
