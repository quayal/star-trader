# E05 — Trading phase and resolution (#11)

**Prototype:** `.ai/prototypes/discovery/dashboard-map-system/revision-014/` (revision 013 accepted as the first version, D07; 014 removes the timer, D09). Use the "Test state" bar → phase **Trading**; tick "You are first in initiative" to see the order editor.
**Brief:** `.ai/specs/product-brief.md` — R10–R16, R23, R24, R25, R28, R29, R33, D07, D09.
**Siblings:** E03 Bidding (`2026-10-29-e03-bidding-phase.md` — screen IDs are defined there), E04 Ship-jumps (`2026-10-29-e04-ship-jumps-phase.md`).

## 📝 TLDR

When Ship-jumps end, everyone is taken to the Trading screen. Every system + good with pending offers is queued. The player first in initiative decides the order, may reorder what's left at any time, and presses **Resolve next**. Each trade resolves by 2d6 + the good's S/D at that system: non-negative → the highest buy offer wins, negative → the lowest sell offer wins, with ties broken by initiative order. The winner chooses an amount from 1 to the maximum. They pay their own price, and the reference price moves by the amount traded. Every result is public and logged. After the last trade the next turn starts with Bidding.

## 📝 Screens used

s03 Trading (main column: current resolution, last result; side column: queue), s01–s04 decision alert, s04 Game log, s02 System details (prices and positions update). IDs are defined in the E03 spec.

## 📋 Stories and acceptance criteria

### E05-S01 — The first initiative player orders and triggers resolutions

1. [s03] [D07] When Trading starts, every player's client navigates to s03.
2. [s03] [R25] The queue holds every system + good with at least one pending offer. Initial order: proposed — by system in map order, then good.
3. [s03, side] [R25] The first initiative player sees "Remaining trades" with drag-and-drop and ↑/↓ buttons (keyboard alternative), and **Resolve next**. Others see the same list, read-only: "Order decided by {corp} (first in initiative); it can change until each one resolves."
4. [s03] [proto-A22] **Resolve next** is available only when no trade is waiting for a winner's decision; the queue can still be reordered meanwhile ("Resolve next is available once the current winner decides.").
5. [s03] Other players see "Waiting for {corp} to resolve the next trade." when nothing is resolving.
6. [s04] [R25] Each resolution logs "Resolving: {system} {good} (chosen by {corp})".
7. [s01–s04 top bar] Phase reads "Trading — n of N resolved".
8. [s03] Before Trading: "Trading starts after Ship-jumps. Results will appear here."
9. [—] [D09] Nothing in Trading has a deadline: the game waits for the first initiative player to press **Resolve next**, and for each winner to decide. Everyone sees whom it is waiting for ("Waiting for Vega Lines to resolve the next trade", "Kessler Cargo can sell up to 4 u — waiting for their decision").

### E05-S02 — A trade resolves to one winning offer

1. [—] [R08, R24] Roll 2d6 + S/D of that good at that system. ≥ 0 → best BUY (highest price); < 0 → best SELL (lowest price). A price tie is broken by initiative order.
2. [s03] [D07] Shown in plain terms, without the dice arithmetic: "+1 → highest offer wins" / "−3 → lowest offer wins".
3. [s03] [R24] On a tie: "Tie at {price} HT — {corp} wins on initiative order".
4. [s03] "{Highest bidder | Lowest seller} ({corp}, {price} HT) can {buy | sell} up to {max} u — waiting for their decision" (or "— your decision").
5. [—] [R13, R15] Max = the distance between the dice-resolved position and the indicator implied by the winning price, with the price clamped to +7 (buy) / −6 (sell) from the current price.
6. [—] If the roll picks a side with no offers, the trade resolves with no winner (OQ-1).

### E05-S03 — The winner chooses an amount and the result is public

