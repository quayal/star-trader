# Bidding: the Market screen

**Brief:** `.ai/specs/product-brief.md`: R12 (buys capped by money), R16 (overflow lost), R17 (secret offers), R23/R49
(presence: a ship or a warehouse), R33 (one offer per good per system), R34–R38 (resolution, price move, Wholesaler,
Dictator), R42 (cargo), R45 (warehouses), R68 (jump chance), R109–R115 (tip-offs and events), D08 (market level from
the S/D indicator), D34 (the phase rail, `2026-11-26-jumps-screen-and-phase-rail.md`).
**Depends on:** the phase rail from `2026-11-26-jumps-screen-and-phase-rail.md`. This spec moves the rail's Bidding link
from the Deck to the new screen.
**Source:** the creator, 2026-11-26. Trade offers need a lot at once: prices in every system, the S/D situation,
warehouses, and all the goods you have and where they are.

## 📝 TLDR

Bidding gets a **Market** screen, `#game/{id}/market`. It is built around a 4 goods × 6 systems matrix, so all 24 markets
can be compared at a glance. Each cell shows the price, which way the market leans (buyers or sellers), the player's
own stock and position there, and their offer if they've placed one. Selecting a cell opens a side panel with the
detail and the offer form. Around the matrix, a **holdings** strip shows what the player has and where, and an
**offers check** warns about offers the player can't carry out.

The pattern is overview first, then zoom and filter, then details on demand: a dense comparison grid like a trading
terminal, with an inspector panel for the one market being worked on. Because Bidding happens *before* Jumps, every
offer is a plan ("I'll have a ship there with space, or with the goods"). The screen shows whether the plan can work:
is a ship already there, or how likely is one to get there?

## 📋 Acceptance criteria

### Route and layout

1. **Route.** A change into Bidding opens the Market screen (`screenForPhaseChange` gains `BIDDING: 'market'`), and the
   rail's Bidding item links there. Focus goes to its heading. The initiative bid moves here too, as a compact panel
   above the matrix, so all of Bidding is on one screen. After Bidding the screen stays reachable from the rail,
   read-only, as a market overview.
2. **Layout, wide screens.** Holdings strip on top. The matrix fills the main column, and the inspector panel sits on
   the right. Below the matrix: Your offers and the offers check. **Phone:** the matrix becomes one card per good, with
   six system rows in each, and the inspector opens as a bottom sheet.

### The matrix

3. **Rows are goods (CHIPS, FOOD, ISO, POLY) and columns are systems.** Each cell shows:
   - **price** (big), and its move last turn (`▲2`, `▼1`, `—`) from the last resolution;
   - **lean:** the D08 market level and the buy chance as a percentage (`buyers 72%`), from the existing
     `buyChance(indicator)`. It's a small two-sided bar, buyers on one side and sellers on the other, so the lean reads
     without the number. Tokens only;
   - **you there:** units you hold at that system (ships in Port, Planet or Space, and the warehouse), and your market
     position tier as a badge (`R`, `W`, `D`);
   - **your offer**, if any: `Buy 7` or `Sell 12`, as a chip. A Dictator cell says `Dictator: you offer in Trading`
     (R38).
4. **Row extremes.** In each row, the cheapest price and the dearest price are marked. A line under the row gives the
   spread: `FOOD: cheapest Sigma Draconis 4, dearest Beta Hydri 14 (spread 10)`. This is the route to look at. Not
   advice, just the two numbers.
5. **Column header (per system):** the system name; your presence there **now**, e.g. `Wayfarer (Port), warehouse 12/20`;
   and the **reach**, the best jump chance of any of your ships that can still jump, e.g. `reach 83% (Wayfarer)`.
   `here` if a ship or warehouse is already there; `—` if nothing can get there this turn.
6. **Filters** (a toggle group above the matrix): `All`, `Where I have goods`, `Where I can be` (presence or reach ≥ 50%),
   `My offers`. Filtering dims cells; it doesn't remove them, so the grid's shape stays.
7. **Selecting a cell** (click, or arrow keys then Enter; the matrix is a grid with roving focus) opens it in the
   inspector. The selected cell is outlined and its row and column headers are highlighted.

### The inspector (one market)

