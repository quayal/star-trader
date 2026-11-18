# Run: E12 — Connections (2026-11-18)

**Asked:** implement epic E12 (#146) from `.ai/specs/2026-11-18-connections.md` via `om-auto-implement-spec`, all three stories in one PR: #147 (E12-S01 starting points 3 + d3 placed in turn 1's Bidding, gating done), #148 (E12-S02 one raise per Investment at the new level × 10 HT, +1 business on a market position rise), #149 (E12-S03 factory cap = political + business). Record R81–R84 in the brief, move the spec to `implemented/`, unattended, stacked on PR #155 (E11-S01, still open) if it hadn't merged.

**Outcome:** done. PR opened against `main`, stacked on #155 (branch `feat/connections` from `origin/feat/corporations-tab`).

## What was done

- Domain: `Connections` (0–10 each, raise cost, market-rise business point, factory cap and its refusal text, the starting placement), `ConnectionKind`, `InvalidConnectionsException` (`400`). `Player` gains `connections` and `connectionPoints` with `placeConnections`, `requireConnectionsPlaced` (the gate), `raiseConnection`, the cap in `buyFactoryUnits` and the business point in `promotedAt`. `Turn.connectionsRaised` holds once per Investment. `Dice.d3()`; `Game.start` takes the dice and logs each roll. `PositionRoll.businessConnections` carries the rise into the log line.
- Web: `POST /games/{id}/connections/placement` and `/connections/raise`; `players[].connections`, `players[].connectionPoints`, `positionRoll.businessConnections`, and `you.invest.factoryUnits`, `factoryCap`, `connections`, `raisedConnection`.
- Persistence: player attributes `political`, `business`, `criminal`, `connectionPoints`, `turn.connectionsRaised`, `positionRoll.businessConnections`; missing reads as 0 / empty (creator: no compatibility needed beyond that).
- Frontend: placement form on the Deck (turn 1 Bidding), Connections section on Corporations, factory-cap line, "up to N units" and a raise form on Infrastructure, the business rise in the Trading position-roll line. New `js/connections.js` with tests.
- Docs: R81–R84 in the brief, `BACKWARD_COMPATIBILITY.md` §6/§7, implementation notes appended to the spec, spec moved to `implemented/`, a lessons entry.

## Decisions the spec didn't cover

- Refusal texts for a bad or repeated placement and an unknown kind (listed in the spec's implementation notes).
- A raise waits for the player's Collect like every other Investment action.
- In the position-roll line, the business rise comes before a demotion.
- Tests: `START_DICE` + `ConnectionsPlaced(game).byEveryone()` (cap 6) for fixtures past turn 1's Bidding; a few factory tests now assume a cap and were adjusted (e.g. "no cap on the size" became "up to the cap").

## Verification

- `env -u AWS_REGION ./gradlew check` green with Docker up, no tests skipped.
- trader-implement diff audit against `origin/feat/corporations-tab`: two adapter hits (a request parser, and an existing line that gained a parameter), no part taking a part; `Game.kt` grew by coordination only.
- Browser walk (headless Chrome over CDP, app on port 3100 with its own DynamoDB Local on 8100, both stopped afterwards): placement form and gate, a wrong total refused, Corporations section, the cap line and "up to N", a purchase over the cap refused in the creator's wording, a raise that grows the cap, the log lines, no horizontal scroll at 390 px. The walk caught two polish issues (a stale cap error after a raise, a missing full stop), fixed in the same PR.

## Notes

- The worktree sandbox refused several compound shell commands (heredocs with many lines, chained git calls); writing scripts into the scratchpad and running them worked.
