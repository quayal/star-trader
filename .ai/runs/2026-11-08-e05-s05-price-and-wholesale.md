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

## Notes from the run

- **Changed from the plan while building:**
  - A Dictator's pass in the wholesale step is logged as "… (Dictator) passed on wholesale". Plain "passed" would read
    the same as their pass before the roll ("Altair Trade Co. (Dictator) passed"), which is already in the log.
  - `Player.trade` now says why when the player's max is 0 ("You can't afford any units at 10 HT.", "You have no POLY
    in your ships in port at Gamma Leporis, so you can't sell any."), not "Enter a whole number from 1 to 0.". Only the
    wholesale step can reach it with a 0 max; S04's trader acknowledges with Trade nothing instead.
  - The wholesale step reuses s03's amount form (amount, per-ship split, loss warning), adding Buy/Sell and Pass in
    wholesale mode, so there's one form to keep right rather than two.
  - The Dictator is now asked in the wholesale step of every good they're Dictator of (R37), so a good with a Dictator
    resolves only once they've answered. Existing S02/S04 tests that expected it resolved right after the trades now
    pass the Dictator's wholesale step first.
- **Docker was running**, so `GameControllerTest` and the DynamoDB tests ran: 501 Kotlin tests, 0 skipped; 187 JS tests.
- **Browser walk** (agent-browser 0.35.2, two players, own DynamoDB Local on 8100 and app on 3100 with
  `RESEND_API_KEY=` blank; game set up through the API; positions, a ship's system and cargo seeded in DynamoDB):
  - Gamma Leporis POLY (2 HT, market +10): Vega bought 16 of 18 at 5 HT. Both players' s03 then read "No one else can
    trade at this market." and "POLY price at Gamma Leporis: 2 → 5 HT" (S05-1, S05-2). The Deck's map read "POLY 5 HT"
    straight after (S05-3).
  - Orion (Wholesaler, no offer) got "As Wholesaler, buy or sell up to 5 u of POLY at the new price, 5 HT?" with his
    two ships in port, the amount pre-filled with 5 and the split into Meridian's free space. Sell switched to his own
    sell max. A 2 + 3 split warned "Only 4 u fit in the ships you chose; the rest will be lost.". Vega meanwhile saw
    "Waiting for Orion Freight (Wholesaler) to trade up to 5 u at 5 HT, or pass." and the Deck showed Orion "Your
    decision is needed: as Wholesaler, …" with Go to Trading (S01-8, S01-9). He bought 5 into Meridian: money 300 →
    275, and "Orion Freight (Wholesaler) bought 5 u at 5 HT each, paid 25 HT" on s03 and in the log (S05-5).
  - Sigma Draconis FOOD, Vega Dictator, no offers: she passed before the roll; market 0, so "FOOD price at Sigma
    Draconis: 10 → 10 HT" was still shown and logged. Then she was asked as Dictator; Sell said "You have no FOOD in your
    ships in port at Sigma Draconis, so you can't sell any." with only Pass; she passed: "Vega Lines (Dictator) passed on
    wholesale".
  - At 390 px, Beta Hydri ISO with Orion as Wholesaler and no ship there: "You have no ship in port at Beta Hydri, so
    everything you buy will be lost." The form fit with no horizontal scroll. He passed.
  - One click on Trade didn't submit (no request, no error). Replaying the same steps on another good (Deck → Trading
    by hash, the button below the fold) worked the first time, as did every other click, so it wasn't reproduced. I
    put it down to clicking while the view was switching, not to the form.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [x] 1.1 Game prices, the resolution's start price and new price — ffff03d
- [x] 1.2 Resolution asks the wholesalers — ffff03d
- [x] 1.3 Game, Turn, Player and TradingService apply the price and take wholesale decisions — ffff03d

### Phase 2: Adapters

- [x] 2.1 Persistence of prices and the wholesale step — ffff03d (landed with Phase 1: the new fields don't compile without it)
- [x] 2.2 Web: wholesale endpoints, the board's prices and the wholesale step in the game shape — fd845f8

### Phase 3: Frontend

- [x] 3.1 trading.js helpers for the price line and the wholesale form — 60454fe
- [x] 3.2 s03 wholesale form — bba4034

### Phase 4: Verify

- [x] 4.1 Full gate and browser walk — bba4034