8. **Detail:** price, S/D indicator (signed), buy chance, last turn's resolution here (winner type, price move), who
   holds positions here (public: Wholesalers, Dictator), and any **learned** tip-off or announced event that affects this
   good or system (R110, R115), e.g. `Event this turn: Food +3 at Beta Hydri`.
9. **Your side:**
   - your stock here and the free space here, from ships and the warehouse, plus the free space on ships that can
     reach it, with each ship's chance;
   - your stock of this good elsewhere, e.g. `FOOD: 6 on Wayfarer at Tau Ceti (reach 58%)`;
   - your cash.
10. **The offer form** (the existing rules and messages from `offers.js`): Buy/Sell, price 1–20, place, change, withdraw.
    Next to the price, a fixed hint: `Buy above 11 or sell below 4 moves no further` (the R15 caps relative to the
    current price), so players don't overpay for nothing. One offer per good per system (R33), and the Dictator block
    (R38), as today.

### Holdings strip

11. **What you have, where.** One chip per place that holds goods, ships and warehouses alike. Example: `Wayfarer · Tau
    Ceti Port · FOOD 6, ISO 2 · free 0/8`; `Warehouse · Beta Hydri · POLY 10 · free 10/20`. A chip also shows whether a
    ship can still jump. Clicking a chip filters the matrix to that place's column.

### Offers check (private, live)

12. Under the list of your offers, a check per offer and for the set as a whole. All are warnings, never blocks:
    - **No way to be there.** A buy or sell at a system with no ship or warehouse there now, and no ship that can jump
      there: `Tau Ceti: nothing of yours can be there. A won buy is lost (R23).`
    - **Relies on a jump.** Presence only through a jump: `Relies on Wayfarer reaching Tau Ceti (58%).`
    - **Too many places.** Offers in more systems than you have ships that can jump plus places already covered:
      `Offers in 4 systems, but you can cover at most 3 this turn.`
    - **Sell with nothing.** A sell of a good you hold nowhere that could be there: `Nothing to sell: no ISO at Sigma
      Draconis, and no ship with ISO can reach it.`
    - **Buy with no space.** A buy where no space can be there (cargo goods only; CHIPS and FOOD take no space but need a
      ship or warehouse, R47 wording): `No free space can reach Mu Herculis; overflow is lost (R16).`
13. The check is a pure function in JS over `you`, `board` and the ships' `jumpChances`, and it has unit tests. No new
    server rule: it reads only what the viewer already gets.

### Server

14. **Last turn's move per market.** `board[].market[]` gains `lastChange` (the price move in the last resolution, signed;
    0 on turn 1) if the game doesn't already keep it. Public, like the price.
15. Nothing else new: prices, indicators, positions, holdings, jump chances, tip-offs and conditions are all in the
    game state today.

### Tests

16. JS unit tests for the cell model (extremes, presence and reach, the offers check, filters) and for the
    phase-change route. A Kotlin test for `lastChange`. `GameControllerTest` covers the field.

## Defaults I chose

- **Goods as rows, systems as columns.** Comparing one good across systems (where to buy, where to sell) is the
  common question. A row is that question.
- **Warnings, not blocks.** A bid that looks impossible may be a bluff, or part of a plan the screen can't see.
- **No predicted amount.** The amount you get depends on the roll and the other offers (R34). A number before the roll
  would be a guess dressed as a fact. The R15 hint is the one certainty shown.
- **The map stays on the Deck.** The matrix is for comparing, the map for geography. System details keeps its offer
  form for players who work system by system.
- **The initiative bid moves here** so Bidding is one screen. The Deck no longer has a Bidding panel.

## Brief changes

- New rule R138: during Bidding, the player sees every market (price, lean, last move), their holdings and presence or
  reach per system, and a check of their offers against them, on one screen.
- D15: Bidding opens the Market screen instead of the Deck.

## Out of scope

- Predicting trade amounts or other players' offers.
- Planning tools across turns (routes, saved plans). The notepad covers that for now.
- A Market screen of its own for Trading. Trading keeps its screen; this one is read-only after Bidding.
- Screens for Initiative and Control. Initiative keeps the Deck's turn-order panel, and Control stays a label on the rail.
