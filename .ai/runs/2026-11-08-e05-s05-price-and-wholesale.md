# Execution plan: E05-S05 — The price changes, then Wholesalers trade

Source doc: .ai/specs/2026-10-29-e05-trading-phase.md
Issue: #49

## Goal

When the counter can reach no more offers, the good's price changes once: start price + the price modifier of the
counter's final position, clamped to 1–20 HT (S05-1, R35). That's shown on s03 and logged, "ISO price at Mu Herculis:
11 → 10 HT", even when nobody traded (S05-2), and the map and s02 show the new price from then on (S05-3); the next
resolution of that good starts from it. Then each Wholesaler of that good at that system who placed no offer for it,
and the Dictator, is asked in initiative order: buy or sell up to 5 u at the new price, choosing ships as in S04-3,
capped by money and cargo, or pass (S05-4, S05-6). Wholesale trades don't move the price and are public and logged
(S05-5). The good finishes resolving when the last one asked has answered.

## What already works (checked before planning)

- E05-S01 to S04 are merged (PRs #88–#91). `Resolution` waits for the Dictator, then for each trader in turn
  (`awaitingTrade`), and `isFinished()` is true once nobody is asked and no offer can trade.
- Prices are fixed today: `MarketGood.startPrice()` reads `StarSystem.getPrice`. `GameResponseDto.board` is built from
  `StarSystem.entries`, and its comment already says it is sent per game "so that trading can move its prices (E05)
  without changing the shape". `board.js` and `offers.js` read prices from `game.board`.
- `SupplyDemand.priceModifier()` holds the R35 table (indicator −18..18 → modifier +7..−6). The worked example checks:
  11 + modifier(4) = 11 − 1 = 10.
- `Player.tradeMax(offer, counterMax)` and `Player.trade(offer, counterMax, amount, loads)` already cap by money (R12)
  and by what ships in port hold (R10), and split across ships in port with overflow lost (R42, R16). A wholesale is
  the same settlement at the new price with a cap of 5.
- Market positions are on `Player.marketPositions` (public); `Player.isDictatorOf` exists.

## Scope

- **Domain** (`game`):
  - `MarketPrices`: each good's price at each system in this game, starting at the board's (R35: only the price carries
    over). `Game.prices`.
  - `Resolution` gains `startPrice` (from the game's prices when the good starts resolving), `wholesalers` (who will be
    asked, fixed when it starts: Wholesalers of that good there without an offer, then the Dictator, ordered by
    initiative) and `wholesales` (their answers). `newPrice()` once trading is over; `awaitingWholesaler()`;
    `withWholesale(...)`. `isFinished()` also waits for the wholesalers.
  - `Wholesale`: who, Buy/Sell or a pass, units, at the new price; `terms()` for the log.
  - `Game`: applies the new price to `prices` and logs it the moment trading is over; `wholesale(email, type, amount,
    loads)` and `passWholesale(email)`; `Player` gains what it needs to be asked (`isWholesalerOf`).
  - `TradingService.wholesale(...)` / `passWholesale(...)` inside `GameStore.update`.
- **Adapters**:
  - Persistence: `GameItem.prices`, `ResolutionItem.startPrice` / `wholesalers` / `wholesales`. Older items have none:
    board prices, the board's start price, nobody asked.
  - Web: `POST /api/games/{id}/trading/wholesale` `{ type, amount, ships }` and `POST …/trading/wholesale-pass`. `board`
    uses the game's prices. `turn.resolution` gains `startPrice`, `newPrice`, `awaitingWholesaler`, `wholesales`.
    `you.wholesaleMax` `{ buy, sell }` (private: money and cargo, R29).
- **Frontend**: s03 shows the price line and wholesale lines after the trades, the wholesale form (Buy/Sell, amount,
  per-ship split, Trade / Pass) for the one asked, the waiting line for everyone else, and the decision alert (S01-9).

## Non-goals

- Position rolls after 6+ units (S06), the turn's end (S07), end of game (S08).
- Deleting the old `trading` package (`BestOfferCalculator`, `TradeDesk`, `TransactionAmountCalculator`).

## Decisions (autonomous, reversible)

- **Prices live on the game, not the board enum.** `StarSystem`'s prices become each game's starting prices.
- **Who is asked is fixed when the good starts resolving.** Positions don't change during the turn (S06-5), and the
  only offer placed after that is the Dictator's, who is asked anyway (R37: "the Dictator even if they did").
- **A wholesale settles like a trade (S04):** only ships in port load or unload, a sell is capped by what they hold, a
  buy by money; POLY/ISO beyond free space are lost. The cap is 5 u (R37).
- **Pass is always possible, and is the only choice when neither a buy nor a sell of 1 u is possible.** The form says
  why, privately, as S04-5 does. No separate "Trade nothing".
- **Wholesale lines say the total, like S04-6:** "Kessler Cargo (Wholesaler) bought 3 u at 10 HT each, paid 30 HT",
  "Altair Trade Co. (Dictator) passed". S05-5's example has no total; the creator's S04-6 decision added one to every
  trade line.
- **The price line is logged after the counter's end line**, before any wholesale: "No one else can trade at Mu
  Herculis ISO", then "ISO price at Mu Herculis: 11 → 10 HT".
- **The counter is clamped to −18..18 for the modifier lookup** (R15 saturation); it can't leave that range today, but
  the table has no entry beyond it.

## Implementation Plan

### Phase 1: Domain

1. `MarketPrices` and `Game.prices`; `Resolution.startPrice` used for every max (R34); `newPrice()` (R35). Tests:
   `MarketPricesTest`, `ResolutionTest` (worked example's 11 → 10, clamps at 1 and 20, no trades).
2. `Wholesale`, `Resolution.wholesalers` / `wholesales` / `awaitingWholesaler` / `withWholesale`, `isFinished`. Tests:
   `ResolutionTest`.
3. `Game` / `Turn` / `Player` / `TradingService`: the price applied and logged, who is asked, `wholesale` and
   `passWholesale`, Resolve next refused while someone is asked. Tests: `GameTradingTest`, `TradingServiceTest`,
   `PlayerTest`.

### Phase 2: Adapters

1. Persistence: prices, start price, wholesalers and wholesales; round trip and older-item tests.
   `BACKWARD_COMPATIBILITY.md` §7.
2. Web: the two endpoints, the board's prices, the resolution's new fields, `you.wholesaleMax`. `GameResponseDtoTest`,
   `GameControllerTest`. `BACKWARD_COMPATIBILITY.md` §6.

### Phase 3: Frontend

1. `js/trading.js`: price and wholesale lines, waiting and alert text, the wholesale form's text, split and validation.
   Tests in `src/test/js/trading.test.mjs`.
2. s03: the wholesale form, keyed by game/turn/good/player so a poll doesn't wipe it.

### Phase 4: Verify

1. Full gate (`./gradlew build`), then a browser walk with two players: a price change with trades and one with none,
   the board's new price, a Wholesaler's buy split across ships, a pass, the waiting line and alert for the other
   player, phone width.

## Risks

- Every resolution action now may change prices and must log in the right order; one helper in `Game` handles it for
  all five actions (resolve next, Dictator offer/pass, trade, trade nothing) plus the two wholesale ones.
- Money and cargo stay private: `you.wholesaleMax` only for the player asked. Tested in DTO and controller tests.
- A resolution saved before S05 reads with the board's start price and nobody asked; one saved finished stays finished.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [ ] 1.1 Game prices, the resolution's start price and new price
- [ ] 1.2 Resolution asks the wholesalers
- [ ] 1.3 Game, Turn, Player and TradingService apply the price and take wholesale decisions

### Phase 2: Adapters

- [ ] 2.1 Persistence of prices and the wholesale step
- [ ] 2.2 Web: wholesale endpoints, the board's prices and the wholesale step in the game shape

### Phase 3: Frontend

- [ ] 3.1 trading.js helpers for the price line and the wholesale form
- [ ] 3.2 s03 wholesale form

### Phase 4: Verify

- [ ] 4.1 Full gate and browser walk
