# Execution plan: E05-S03 — The counter resolves the offers

Source doc: .ai/specs/2026-10-29-e05-trading-phase.md
Issue: #47

## Goal

Once a good's offers are disclosed and its Dictator, if any, has decided, the counter starts at the good's S/D plus a
2d6 roll (S03-1). The winning offer trades first (S03-2), then whichever untraded offer allows the most units at the
current counter (S03-3). Max comes from the start price, with R15 saturation (S03-4). Each unit moves the counter one
step (S03-5). s03 shows it in plain terms: "Market +3 → buyers first", each trade, then "No one else can trade at this
market" (S03-6). With nothing to trade, the resolution goes straight on (S03-7).

## What already works (checked before planning)

- E05-S01 (#46, PR #88) and E05-S02 (#51, PR #89) are merged. `Turn.resolveNext(dictator)` starts a `Resolution` that
  discloses the good's offers and asks the Dictator. Once nobody is asked, the resolution finishes and the good counts as
  resolved. **Resolve next** is refused while one is unfinished.
- `Dice` (game package) has only `d2()`. `RandomDice` is the Spring bean, `FakeDice` the test double.
- `SupplyDemand` (trading package, `internal`) holds the S/D → price-modifier table and `lowestFor`/`highestFor`. The
  spec's `SDCalculator` doesn't exist under that name any more; `SupplyDemand` is what the spec means. Its lookups throw
  for a modifier off the table, so R15 saturation isn't applied yet.
- Prices don't change in a game yet (S05), so a good's start price is `StarSystem.getPrice`, and its S/D is
  `StarSystem.getIndicator`.
- The old `trading` package's `TradeDesk.resolve` / `BestOfferCalculator` aren't reachable from any endpoint.
  `TradeOfferController` only reads `TradeDesk.MIN_PRICE`/`MAX_PRICE`.

## Scope

- **Domain** (`game`):
  - `Dice.twoD6()`.
  - `SupplyDemand.buyTarget` / `sellTarget` with R15 saturation (price modifier clamped to −6..+7).
  - `Offer.maxUnits(counter, startPrice)`.
  - On `Resolution`: `counter` (null until it starts), `trades` (one `Trade` per offer that traded, in order) and
    `startCounter(roll, initiative)`, which runs the loop.
  - The counter starts when nobody is asked any more: at **Resolve next** with no Dictator, and after the Dictator's
    offer or pass.
  - `Game` logs the start ("Mu Herculis ISO: market +3 → buyers first"), each trade ("Vega Lines bought 4 u at 11 HT
    each"), and the end ("No one else can trade at Mu Herculis ISO", or "No one can trade at Mu Herculis ISO").
  - `TradingService` gets `Dice`.
- **Adapters**:
  - `ResolutionItem` gains `counter` (absent until the counter starts) and `trades`. An older item has neither, which
    reads as null and empty and is correct: every S02 resolution finished without a counter.
  - The game shape's `turn.resolution` gains `counter` and `trades` (player, type, price, units).
- **Frontend**: s03's resolution panel adds the market line, one line per trade, and the end line under the offers.

## Non-goals

- The trader's amount choice, R12 money and R10/R16/R42 cargo caps, and money or cargo changing (S04). Until S04 each
  offer trades its full max, and no money or goods move.
- The price change (S05), Wholesalers (S05), position rolls (S06).
- Deleting or rewiring the old `trading` package (`TradeDesk`, `BestOfferCalculator`, `TransactionAmountCalculator`).
  Nothing calls them in a game. Removing them is its own cleanup.

## Decisions (autonomous, reversible)

- **Until S04, each offer trades its max.** S04 puts the trader's 1..max choice into the same loop. The loop is written
  one trade at a time (`nextTrade()`), so S04 can pause it for a decision.
- **No money or cargo moves in S03.** S04-7 owns "Your money updates", and S04-2..4 own the caps. A trade logged in
  S03 is a record of the counter loop, not a settlement.
- **If the winning side has no offer that allows ≥ 1 u, the first trade goes by the general rule (S03-3).** For example,
  the counter is ≥ 0 and nobody buys, but a seller can still sell. R34 says the winning offer trades first, but not what
  happens when it can't trade. Stopping would let a market with willing sellers end with no trades.
- **The roll happens when the counter starts, not at Resolve next.** The Dictator decides "before the roll" (R38), so
  the roll can't exist while they're asked.
- **Initiative order breaks ties** (R24, R34): the turn's `initiative`, not placement order.
- **Plain text, no dice arithmetic** (D07): "Market +3 → buyers first" / "Market −2 → sellers first". The roll itself
  isn't shown, only the counter. The market line uses a real minus sign (U+2212).
- **Logged** like the rest of the resolution (TLDR: "Everything is public and logged"). The trade line uses S04-6's
  wording, so S04 doesn't change it.

## Implementation Plan

### Phase 1: Domain

1. `Dice.twoD6()` (RandomDice, FakeDice). `SupplyDemand` buy/sell targets with R15 saturation, and
   `Offer.maxUnits(counter, startPrice)`. Tests: `SupplyDemandTest`, `OfferTest`, `RandomDiceTest`.
2. `Resolution.startCounter(roll, initiative)`: the counter, the first trade, the next trades, and stopping. Tests in
   `ResolutionTest` cover the spec's worked example and its variants, ties, the fallback, no offers, and nobody able to
   trade.
3. `Turn` and `Game` start the counter when nobody is asked any more, and log the market line, the trades and the end.
   `TradingService` takes `Dice`, and so does `GameController`'s wiring. Tests: `TurnTest`, `GameTradingTest`,
   `TradingServiceTest`.

### Phase 2: Adapters

1. Persistence: `ResolutionItem` gains `counter` and `trades`. A round trip test, and a test that an older item reads
   with no counter. `BACKWARD_COMPATIBILITY.md` §7.
2. Web: the game shape's `turn.resolution.counter` and `trades` (`GameResponseDtoTest`, `GameControllerTest`).
   `BACKWARD_COMPATIBILITY.md` §6.

### Phase 3: Frontend

1. `js/trading.js`: the market line, trade lines and end line. Tests in `src/test/js/trading.test.mjs`.
2. s03: those lines in the resolution panel, under the offers.

### Phase 4: Verify

1. Full gate (`./gradlew build`), then a browser walk with two players into Trading. Check a good with offers on both
   sides, a good with none, and a seeded Dictator good (the counter starts only after the decision). Also check the log,
   and phone width.

## Risks

- `FakeDice` is used by many tests. `twoD6()` gets its own queue, so tests that only roll d2 don't change.
- Turning the roll into a counter must stay hidden while a Dictator is asked (R38). Tested.
- The resolution panel is redrawn on every poll. The new lines are static text, so there's no form state to keep.

## Notes from the run

- **Changed from the plan while building:**
  - `Resolution.nextTrade(initiative)` is public, not private. S04 needs "who trades next, and their max" to ask the
    trader. It also lets `ResolutionTest` check S03-3's buyer–seller tie directly: while every offer trades its max, that
    tie can't happen. After a buy, only sellers can still trade, and after a sell, only buyers. It needs a partial
    trade, so S04.
  - The stored attribute is `startingCounter`, not `counter`, so it isn't confused with the counter now
    (`Resolution.counter()`, derived from the trades).
  - `GameControllerTest` runs with real dice, so its log assertions check only what doesn't depend on the roll. The
    Dictator case still checks its trades exactly: both offers saturate, so the buy takes `startingCounter + 18`.
- **Docker was running**, so `GameControllerTest` and the DynamoDB tests ran: 441 Kotlin tests, 0 skipped, plus 171 JS
  tests.
- **Browser walk** (agent-browser 0.35.2, two players, own DynamoDB Local on 8100 and app on 3100, game set up through
  the API, Orion seeded as Dictator of POLY at Gamma Leporis):
  - Gamma Leporis POLY: while Orion (Dictator) decided, neither player saw a Market section (R38). After Orion passed,
    both saw "Market +13 → buyers first", "Vega Lines bought 16 u at 3 HT each" (13 − (−3)), and "No one else can
    trade at this market." (S03-1, S03-4, S03-6).
  - Tau Ceti ISO: "Market −1 → sellers first". Orion's sell at 4 traded first, 2 u, though Vega's buy allowed more,
    then Vega bought 6 (S03-2, S03-3, S03-5).
  - Tau Ceti POLY, no offers: "No offers for this good.", "Market −3 → sellers first", "No one can trade at this
    market." (S03-7).
  - Mu Herculis ISO (the worked example's offers, roll 3): "Market +4", Vega bought 5 (to −1), then Orion sold 5. The
    game log carried the same lines with the good named.
  - At 390 px: no horizontal scroll, and the lines wrap.
  - The first app start picked up a real `RESEND_API_KEY` from the shell and tried to email the test players. Resend
    refused the example.com addresses, so nothing was sent. The app was restarted with the key blanked (see the lesson).

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [x] 1.1 Dice.twoD6, saturated targets and Offer.maxUnits — 851b3fa
- [x] 1.2 Resolution counter loop — abacee3
- [x] 1.3 Turn, Game and TradingService start the counter and log it — abacee3

### Phase 2: Adapters

- [x] 2.1 Persistence of the counter and trades — abacee3 (landed with Phase 1: the new Resolution fields don't compile without it)
- [x] 2.2 Web: counter and trades in the game shape — 3af7015

### Phase 3: Frontend

- [x] 3.1 trading.js helpers for the counter — 4db7785
- [x] 3.2 s03 shows the counter and trades — b69f93c

### Phase 4: Verify

- [x] 4.1 Full gate and browser walk — b69f93c