1. [any screen] [D07, proto-A15] When you win and aren't on s03, an alert on any screen reads "Your decision is needed: you won {good} at {system}." with a link to Trading.
2. [s03] [R13] Amount field pre-filled with max; whole number 1..max, else inline error "Enter a whole number from 1 to {max}." with focus. Help: "You pay your offer price, {price} HT each. Trading more moves the price further."
3. [—] [R14] You pay or receive your own offer price per unit. The reference price moves by the amount actually traded, one step per unit, as R14 describes.
4. [—] [R12] A buy's max is first reduced to what you can afford.
5. [—] [R10, R23] A sell is capped by what your ship(s) at that system hold. Blocked on Q03 (warehouses and how goods move).
6. [—] [R16, R23] Bought goods go into your ship(s) at that system; anything that doesn't fit, or everything if you have no ship there, is lost. The winner is told before confirming: "Only {n} u fit in your ships here; the rest will be lost." (proposed).
7. [s03, s04] [R25] Result for everyone: "{corp} {bought | sold} {n} u paying {price} HT each; {good} price at {system} moves {a} → {b} HT". Shown as "Last result" and logged.
8. [s01, s02] Updated prices and market labels are shown on the map and s02 right after.
9. [s01 top bar] [R29] The winner's money updates; nobody else sees it.
10. [—] If the max after R12 (afford) or R10 (holdings) is 0, the winner is shown why ("You can't afford any units at 15 HT") and the trade ends with no units traded (proposed).
11. [—] [R17] Losing offers are never revealed; only the winning price is public (proposed).

### E05-S04 — Market positions take effect

1. [s02] [R28] Positions are shown per good: Dictator, Wholesaler, Retailer.
2. [—] [R11] Wholesaler: may buy/sell up to 6 u after the main trade, whatever its outcome. Dictator: their offer trumps competing offers. The UI for both was not prototyped (OQ-5).
3. [—] [R10, Q04] A won trade updates the winner's tier. How tiers are earned is Q04 — **blocked**; this story can ship effects against fixed tiers first.

### E05-S05 — The turn ends

1. [s03] When the queue is empty and no decision is pending: "All trades resolved. The next turn starts with Bidding."
2. [—] [N06] The turn number increases and the phase becomes Bidding. Offers that didn't win expire (proposed).
3. [s04] The new turn starts a new group in the log.

## 📝 States checklist (Trading)

| State | Where | Covered by | In prototype? |
|---|---|---|---|
| Before Trading | s03 | S01-8 | yes |
| You order the queue | s03 | S01-3, S01-4 | yes |
| Someone else orders | s03 | S01-3, S01-5 | yes |
| Resolving, waiting for winner | s03 | S02-4 | yes |
| Tie | s03 | S02-3 | yes |
| You win, elsewhere in the app | any | S03-1 | yes |
| Amount error | s03 | S03-2 | yes |
| Roll picks a side with no offers | s03 | S02-6 | **no — OQ-1** |
| Winner doesn't decide, or is away | s03 | S01-9 — the game waits and names them (D09) | yes |
| First initiative player doesn't press Resolve next | s03 | S01-9 — same | yes |
| Can't afford anything / nothing to sell | s03 | S03-10 | **no** |
| Goods lost (overflow or no ship) | s03 | S03-6 | **no** |
| Empty queue (no offers this turn) | s03 | S05-1 | **no** — the prototype always has three trades |
| Turn ends | s03 | S05 | yes |
| End of game | — | — | **no — OQ-7** |

## 📝 Decisions in play

R10–R16 (resolution, amount, price move, affordability, overflow), R23 (ship presence), R24 (tie-break), R25 (ordering), R28/R11 (positions), R29 (money private), R33, D07, D09 (no timers), Q03, Q04, Q06.

## ❓ Open questions — only those that change a rule or the flow

| # | Question | Blocking? | Proposed default |
|---|---|---|---|
| OQ-1 | The roll says "highest buy wins" but there are only sell offers (or the reverse). What happens? | **yes** for S02 | no trade; logged as "{system} {good}: no {buy/sell} offers — no trade" |
| OQ-5 | How does the player use Wholesaler's extra 6 u, and how is a Dictator's trump shown? | no for S01–S03 | design in a follow-up prototype revision |
| OQ-7 | When does a game end, and what does everyone see? There is no rule in the brief. | no for the first playtest | open-ended; the creator ends it manually (needs its own story) |

## 🚫 Out of scope

Computing tiers (Q04); warehouses as cargo source and factory output (Q03, R32); loans and reputation (N05); end of game (OQ-7, separate story).

## 📋 Phasing and dependencies

- Depends on E03 (offers, initiative) and E04 (ship positions).
- Order: S01 + S02 → S03 → S05 → S04. S04 is last because it is partly blocked on Q04.
- Existing code to reuse: `BestOfferCalculator`, `TradeDesk`, `TransactionAmountCalculator`, `SDCalculator`, `DiceRoller`. R24 replaces submission order as the tie-break; R13/R14 change the amount and price-move rules from what `TradeDesk` does today.

## Screens without a criterion in this spec

s05, s06, s07, s08 (covered by E03 and E04).
