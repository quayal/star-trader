# E05 — Trading phase and resolution (#11)

**Prototype:** `.ai/prototypes/discovery/dashboard-map-system/revision-015/` (revision 013 accepted as the first version, D07; 014 removes the timer, D09; 015 shows the counter resolution, Dictator and Wholesaler steps, R34–R38). Use the "Test state" bar → phase **Trading**; tick "You are first in initiative" to see the order editor. 
**Brief:** `.ai/specs/product-brief.md` — R10, R12, R15, R16, R23, R25, R28, R29, R33, R34–R43, D07, D09, D10.
**Siblings:** E03 Bidding (`2026-10-29-e03-bidding-phase.md` — screen IDs are defined there), E04 Ship-jumps (`2026-10-29-e04-ship-jumps-phase.md`).

## 📝 TLDR

When Ship-jumps end, everyone is taken to the Trading screen. Every good at every system is queued (R36). The first initiative player orders the queue, may reorder what's left at any time, and presses **Resolve next**. Each resolution runs in this order:

1. All offers for that good are disclosed.
2. The Dictator may place their offer; Dictators can't bid for that good in Bidding (R38).
3. A counter starts at S/D + 2d6.
4. The winning offer trades first: the highest buy if the counter is ≥ 0, else the lowest sell.
5. Then whichever remaining offer allows the most units at the current counter trades next, each offer once. Amounts use the start price, and each unit moves the counter (R34).
6. The price then changes once, by the counter's final position, clamped to 1–20 (R35).
7. Wholesalers without an offer, and the Dictator, may trade up to 5 u at the new price (R37).

Everything is public and logged. After the last good, the next turn starts with Bidding.

## 📝 Worked example (from the creator's review, 2026-10-29)

Mu Herculis ISO: start price 11 HT, S/D +1. Altair is Dictator and Kessler is Wholesaler of ISO at Mu Herculis (positions are per good per system, so either may hold no position for ISO elsewhere). Offers: Vega buys at 11, Orion sells at 10.

1. Offers are disclosed. Altair passes.
2. Roll 2 → counter 3.
3. Vega wins (counter ≥ 0). Max = 3 − (−1) = 4; Vega buys 4 → counter −1.
4. Orion's sell (target 4): max = 4 − (−1) = 5; Orion sells 5 → counter 4. Nobody is left.
5. New price = 11 + modifier(4) = 10 HT.
6. Kessler (no offer here) buys 3 u at 10 HT. Altair sells 2 u at 10 HT. The price stays 10.
7. Next turn: the price starts at 10, and the counter is +1 + a new roll.

Variant: if Vega had bought only 1 (counter 2), Orion's max would be 4 − 2 = 2. Had a third offer, Kessler buying at 10 (target 2), been present after Orion sold 5 (counter 4), Kessler could buy 4 − 2 = 2 u.

## 📝 Screens used

s03 Trading (main column: current resolution; side column: queue), the "your decision" alert on s01–s04, s04 Game log, s02 System details (prices update). IDs are defined in the E03 spec.

## 📋 Stories and acceptance criteria

### E05-S01 — The first initiative player orders and triggers resolutions

1. [s03] [D07] When Trading starts, every player's client navigates to s03.
2. [s03] [R36] The queue holds every good at every system. Initial order: by system in map order, then good (proposed).
3. [s03, side] [R25] The first initiative player sees "Remaining goods" with drag-and-drop and ↑/↓ buttons (keyboard alternative), and **Resolve next**. Others see the list read-only: "Order decided by {corp} (first in initiative); it can change until each one resolves."
4. [s03] [R25] **Resolve next** is available only when the current resolution has finished, including its Dictator and Wholesaler steps. The queue can be reordered meanwhile.
5. [s04] [R25] Each resolution logs "Resolving: {system} {good} (chosen by {corp})".
6. [s01–s04 top bar] Phase reads "Trading — n of N resolved".
7. [s03] Before Trading: "Trading starts after Ship-jumps. Results will appear here."
8. [—] [D09] Nothing has a deadline. Whoever the game waits for is named: "Waiting for Vega Lines to resolve the next good", "Waiting for Altair Trade Co. (Dictator) to decide", "Vega Lines can buy up to 4 u — waiting for their decision".
9. [any screen] Whenever you have a decision to make (trade amount, Dictator offer, wholesale) and aren't on s03, an alert says so with a link to Trading.

