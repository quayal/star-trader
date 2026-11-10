# Execution plan: E05-S06 — Trading 6 or more units can raise a player's market position

Source doc: .ai/specs/2026-10-29-e05-trading-phase.md
Issue: #50

## Goal

After one offer's trade of at least 6 units of a good at a system, roll 2d6; a roll at most the units traded moves the
trader one tier up for that good there: none → Retailer → Wholesaler → Dictator (S06-2, R43). A Dictator can't go
higher, and a Wholesaler who becomes Dictator demotes the previous Dictator to Wholesaler (S06-3). The roll and its
outcome are public on s03 and in the log, "Vega Lines traded 7 u — rolled 5: now Wholesaler of ISO at Mu Herculis from
turn 5" / "rolled 9: no change" (S06-4). Every change counts from the next turn; for the rest of this turn everyone keeps
the position they had (S06-5). s02 shows positions per good at each system (S06-1).

## What already works (checked before planning)

- E05-S01 to S05 are merged (PRs #88–#92). `Game.trade` settles one offer's trade and logs it; `Resolution.trades`
  records each, and the wholesale step's `wholesalers` are fixed when the good starts resolving, from the positions
  then (the S05 code already cites S06-5).
- `Player.marketPositions` is stored (`MarketPositionItem`), sent publicly, and s02's "Market positions" table
  (`board.js` `marketPositions`) already lists the holders of each tier per good at a system. S06-1 is therefore
  already met for positions in effect; what's missing is a change waiting for the next turn.
- `Game.advancePhase` from Trading moves to the next turn's Bidding (`Turn.next()`), but nothing in production calls
  it from Trading yet: that's E05-S07.

## Scope

- **Domain** (`game`):
  - `PositionRoll(roll, promotedTo, demoted)` on each `Trade` (null when no roll). `MarketTier.next()` and
    `displayName`.
  - `Player.pendingPositions`: the changes rolls made this turn. `tierAt`, `canRiseAt`, `promotionAt`, `promotedAt`,
    `willBeDictatorOf`, `demotedAt`, `applyPendingPositions`.
  - `Game.trade(…, dice)` rolls after 6+ units and logs the outcome after the trade line. `Game.applyPendingPositions()`
    is the single entry point that makes the pending changes count; `advancePhase` calls it when Trading moves to the
    next turn.
- **Adapters**: `PlayerItem.pendingPositions`, `TradeItem.positionRoll` (`PositionRollItem`), both absent on older
  items. `PlayerResponseDto.pendingPositions`, `TradeResponseDto.positionRoll`. `BACKWARD_COMPATIBILITY.md` §6, §7.
- **Frontend**: s03's market lines show each roll after its trade (`trading.js` `counterLines`); s02's positions table
  shows a pending change beside the tier held now (`board.js` `marketPositions`). s04 shows the server's log line.

## Non-goals

- The turn's end (S07) and the game's end (S08), which another run is building at the same time. This run doesn't
  touch how or when the turn advances beyond calling `applyPendingPositions()` in the existing Trading → next turn
  branch of `advancePhase`.

## Decisions (autonomous, reversible)

- **The pending change lives on the player** (`Player.pendingPositions`), not on the turn, so `Turn.next()` can't drop
  it and S07 needs one call, `Game.applyPendingPositions()`, wherever it starts the next turn.
- **A Dictator doesn't roll.** S06-3: they can't go higher, so the roll could change nothing; no log line either.
- **The base tier is the one held this turn** (S06-5): a pending change never stacks. A player trades a good at a
  system at most once per turn anyway; the wholesale step is at most 5 u, so it never rolls.
- **Two Dictators in one turn: the later roll wins.** Becoming Dictator demotes everyone who is or was about to be its
  Dictator from the next turn: the current Dictator, and anyone promoted to Dictator earlier this turn (whose pending
  change then becomes "stays Wholesaler"). The log names each: "…; Altair Trade Co. is demoted to Wholesaler from turn
  5".
- **The demotion is part of the roll's log line**, not a line of its own, so the cause and effect read together.
- **s02 shows a pending change beside the current tier**: "Vega Lines (Wholesaler from turn 5)" under Dictator; a first
  position under its new tier, "Kessler Cargo (from turn 5)". Showing the player in two columns would read as two
  positions.
- **The last turn still rolls** and logs "from turn {last + 1}". R43 doesn't exempt it, and positions are worth nothing
  once the game ends (R39). Worth a look in S08.

## Implementation Plan

### Phase 1: Domain

1. `PositionRoll`, `MarketTier.next()`, `Trade.positionRoll`. Tests: `PositionRollTest`.
2. `Player.pendingPositions` and its methods. Tests: `PlayerTest`.
3. `Game.trade` rolls and logs; `Game.applyPendingPositions()`; `advancePhase` calls it. Tests: `GamePositionRollTest`,
   existing trade tests given their rolls.

### Phase 2: Adapters

1. Persistence of pending positions and position rolls, with older items. `PlayerItemTest`, `TurnItemTest`.
2. Web: the two new fields. `GameResponseDtoTest`, `GameControllerTest`.

### Phase 3: Frontend

1. `trading.js`: the roll line. `board.js`: pending positions on s02. Tests in `trading.test.mjs`, `board.test.mjs`.

### Phase 4: Verify

1. Full gate, then a browser walk with two players: a promotion to Dictator demoting the Dictator, a roll with no
   change, the wholesale step still asking the old Dictator, s02 and s04, phone width.

## Risks

- `Game.trade` takes `dice` now; every caller passes it. A trade of 12 u or more always promotes (2d6 ≤ 12).
- S07 merging first and replacing `advancePhase`'s Trading branch would drop the `applyPendingPositions()` call; the PR
  description names the entry point, and `GamePositionRollTest.shouldApplyThePendingPositionsWhenTheNextTurnStarts`
  fails if it's lost.

## Notes from the run

- **Docker was running**, so `GameControllerTest` and the DynamoDB tests ran: 516 Kotlin tests, 0 skipped; 189 JS tests.
- **Changed from the plan while building:** nothing of substance. S06-1 needed no new screen: the s02 table existed
  since E03-S02, and this run adds the pending changes to it.
- **Browser walk** (agent-browser 0.35.2, two players, own DynamoDB Local on 8106 and app on 3106 with
  `RESEND_API_KEY=` blank; game set up through the API; Vega Lines seeded as Dictator and Orion Freight as Wholesaler of
  Tau Ceti ISO in DynamoDB; both had a ship in port at Tau Ceti):
  - Vega Lines, Dictator, got "You're Dictator of ISO here. Place an offer before the roll?" and passed on s03.
  - Orion Freight bought 12 ISO at 20 HT on s03. s03 then read "Orion Freight could buy up to 16 u: bought 12 u at 20 HT
    each, paid 240 HT", "Orion Freight traded 12 u — rolled 7: now Dictator of ISO at Tau Ceti from turn 2; Vega Lines
    is demoted to Wholesaler from turn 2", then the end and price lines (S06-2, S06-3, S06-4).
  - The wholesale step then asked "Waiting for Vega Lines (Dictator) to trade up to 5 u at 9 HT, or pass.": the old
    Dictator still counts this turn (S06-5).
  - Vega Lines bought 6 POLY at Tau Ceti: "Vega Lines traded 6 u — rolled 7: no change" on s03.
  - s02 for Tau Ceti, Market positions: ISO — Dictator "Vega Lines (Wholesaler from turn 2)", Wholesaler "Orion Freight
    (Dictator from turn 2)"; POLY — "Nobody holds a position for this good yet" (S06-1).
  - s04 had the same roll lines, in order after each trade.
  - At 390 px s03's roll lines wrapped with no horizontal scroll (scrollWidth 390).
  - Not walked: the change taking effect in turn 2, since nothing in the app advances from Trading until S07. It's
    covered by `GamePositionRollTest.shouldApplyThePendingPositionsWhenTheNextTurnStarts` through `advancePhase`.

## Progress

PR: (set when opened)

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [x] 1.1 PositionRoll, MarketTier.next and the trade's roll — 94b5e86
- [x] 1.2 Player's pending positions — 94b5e86
- [x] 1.3 Game rolls, logs and applies pending positions — 94b5e86

### Phase 2: Adapters

- [x] 2.1 Persistence of pending positions and position rolls — 94b5e86 (with Phase 1: the new fields don't compile without it)
- [x] 2.2 Web: pending positions and the trade's roll in the game shape — 94b5e86

### Phase 3: Frontend

- [x] 3.1 s03 roll lines and s02 pending positions — 381b427

### Phase 4: Verify

- [x] 4.1 Full gate and browser walk — 381b427
