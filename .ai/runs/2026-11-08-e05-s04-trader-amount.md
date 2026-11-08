# Execution plan: E05-S04 — Each trader chooses an amount and which ships load or unload

Source doc: .ai/specs/2026-10-29-e05-trading-phase.md
Issue: #48

## Goal

When the counter reaches an offer that can trade, the game waits for that offer's player. They choose an amount from 1
to their max, pre-filled with the max (S04-1). A buy's max is first cut to what they can afford (S04-2), and a sell's
to what their ships at that system hold (S04-3, R10). They also choose which of their ships there load or unload, and
how many units each takes (S04-3). POLY and ISO beyond the chosen ships' free space are lost, and with no ship there
everything bought is lost (S04-4). A trader whose max is 0 after the caps is skipped and shown why (S04-5). Each trade
is public and logged (S04-6), and the trader's money and cargo change (S04-7).

## What already works (checked before planning)

- E05-S03 (#47, PR #90) is merged. `Resolution.startCounterOnceDecided` rolls the counter and then runs
  `tradeUntilNobodyCan`, so each offer trades its full counter max at once. No money or cargo moves.
- `Resolution.nextTrade(initiative)` is public: who trades next and their counter max.
- `Trade.terms()` already uses S04-6's wording ("bought 4 u at 11 HT each"), and `Game.counterLog` logs every trade
  together with the market line when the counter starts.
- `Ship.cargo` / `cargoSpacesUsed()` / `CARGO_SPACES` and `Goods.takesCargoSpace` exist (R42). `YourShipResponseDto`
  already sends the viewer's cargo and space used; the top bar shows `you.money`, refreshed on every poll.

## Scope

- **Domain** (`game`):
  - `Resolution` stores the turn's `initiative` (copied when it starts) so it can tell, on its own, whom it waits for.
    The counter no longer runs to its end: `awaitingTrade()` is the next trade (offer and counter max), and
    `withTrade(trade)` records one and moves on. `isFinished()` is true once nobody is asked and no offer can trade.
  - `Trade` gains `shortfall` (null, or why nothing traded: `CANT_AFFORD` / `NOTHING_TO_SELL`) for S04-5's 0-unit trade.
  - `Ship.load(goods, units)` (returns the ship and the units lost, R42/R16) and `Ship.unload(goods, units)`.
  - `Player.tradeMax(offer, counterMax)` (R12, R10: capped by money for a buy, by what their ships at that system hold
    for a sell), `Player.shortfall(...)`, and `Player.settle(offer, units, loads)`: checks the split across their
    ships at that system, moves money and cargo, and returns what was lost.
  - `Game.trade(email, amount, loads)`: checks it's that player's trade, the amount 1..max ("Enter a whole number from
    1 to {max}."), settles, records the trade, then skips any next trader whose max is 0. Logs each trade as it
    happens and the end line once the resolution finishes.
  - `TradingService.trade(...)` inside `GameStore.update`.
- **Adapters**:
  - Persistence: `ResolutionItem` stores `initiative`; `TradeItem` stores `shortfall`. Older items have neither,
    which reads as an empty initiative and no shortfall. An S03 resolution ran to its end, so it still reads finished.
  - Web: `POST /api/games/{id}/trading/trade` `{ amount, ships: [{ name, units }] }`. `turn.resolution.awaitingTrade`
    (player, type, price, counter max: public, D09). `you.tradeMax` (private: depends on money and cargo, R29). A trade's
    `shortfall` only in its own player's view.
- **Frontend**: s03's amount form for the trader (amount, one row per ship at that system with its cargo, the split,
  the overflow warning), the waiting line for everyone else, the decision alert (S01-9), and the 0-unit reason.

## Non-goals

- The price change and Wholesalers (S05), position rolls (S06), the turn's end (S07).
- Deleting the old `trading` package.

## Decisions (autonomous, reversible)

- **"At that system" means any of the player's ships in that system, wherever it landed (port, planet or space).**
  R23 says "physically present"; R42 says "ships at that system". R10 mentions "ship-in-port" but predates R30/R42.
- **The split is explicit and must add up to the amount** whenever the player has a ship there. With no ship there, a
  buy sends no ships and everything is lost (S04-4); a sell's max is already 0, so it never asks.
- **The form pre-fills the split**: a sell draws from the ships in order of what they hold; a buy fills each ship's
  free space in order, then puts the rest on the first ship (where it's lost, and the warning says so). The player
  can change any row.
- **A trader whose max is 0 after R12/R10 is skipped at once, with no click** (S04-5 "their offer is done"). The game
  doesn't wait on a decision with only one answer (D09). The trade is recorded with 0 units and a reason.
- **Why a trader couldn't trade is private.** "You can't afford any units at 15 HT" tells something about money (R29),
  and "You have no ISO to sell here" about cargo (S02-7). The public log and the trade list say only "Vega Lines
  couldn't trade". The reason reaches only that player's view.
- **The public "can buy up to n u" is the counter's max, not the capped one.** The capped max depends on money (R29).
- **Logged as it happens**: the market line when the counter starts, each trade when it's made, the end line when the
  resolution finishes. Before S04 they were logged together.
- **A sell's help text**: "You get your offer price, {price} HT each." The spec gives only the buy's wording.

## Implementation Plan

### Phase 1: Domain

1. `Ship.load` / `unload`, `Player.tradeMax` / `shortfall` / `settle` (with `ShipLoad`). Tests: `ShipTest`,
   `PlayerTest`.
2. `Resolution` waits for each trader: `initiative`, `awaitingTrade()`, `withTrade`, `isFinished`; `Trade.shortfall`.
   Tests: `ResolutionTest` (worked example played out one decision at a time, partial trades, the buyer–seller tie).
3. `Turn` / `Game` / `TradingService`: `trade(...)`, skipping traders who can't, logging as it happens, Resolve next
   refused while a trader decides. Tests: `TurnTest`, `GameTradingTest`, `TradingServiceTest`.

### Phase 2: Adapters

1. Persistence: `ResolutionItem.initiative`, `TradeItem.shortfall`; round trip and older-item tests.
   `BACKWARD_COMPATIBILITY.md` §7.
2. Web: the trade endpoint, `turn.resolution.awaitingTrade`, `you.tradeMax`, a trade's private `shortfall`.
   `GameResponseDtoTest`, `GameControllerTest`. `BACKWARD_COMPATIBILITY.md` §6.

### Phase 3: Frontend

1. `js/trading.js`: the amount form's text, default split, overflow warning, validation, waiting and alert text,
   0-unit lines. Tests in `src/test/js/trading.test.mjs`.
2. s03: the amount form (amount, per-ship rows, warning, Confirm), keyed by game/turn/good so a poll doesn't wipe it.

### Phase 4: Verify

1. Full gate (`./gradlew build`), then a browser walk with two players: a buy split across two ships with overflow lost,
   a sell, a trader who can't afford anything, the waiting line for the other player, the alert on another screen,
   money in the top bar, and phone width.

## Risks

- The resolution panel is redrawn on every poll; the amount form must keep what the player typed (key it by
  game/turn/good/offer, lessons 2026-11-04).
- Money and cargo are private: the 0-unit reason and the capped max must not reach other players. Tested in the DTO and
  controller tests.
- An S03 resolution saved mid-game reads with no initiative. It ran to its end, so nothing waits; tested.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [ ] 1.1 Ship load/unload and Player caps and settlement
- [ ] 1.2 Resolution waits for each trader
- [ ] 1.3 Turn, Game and TradingService take the trader's decision

### Phase 2: Adapters

- [ ] 2.1 Persistence of initiative and shortfall
- [ ] 2.2 Web: the trade endpoint and the awaiting trade in the game shape

### Phase 3: Frontend

- [ ] 3.1 trading.js helpers for the amount form
- [ ] 3.2 s03 amount form

### Phase 4: Verify

- [ ] 4.1 Full gate and browser walk