### E05-S02 — Offers are disclosed and the Dictator may act

1. [s03] [R34, R17] When a good starts resolving, every offer for it is shown to all players: corporation, Buy/Sell, price.
2. [—] [R38] If the good at this system has a Dictator, they are asked before the roll: "Place an offer?" They may place one offer (R33) or pass. They never have an offer from Bidding for this good here (E03-S03-10).
3. [s03] The Dictator's offer is shown like the others and has no other advantage.
4. [—] With no Dictator, resolution goes straight to the roll.

### E05-S03 — The counter resolves the offers

1. [—] [R34] Counter = S/D of that good at that system + 2d6 roll. The S/D itself never changes (R35).
2. [—] [R34] First trade: the winning offer — highest BUY if the counter is ≥ 0, else the lowest SELL; equal prices go by initiative order.
3. [—] [R34] Next trade, repeated: of the offers that haven't traded, the one with the highest max at the current counter. A buyer–seller tie goes to the counter's sign (≥ 0 → buyer), then initiative order. Stop when no offer allows ≥ 1 u.
4. [—] [R34, R13, R15] Max for a buy = counter − buy target of (offer price − start price); for a sell = sell target − counter. Targets come from `SDCalculator`; R15 saturation applies. The start price is used throughout.
5. [—] [R34] Each unit traded moves the counter one step: down for a buy, up for a sell.
6. [s03] [D07] Shown in plain terms, without dice arithmetic: "Market +3 → buyers first", then each trade as it happens, and "No one else can trade at this market" at the end.
7. [s03] If no offer can trade at all (or there are none), the resolution goes straight to the price change.

### E05-S04 — Each trader chooses an amount

1. [s03] [R34] The trader whose turn it is sees: amount pre-filled with max; a whole number 1..max, else inline error "Enter a whole number from 1 to {max}." with focus. Help: "You pay your offer price, {price} HT each. Trading more leaves less for others."
2. [—] [R12] A buy's max is first reduced to what you can afford.
3. [s03] [R42] With the amount, you choose which of your ships at that system loads or unloads, and may split the amount across them. A sell is capped by what those ships hold of that good (R10).
4. [s03] [R42, R16, R23] POLY and ISO take one cargo space per unit, 10 per ship; CHIPS and FOOD take none. Bulky units beyond the chosen ships' free space are lost; with no ship there, everything bought is lost. Before confirming: "Only {n} u fit in the ships you chose; the rest will be lost."
5. [—] If the max after R12/R10 is 0, the trader is shown why ("You can't afford any units at 15 HT") and their offer is done with no units traded (proposed).
6. [s03, s04] [R25] Each trade is public: "{corp} {bought | sold} {n} u at {price} HT each".
7. [s01 top bar] [R29] Your money updates; nobody else sees it.

### E05-S05 — The price changes, then Wholesalers trade

1. [—] [R35] New price = start price + modifier(final counter), clamped to 1–20 HT.
2. [s03, s04] "{good} price at {system}: {a} → {b} HT" — shown and logged even when nobody traded.
3. [s01, s02] The map and s02 show the new price and market label right after.
4. [—] [R37] Then each Wholesaler of that good at that system who placed no offer for it, and the Dictator, is asked: "Buy or sell up to 5 u at {b} HT?" — choosing ships as in S04-3, capped by money and cargo (R12, R42); "Pass" is always possible.
5. [—] [R37] Wholesale trades don't move the price. They're public and logged: "{corp} (Wholesaler) bought 3 u at 10 HT".
6. [—] Order when several are asked: initiative order (proposed).

### E05-S06 — Trading 6 or more units can raise your market position

