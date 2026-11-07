# Execution plan: E05-S01 — The first initiative player orders and triggers resolutions

Source doc: .ai/specs/2026-10-29-e05-trading-phase.md
Issue: #46

## Goal

When Ship-jumps end, every player lands on the new Trading screen (s03). It holds a queue of every good at every system
(R36). The first player in initiative orders it and presses **Resolve next**, one good at a time (R25). Everyone else
sees the order read-only, and the game names who it waits for (D09).

## What already works (checked before planning)

- The last jump slot ending moves the turn to `TRADING` (`Turn.endJumpSlot` → `toTrading`), and `turn.initiative` is
  set from Bidding on, so "first in initiative" is `initiative[0]`.
- Every poll redraws the top bar, the log and the Deck, and announces new log lines through the polite live region
  (E04-S04), so a "Resolving: …" log line reaches every player within a poll with no extra work.
- Nothing ends Trading today. A game that reaches it stays there (S07 ends the turn).

## Scope

- **Domain** (`game`): a `MarketGood` (system + good) value, and on `Turn` the remaining `tradingQueue` and the
  `resolvedGoods`, filled when Trading starts, in map order (`StarSystem` order), then good (`Goods` order) (S01-2).
  `Game` checks that the caller is first in initiative, reorders the remaining goods or resolves the next one, and logs
  "Resolving: {system} {good} (chosen by {corp})" (S01-5). A `TradingService` runs both inside `GameStore.update` (§9).
- **Adapters**: `TurnItem` stores both lists (`MarketGoodItem`), and an item saved in Trading before this change reads
  with the full queue. The game shape gains `turn.tradingQueue` and `turn.resolvedGoods`. New endpoints:
  `PUT /api/games/{id}/trading/queue` and `POST /api/games/{id}/trading/resolve-next`.
- **Frontend**: s03 at `#game/{id}/trading`, with a "Trading" nav item. Before Trading it shows the S01-7 text. In
  Trading it shows the "Remaining goods" list (an editor with drag and drop plus ↑/↓ and **Resolve next** for the first
  player, read-only for others, S01-3), the last good resolved and who the game waits for (S01-8). The top bar reads
  "Trading — n of N resolved" (S01-6), and every client opens s03 when Trading starts (S01-1). Off s03, the player the
  game waits for sees an alert linking to Trading (S01-9).

## Non-goals

- What a resolution does: disclosing offers (S02), the counter and trades (S03, S04), the price change and Wholesalers
  (S05), positions (S06). In this story a resolution finishes as soon as it starts, so **Resolve next** is available
  whenever goods remain. S01-4's "only when the current resolution has finished" becomes a real check once S02–S05 give
  a resolution steps. The queue can always be reordered meanwhile.
- Ending the turn after the last good (S07) and the end of the game (S08). After the last good, s03 says every good is
  resolved and the game stays in Trading, as it does today.
- `BestOfferCalculator` / `TradeDesk` (the old `trading` package) stay untouched until S03.

## Decisions (autonomous, reversible)

- **S01-9's alert also covers "resolve the next good".** The spec lists the amount, Dictator and wholesale decisions,
  none of which exist until S02–S05. Resolving the next good is the one decision the game waits on in this story (D09),
  so the alert names it ("Your decision is needed: resolve the next good. Go to Trading"). The later decisions plug into
  the same alert.
- **Reordering sends the whole remaining order.** `PUT …/trading/queue` takes the remaining goods in their new order. An
  order that doesn't match what's left (e.g. a good resolved in between) is refused with `409` "The remaining goods have
  changed. Try again.", and the screen re-polls. This keeps the check in one place and race-free (§9).
- **The resolver is `initiative[0]`.** Out of turn: `409` "Order decided by {corp} (first in initiative)."
- **Opening s03 when Trading starts** happens only on the transition, seen by this page for this game (the previous
  render was the same game and not in Trading). A first load already in Trading doesn't redirect, so a player who opens
  the log or a system isn't pulled away.

## Implementation Plan

### Phase 1: Domain

1. `MarketGood` and the trading queue on `Turn`: `toTrading` fills it, `resolveNext`, `reorderQueue`, `resolver`,
   `requireTrading`. Tests first (`TurnTest`).
2. `Game.reorderTradingQueue` and `Game.resolveNextGood`: membership, first-in-initiative check, the log line. Tests
   (`GameTradingTest`).
3. `TradingService`, both actions inside `GameStore.update`. Tests with `FakeGameStore`.

### Phase 2: Adapters

1. Persistence: `MarketGoodItem`, `TurnItem` round trip, and an older item in Trading reads with the full queue.
   `BACKWARD_COMPATIBILITY.md` §7.
2. Web: `turn.tradingQueue` and `turn.resolvedGoods` in the game shape, the two endpoints and their status codes
   (`GameControllerTest`). `BACKWARD_COMPATIBILITY.md` §6.

### Phase 3: Frontend

1. `js/trading.js` pure helpers: route and link, queue labels, resolver, waiting text, "n of N resolved", moving an
   item, and the decision alert. Tests in `src/test/js/trading.test.mjs`.
2. s03 in `index.html`: route, nav item, before-Trading text, the read-only list, the editor (drag and drop + ↑/↓,
   focus kept, moves announced), **Resolve next** with its message.
3. The top bar's "Trading — n of N resolved", opening s03 when Trading starts, the decision alert on other screens, and
   a progress cue when it becomes the viewer's turn to resolve.

### Phase 4: Verify

1. Full gate (`./gradlew build`) and a browser walk with two players through Bidding, Ship-jumps and into Trading:
   auto-open, reorder by button and by drag, resolve, read-only view, alert, top bar, log.

## Risks

- `index.html` is one page for every game: any s03 state that outlives a poll (a pending reorder, a message) is keyed by
  game and turn (lessons 2026-11-04).
- A poll arriving while a reorder is in flight could redraw the old order. The editor keeps its local order until the
  `PUT` answers.
- Elements toggled with `hidden` whose class sets `display` need `[hidden] { display: none; }` (lessons 2026-10-30,
  2026-11-04).

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [x] 1.1 MarketGood and the trading queue on Turn — 9a72897
- [x] 1.2 Game reorders and resolves the next good — 922441f
- [x] 1.3 TradingService — 31f6b80

### Phase 2: Adapters

- [x] 2.1 Persistence of the trading queue — 9a72897 (landed with 1.1: the new Turn fields don't compile without it)
- [ ] 2.2 Web: game shape and trading endpoints

### Phase 3: Frontend

- [ ] 3.1 trading.js helpers
- [ ] 3.2 s03 Trading screen
- [ ] 3.3 Top bar, auto-open, decision alert, progress cue

### Phase 4: Verify

- [ ] 4.1 Full gate and browser walk
