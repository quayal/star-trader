# The Game log outgrows one DynamoDB item, and one place maps refusals to HTTP statuses

**Brief:** `.ai/specs/product-brief.md` — R41 (a game lasts up to 50 turns), D02 (document store), E02-S03 (a game survives a restart), E03-S06 (the Game log).
**Source:** the project review of 2026-11-16, findings 1 and 4; the creator asked for both to be fixed.
**Standards:** `.ai/engineering-standards.md` §9 (atomic changes), §12 (stored data is mapped field by field).

## 📝 TLDR

Two technical fixes, no rule changes.

1. **The log.** A game is one DynamoDB item, and that item held the whole Game log. Every good resolved adds about five lines, so a turn adds 5 KB or more (an estimate from the log's text), and a 50-turn game approaches DynamoDB's 400 KB limit for one item, after which no change to the game can be saved. The log is now stored **one item per turn**, beside the game item, so no item grows past one turn's lines. The 2-second poll also stops carrying the whole log: the page says how many entries it has and gets only the newer ones.
2. **Refusals.** Every endpoint of `GameController` repeated the same `try`/`catch` ladder turning a refusal into a status. One `GameExceptionAdvice` does it now. No status or message changes.

## 📋 Acceptance criteria

### The log is stored per turn

1. The game item no longer holds `log`. It holds `logTurns`, the turns that have a log item. Each of those is its own item, `log#{gameId}#{turn}`, holding that turn's entries in order.
2. A change to a game writes the game item and only the log items of the turns whose entries changed, in **one transaction**, still conditional on the game item's version (§9). A change that adds no log line writes the game item alone, as before.
3. Reading a game reads its item, then its log items with a consistent read, and puts the entries back in turn order. `GameStore`'s contract and `Game` are unchanged: a game still carries its whole log.
4. A game saved before this change, with `log` inside its item, still loads. Its next save moves the log into per-turn items and drops the inline one (BACKWARD_COMPATIBILITY.md §7).
5. A test saves and reloads a game whose log is well over 400 KB.
6. Concurrent changes still apply exactly once each (the existing concurrency tests pass against the transactional write).

### The poll sends only new log entries

7. `GET /api/games/{id}?logAfter={n}` answers with the log entries after the first `n`, and `logOffset: n`. Without the parameter, or with an `n` the log doesn't have, it answers with the whole log and `logOffset: 0`, as before. Every other endpoint answers with the whole log.
8. The page asks for entries after the ones it has for that game and joins them; if what comes back doesn't follow on from what it has, it asks again for the whole log. Every screen keeps seeing a whole log.

### Refusals map to statuses in one place

9. `GameExceptionAdvice` maps: no such game → `404`; not a player, not the creator → `403`; a request the player can correct → `400`; out of turn, after the phase closed, or in the wrong state → `409`. The body is the refusal's message.
10. `POST /api/games` keeps answering `400` for a length that can't be used: the one place a game-state refusal is the caller's to correct.
11. Every existing controller test passes unchanged.

## Defaults I chose

- **Per turn, not per entry:** a turn's lines are written together and never change once the turn is over, and 50 items is well inside one batch read (100) and one transaction (100).
- **No new table or index:** log items share the games table under an id with `#`, as sessions do.
- **Reading stays whole.** Every read still fetches all of a game's log items; only the response got smaller. Skipping finished turns on the server needs `Game` to stop carrying its whole log, which is a bigger change than this fix.
- **The two reads aren't one snapshot.** A poll that lands between another player's write may see that write's log lines one poll before its state. A change is still safe: its transaction checks the version.