1. [s02] [R28] Positions are shown per good at each system: Dictator, Wholesaler, Retailer. Everyone starts with none (R40).
2. [—] [R43] After a trade of at least 6 units of a good at a system (one offer's trade, not summed across a resolution), roll 2d6. If the roll is ≤ the units traded, the trader moves up one tier for that good at that system: none → Retailer → Wholesaler → Dictator.
3. [—] [R43] A Dictator can't go higher. A Wholesaler who becomes Dictator demotes the previous Dictator to Wholesaler. Positions never drop any other way.
4. [s03, s04] The roll and its outcome are public: "Vega Lines traded 7 u — rolled 5: now Wholesaler of ISO at Mu Herculis" / "rolled 9: no change".
5. [—] A promotion takes effect at once, so a new Wholesaler or Dictator can use it in the wholesale step of the same resolution (proposed).

### E05-S07 — The turn ends

1. [s03] When the queue is empty and nothing is pending: "All goods resolved. The next turn starts with Bidding."
2. [—] [N06, R39] Unless it was turn 20, the turn number increases and the phase becomes Bidding. Offers that didn't trade expire (proposed).
3. [s04] The new turn starts a new group in the log.

### E05-S08 — The game ends after turn 20

1. [—] [R39] When turn 20's Trading has resolved its last good, the game ends. No further turns start.
2. [s09] [R39] Every player is taken to "Game over": each player's final money, highest first, and the winner. Only cash counts; ships, factories, warehouses and goods are not valued.
3. [s09] [R39] Final money is shown for everyone, the one point where R29 (money private) doesn't apply.
4. [s09] [R39] Players tied on money share the win: "Vega Lines and Kessler Cargo win with 2,310 HT" (proposed).
5. [s04] The log ends with "Game over: {winner} wins with {money} HT".
6. [s01–s04] After the game ends, every action is gone; the board, system screens and log stay readable.

## 📝 States checklist (Trading)

| State | Where | Covered by | In prototype? |
|---|---|---|---|
| Before Trading | s03 | S01-7 | yes |
| You order the queue / someone else does | s03 | S01-3, S01-4 | yes |
| Offers disclosed | s03 | S02-1 | yes (rev 015) |
| Dictator deciding (you / someone else) | s03 | S02-2, S01-8 | yes (rev 015) |
| Several trades in one resolution | s03 | S03-3 | yes (rev 015) |
| Your amount decision, elsewhere in the app | any | S01-9, S04-1 | yes |
| Amount error | s03 | S04-1 | yes |
| No offers / nobody can trade — price still moves | s03 | S03-7, S05-2 | yes (rev 015) |
| Price clamped at 1 or 20 | s03 | S05-1 | yes (rev 015, Gamma Leporis POLY) |
| Wholesaler asked (you / someone else) | s03 | S05-4 | yes (rev 015) |
| Can't afford anything / nothing to sell | s03 | S04-5 | **no** |
| Goods lost (overflow or no ship) | s03 | S04-4 | **no** |
| Choosing which ships load or unload | s03 | S04-3 | **no** |
| Position roll after 6+ units (promoted / no change / Dictator demoted) | s03, s04 | S06 | **no** |
| Someone the game waits for is away | s03 | S01-8 (D09) | yes |
| Turn ends | s03 | S07 | yes |
| Game over | s09 | S08 | **no** — not in the prototype yet |

## 📝 Decisions in play

R34 (counter resolution), R35 (price change, S/D fixed), R36 (every good every turn), R37 (Wholesalers), R38 (Dictator), R12/R16/R23/R10/R42 (caps and cargo), R15 (saturation), R25 (ordering), R28 (positions shown), R29 (money private), R33, R39 (end of game), R42 (cargo), R43 (position gain), D07, D09, D10.

## ❓ Open questions

None in this spec.

## 🚫 Out of scope

Warehouses and factories (N08); loans and reputation (N05). End of game is S08 (R39).

## 📋 Phasing and dependencies

- Depends on E03 (offers, initiative) and E04 (ship positions).
- Order: S01 + S03 + S04 → S05 → S06 (positions) → S02 (Dictator) → S07 → S08. Revision 015 covers the S02–S05 screens.
- Existing code: `SDCalculator` and the target-indicator math in `TransactionAmountCalculator` stay. `BestOfferCalculator` and `TradeDesk.resolve` assume one winner and must become the counter loop (R34). Today they also throw when the winning side has no offers (`reduce` on an empty list). Tie-break by submission order (`TradeOffer.initiative`) is replaced by turn initiative order.

## Screens without a criterion in this spec

s05, s06, s07, s08 (covered by E03 and E04).
