# E03 — Bidding phase (#9)

**Prototype:** `.ai/prototypes/discovery/dashboard-map-system/revision-014/` (revision 013 accepted as the first version, D07; 014 removes the timer, D09). Open `index.html`; the grey "Test state" bar is a prototype control, not a product feature.
**Brief:** `.ai/specs/product-brief.md` — R17, R24, R27, R28, R29, R31, R33, R38, R39, R41, R42, D07, D08, D09, D10.
**Siblings:** E04 Ship-jumps (`2026-10-29-e04-ship-jumps-phase.md`), E05 Trading (`2026-10-29-e05-trading-phase.md`).

## 📝 TLDR

The first playable phase of a turn. Every player sees the board — six systems with goods, prices and market labels — on the Deck, and the details of one system on System details. During Bidding each player secretly places at most one offer per good per system and may bid money for turn initiative. Bidding closes when everyone has signalled done; there is no timer (D09). Initiative order is then rolled and published in the Game log. This epic also builds the turn and phase engine that E04 and E05 run on, and the screens they share.

## 📝 Screen IDs

These IDs are fixed and shared by E03–E05. Acceptance criteria, review comments and later prototypes refer to them; don't renumber.

| ID | Screen / overlay | Prototype route | Introduced in |
|---|---|---|---|
| s01 | Deck — map, fleet, phase panels | `#dashboard` | E03 |
| s02 | System details — locations, market, positions | `#system-{id}` | E03 |
| s03 | Trading — current resolution, queue | `#trading` | E05 |
| s04 | Game log — public events by turn | `#log` | E03 |
| s05 | Landing dialog (over s01) | — | E04 |
| s06 | Loading game | — | E03 |
| s07 | Not a player in this game | — | E03 (mirrors E02-S04's `403`) |
| s08 | Unknown screen | any other fragment | E03 |
| s09 | Game over — final money and winner | — | E05 (not in the prototype yet) |
| s10 | Sign in (details, then code) | `#sign-in` | E01-S03 |
| s11 | Create a game (length in turns) | `#new` | E02-S05 |
| s12 | Lobby — players joining, Start game | `#game/{id}` before the start | E02-S05 |

A top bar on s01–s04 shows turn number, phase (with its state), your money (R29) and, after Bidding, initiative order. A "couldn't refresh" notice can appear on s01–s04.

## 📋 Stories and acceptance criteria

Each criterion names its screen and the rule it comes from. `proto-A…` IDs are the prototype README's assumptions, not the brief's A01–A06. "Proposed" marks a default I picked where the brief is silent; see Open questions.

### E03-S01 — A game runs turns of three phases

1. [s01–s04 top bar] [N06, R04, R39] A game is always in one turn, shown as "Turn 4 of 20" (the length the creator chose, R41), and one phase: Bidding → Ship-jumps → Trading, then the next turn's Bidding.
2. [—] [D09] No phase or step has a deadline and nobody is auto-skipped. The game waits for every player.
3. [—] [R17, D09] Bidding closes the moment the last player signals done.
4. [—] [A04] An action that reaches the server on the same instant Bidding closes (the last "done") is either applied or rejected, never applied twice or lost silently; the player is told which (see S03-7).
5. [s01–s04] [D01] Every player's client picks up a phase change within the polling interval without a manual refresh.

### E03-S02 — Deck and System details show the shared board

1. [s01] [D07] The map shows the six systems as a flat-topped hexagon ring (Gamma, Beta / Sigma, Mu / Tau, Epsilon), every pair connected.
2. [s01] [D07, D08] Inside each system's circle, every good it trades is listed with its arrow and its price in HT, coloured by market label. A legend under the map explains arrows and labels. Colour is never the only cue (arrow + label in the accessible name).
3. [s01] [D08] Market label comes from buy chance P(2d6 + S/D ≥ 0): high supply ≥ 95%, moderate supply ≥ 70%, in balance 40–60%, moderate demand, high demand. The moderate/high demand split is a setting, default 20% (the brief tracks it under D08).
4. [s01] [—] Under each system: your ships by name (■), other players' ships by corporation initials (□), or "no ships".
5. [s01] [R42] "Your fleet" lists each of your ships separately with its system (link to s02), location (Port / Planet / Space), every good it carries with the amount, and the cargo space it has used: "Wayfarer — Tau Ceti · Port — POLY 4, ISO 3, CHIPS 12 · cargo 7 / 10 used". An empty ship says "Empty · cargo 0 / 10 used".
6. [s01→s02] Selecting a system on the map, a fleet entry or an offer's system opens s02 with its heading focused.
7. [s02] [D07, R31] Three foldable sections: **Locations and ships** (Port: class, legal system; Space: police; Planet: security; ships in each; your own ships there also show their goods and cargo space used, as in S02-5; other players' cargo is never shown), **Market** (good, price, S/D, label with arrow and hint), **Market positions** (Dictator / Wholesaler / Retailer per good, R28; players without a position are not listed). The first two are open by default. Warehouses and factories are not shown (N08).
8. [s02] [—] Empty states: "No ships" per location; "Nobody holds a position for this good yet" in Market positions (everyone starts with none, R40).
9. [s06] While the first state load is in flight, "Loading game…" is shown.
10. [s07] [E02-S04, A05] A signed-in user who isn't in this game sees "You're not a player in this game" and none of its state.
11. [s08] An unknown screen link shows "This screen doesn't exist" with a link to s01.
12. [s01–s04] [D01] When a poll fails, the last known state stays on screen with "Couldn't refresh the game. Showing the state from N seconds ago." and **Retry now**.

### E03-S03 — A player places secret trade offers

1. [s01] [D07] During Bidding, "Place an offer" sits right of the map: system, then good (names only; goods already offered at that system are left out, R33), price, Buy/Sell.
2. [s02] [D07, proto-A24] The same form is on s02 for that system during Bidding.
3. [s01, s02] [R03] Price must be a whole number 1–20. Empty or out-of-range → inline error, focus to the field, nothing placed.
4. [s01, s02] [R33] At most one offer per good per system per turn. When every good at a system is offered: "You have an offer on every good there this turn."
5. [s02] [R23] If you have no ship at that system, the form warns: "You have no ship here. If a buy wins, the goods are lost; a sell has nothing to draw from. A ship can still jump here before Trading." The offer is still allowed.
6. [s01] [R17] "Your offers this turn" lists your offers with a link to their system and states "Only you can see these until Trading resolves them." Empty: "No offers yet this turn."
7. [s01, s02] [R17, A04] An offer submitted after Bidding has closed is rejected with "Bidding has closed — this offer wasn't placed." (proposed wording); typed values stay in the form.
8. [API] [R17, A05] No endpoint returns another player's offers during Bidding — including the existing `/api/trade-offers`, which must stop listing all players' offers.
9. Offers can't be edited or withdrawn in v1 (proposed, OQ-3).
10. [s01, s02] [R38] A Dictator can't place an offer for the good they're Dictator of at that system: the good is left out of the form, with "You're Dictator of {good} here — you place that offer when it resolves in Trading." The API rejects it too.

### E03-S04 — A player signals done bidding

1. [s01 top bar] [R17, proto-A6] "I'm done bidding" is shown during Bidding until pressed; afterwards "You signalled done — you can still add offers".
2. [—] [R17] Done is a readiness flag, not a commit: offers and the initiative bid can still be placed or changed until Bidding closes.
3. [s01 top bar] [D09] The top bar names who the game is waiting for: "Bidding — waiting for Vega Lines, Kessler Cargo" (and "you" while you haven't signalled done). Players nudge each other outside the game, so names matter more than a count.
4. [—] [D09] When the last player signals done, Bidding closes for everyone and the game moves to Ship-jumps.

### E03-S05 — Players bid for turn initiative

1. [s01] [D07, R27] During Bidding, "Bid for initiative" is at the top of the right column. Whole number ≥ 0, at most your money; errors inline with focus: "Enter a whole number of HT, 0 or more." / "A bid can't be negative. Enter 0 to bid nothing." / "You have X HT. Enter X or less."
2. [s01] [R27] After placing: "Initiative bid: N HT (paid when Bidding closes, win or lose)" in Your offers; the button becomes **Change bid**. The bid can be changed until Bidding closes.
3. [—] [R27] When Bidding closes, every bidder pays their bid (all-pay). Initiative order = all players by bid + d2, highest first; ties rerolled until broken.
4. [—] [Q07] A player who never bid is treated as bidding 0 and still rolls (proposed until Q07 is answered).
5. [s01–s04 top bar] From Ship-jumps on, the top bar shows "Initiative: A → B → C → D".
6. [—] [R29] Money and bid amounts stay private: other players see only the resulting order (proposed).

### E03-S06 — The Game log lists public events

1. [s04] [D07] Public events grouped by turn, newest turn first, each turn foldable.
2. [s04] From E03: "Initiative order (bid + d2): …". E04 and E05 add turn order, jumps, landings, trading order and resolved trades.
3. [s04] Never logs offers, bids, money or anything confidential (R17, R29).
4. [s04] Empty (turn 1, nothing yet): "No public events yet." (proposed).

## 📝 States checklist (s01, s02, s04, s06–s08 in Bidding)

| State | Where | Covered by | In prototype? |
|---|---|---|---|
| Default | s01, s02, s04 | S02, S03, S05, S06 | yes |
| Empty | Your offers, s02 sections, s04 | S02-8, S03-6, S06-4 | partly (no empty log) |
| Loading | s06 | S02-9 | yes (simulated) |
| Poll failed / stale | s01–s04 | S02-12 | yes (simulated) |
| No access | s07 | S02-10 | yes (simulated) |
| Unknown link | s08 | S02-11 | yes |
| Validation error | offer form, bid form | S03-3, S05-1 | yes |
| Conflict: Bidding closed while form open | s01, s02 | S03-7 | **no** |
| Waiting for named players | s01 top bar | S04-3 | yes (revision 014) |
| Last player signals done → Bidding closes | s01 | S04-4 | yes (revision 014) |
| A player is away for a long time | s01 top bar | S04-3 — the game waits and names them (D09); stall risk accepted (A06) | yes |
| Not enough money to bid | bid form | S05-1 | yes |

## 📝 Decisions in play

R17 (secret offers; done is a flag), R27 (initiative ordering, all-pay), R29 (money private), R33 (one offer per good per system), R28/R31 (shown on s02, no mechanics here), D09 (no timers; name who we wait for), D07 (screens), D08 (market labels), A04 (close race), A05 (confidential reads).

## ❓ Open questions — gaps the prototype didn't decide

| # | Question | Blocking? | Proposed default |
|---|---|---|---|
| OQ-3 | Can an offer be withdrawn or changed during Bidding? With R33, a typo locks that good for the turn. | no | no for v1; revisit after playtest |
| Q07 | (brief) Is "didn't bid" different from bidding 0? Affects who rolls for initiative. | no | same as 0, still rolls |

## 🚫 Out of scope

D05 resume (Later); R06 confidential info trading; warehouses and factories (N08); design-system work for the fifth market colour (D08 follow-up).

## 📋 Phasing and dependencies

- Depends on E02 (game exists, membership check, polling — E02-S01, S04).
- Order: S01 → S02 → S03/S04 → S05 → S06. S02 can be built against a fixed state before S01.
- `risk-high`: S03-8 and S05-6 are confidential-read checks (A05); the denied-path test is required evidence.

## 📝 Architecture notes (to refine when a story is picked up)

- Turn and phase state lives with the game (E02's game state); no clock or scheduler is needed: phases advance only on player actions (D09).
- Reuse `trading.TradeOffer` and `trading.SDCalculator`; `TradeOfferBook` must become per-game and per-player-readable (R01, R17).
- The polling view is per player: shared state plus that player's own offers, bid and money only.

## Screens without a criterion in this spec

s03 and s09 (E05), s05 (E04).
