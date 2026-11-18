# Playtest feedback: initiative 2d6, warehouse contents, trade history, trade max, Assets, newest log first

- Date: 2026-11-18
- Source: the creator's notes after a playtest, 2026-11-18 (six items, one PR; notifications come after).
- Status: in progress

## What the creator asked

1. **Rule correction:** initiative is bid + **2d6**, not bid + d2. Every player is told how much each player paid and
   what they rolled.
2. **System view:** your own warehouses show what's in them.
3. **Trade history** that is easy to reach: your trades, colour coded, on the Deck just below your ships.
4. **Trade amount in the message:** "The market has reached your sell at 15 HT (you can sell x): your decision", not
   only as the prefilled amount box.
5. **Assets tab:** ships, factories, goods, so a player sees where their invested money is.
6. **Game log:** latest entry first.

## Decisions (defaults taken, override on the PR)

| # | Decision | Why |
|---|---|---|
| 1a | Initiative = bid + 2d6, highest first. Players who tie roll 2d6 again among themselves, as before with d2. | R27 corrected by the creator; tie rule unchanged. |
| 1b | Bids stay secret **during** Bidding (R29). When Bidding closes, every player's bid, roll and total become public: in the log line and in the Turn order panel. | "All players should be informed how much each player paid and how much they rolled." Revealing at close keeps the auction blind. |
| 1c | The log line: `Initiative (bid + 2d6): Vega Lines 5 + 9 = 14 → Orion Freight 0 + 11 = 11`, with any tie-break rolls after it: `(tie: Vega Lines rolled 8, Kessler Cargo 4)`. | One line, every number. |
| 2 | The system view's Warehouses table gets a "Holds" column. Your own row shows what it holds ("3 ISO, 2 FOOD" or "Empty"); other players' rows show "—", since a warehouse's contents are private (R29). | Contents are private; the table is public. |
| 3a | Every trade and wholesale trade is recorded on its log entry as structured data (player, system, goods, buy/sell, units, price), next to the text. Trades are public already (R18), so this adds no leak. Entries written before this change have none, so history starts from the deploy. | The poll already brings the whole log incrementally; a separate list on `Player` would grow with the game inside the game item (§12). |
| 3b | The Deck shows "Your trades" below "Your fleet": newest first, one line each, e.g. `T3 · Bought 4 FOOD at Tau Ceti, 12 HT each (48 HT)`, with Bought/Sold coloured by `.tone--buy` / `.tone--sell`. A trade of 0 units isn't listed. Shows the last 8, with "Show all" to expand. | Short and in reach; the Game log still has everything. |
| 4 | When the game waits for your amount, the status line and the alert say how many you can trade: "The market has reached your sell at 15 HT (you can sell 4 u): your decision." With 0: "(you can't sell any)". | `you.tradeMax` already exists; only the text changes. |
| 5a | A new **Assets** tab (nav item after Fleet), for the viewer only. Sections: **Ships** (name, where, sale value R61's half price, value of its cargo), **Warehouses** (system, blocks, what it holds and its value), **Factories** (system, good, units a turn, what a turn's output is worth now), and a summary line: money, ships' sale value, goods' value. | "Where their invested money is." |
| 5b | Goods are valued at **today's price at the system where they are** (units × price). A factory has no HT value of its own: its line says what one turn's output is worth at today's price. The server computes every number (§13 row 9). | No resale rule for factories exists; the price is the only honest value. |
| 6 | The Game log lists each turn's events newest first, as turns already are. | Asked for directly. |

## Placement (`.ai/engineering-standards.md` §13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| Initiative = bid + 2d6, ties roll again | the turn's bids; dice | 2, 4 | `Turn.closeBidding(emails, dice)` takes rolls from `Dice.twoD6`; `Dice.d2` is removed (nothing else rolls it) |
| What each player bid and rolled, public once Bidding closes | the turn's bids and rolls | 4, 8 | `InitiativeRoll(playerEmail, bid, rolls)` on `Turn.initiativeRolls`, made by `Turn.closeBidding`; its text by `InitiativeRoll` |
| The initiative log line | rolls + corporation names | 7 | `Game.closeBidding` (names are the root's) |
| A trade's record for history | a `Trade` and its good | 4 | `Trade.record(good)` and `Wholesale.record(good)` → `TradeRecord`; `LogEntry.trade` |
| Value of a ship's / warehouse's goods | its cargo + prices passed in | 5 | `Ship.cargoValue(prices)`, `Warehouse.cargoValue(prices)` (shared through `CargoHold`) |
| A factory's output value | its size + a price | 5 | `Factory.outputValue(prices)` |
| Assets shown to the viewer | the above | 1 | `AssetsResponseDto(player, prices)` in `you.assets`, mapping only |
| "you can sell 4 u" | `you.tradeMax` | 9 | `trading.js` formats it |
| Warehouse "Holds" column, own only | `you.warehouses` | 9 | `board.js` `systemHoldings(game, system, myId)` |
| Trade history list | log entries' `trade` | 9 | new `js/trades.js`, filtered by the viewer's id |
| Newest event first | log | 9 | `log.js` `logTurns` |

## Data and compatibility

- `Turn.initiativeRolls` (new, stored in `TurnItem`): missing on older items → empty; the screen then shows the order
  without numbers.
- `LogEntry.trade` (new, stored in `LogEntryItem` as an optional map): missing → null.
- Response: `turn.initiativeRolls: [{ playerId, bid, roll, total, tieRolls }]` (public), `log[].trade: { playerId,
  system, systemName, goods, type, units, price } | null` (public), `you.assets` (private). Additive only
  (BACKWARD_COMPATIBILITY.md §6).

## Product brief

- R27 amended: bid + 2d6; bids and rolls public once Bidding closes (creator, 2026-11-18). R29 amended accordingly: a
  bid is secret only until Bidding closes.

## Out of scope

- Notifications (the creator's next topic).
