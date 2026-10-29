# Prototype: Deck with map, system details, Trading and Game log (revision 004)

- **Brief:** `.ai/specs/product-brief.md`. Visual references: `.ai/specs/reference.md`. Read-only context: `.uxproof/`.
- **Previous revision:** [`../revision-003/`](../revision-003/README.md), copied and then changed. Revisions 001–003 are unchanged; their SHA-256 checksums match their manifests.
- **Flow:** a player's turn as it appears on screen: Bidding on the Deck and system screens, Ship-jumps on the Deck, and Trading and the Game log on their own screens. Brief Key flows 6–9.
- **Date:** 2026-10-28. No panel report.

## How to open

Open `index.html` in a browser. No server is needed. The grey "Test state" bar lets you switch phase, let other players act, and put yourself first in initiative. It also simulates loading, a failed refresh and "not in this game". **Reset** returns to the start. All data is fictitious, so the prototype is not research evidence and does not show the product is ready for production.

## What changed from revision 003 (creator feedback, 2026-10-28)

| Change | Creator's words / reason |
|---|---|
| Initiative sets a **turn order for the whole turn**. In initiative order, each player picks their place, and the top player may go last. Today the turn order sets the order of jumps; later it will order other actions too. | "highest player decides their place in order for the whole turn, then next initiative player…" |
| **Tie-break in trading uses initiative order.** Mu Herculis ISO shows a tie at 12 CR that Kessler Cargo wins over Altair Trade Co. on initiative. | "initiative order is tie-breaker for goods trading" |
| **Trade order is set during Trading and can change at any time.** The top initiative player reorders the remaining queue by drag-and-drop or ↑/↓ and presses **Resolve next** each time. There is no "confirm order" step. | "no 'accept and start' but 'resolve next' with queue always editable" |
| **Landing:** after every jump, a dialog asks where the ship goes: land at the port, land on the planet, or stay in space. The location stats are shown in the dialog. | "After a jump, a modal opens and asks player when do you want to land (or stay in space)" |
| **Factory units are output per turn.** The table shows "n u output / turn" and notes that output with no warehouse or ship to hold it is lost. | "unit is output, what is produced and can't be stored is lost" |
| **Game log is its own screen** in the rail, next to Deck and Trading, grouped by turn with the newest first. It is no longer a side panel. | "Game log, separate screen like deck, trading, fleet" |
| **Market labels:** 40–60% buy chance is now "in balance" | "for percentage between 40-60 use 'in balance'" |

## Rules and decisions kept

| ID | Meaning kept | Source / owner |
|---|---|---|
| R17, A03 | Your offers are visible only to you until Trading. No other player's offers are listed. | brief / reference.md, creator |
| R13 | The winner chooses an amount from 1 to the maximum | brief, creator |
| R18 | Trades resolve one at a time in the top initiative player's order, and results are public | brief, creator (refined below) |
| R19 | Jumps are instant and can go to any system | brief, creator |
| R21 | Initiative bid of 0 or more, capped by your money. Every bidder pays. | brief, creator (refined below) |
| R23 | A notice appears on a system where you have no ship | brief, creator |
| R03.3, R03.5 | Map of 6 systems and 15 edges with ships. S/D shows its number plus a stepped band. | reference.md, creator |
| N03, N07 | No animation. No fuel, hull, ETA or ship purchasing. | brief, creator |

**Creator decisions from 2026-10-28 that are not yet in the brief.** Each one's change path is creator approval, so it should be added to the brief:

| Decision | Refines |
|---|---|
| Money is private (house rule) | new |
| Initiative is an ordering of all players (highest bid + d2 first). It has two uses: (1) each player picks a place in the **turn order for the whole turn**, in initiative order; (2) the top player decides the trade order. | R18, R20, R21 |
| Initiative order is the tie-break between equal offers | **R08**, which currently says "earliest-submitted tiebreak". This conflicts with R08 and needs the brief's change path. |
| The trade order is decided during Trading and stays changeable until each trade resolves ("Resolve next") | R18. The brief's glossary says the order is set "during Bidding". |
| Every ship may jump once per turn. Jumps happen one at a time, each after the previous one resolves. | Key flow 8 |
| After a jump, the player chooses where the ship ends up: port, planet, or space | new |
| Systems have three locations: Port (class, legal system), Space (police) and Planet (security). These are stats only for now. | new |
| Market positions: Retailer is an explicit first level (no advantage, but the way into the higher levels) | R11 |
| A factory's units are its output per turn. Output that can't be stored is lost. | Q03 |
| Unit amounts are small: single digits are typical, 12–14 is large | Q06 |
| One offer per good per system (spec to follow) | new |

## Assumptions

- **[ASSUMPTION A1]** Starting money (1,240 CR) and ship capacity (12 u) are placeholders until Q06 is answered.
- **[ASSUMPTION A2]** Ship, player and corporation names are invented.
- **[ASSUMPTION A3]** Market positions are invented, and they are assumed to be public. How a player earns a tier is still open (Q04).
- **[ASSUMPTION A5]** The phase timer is a static "0:45 left".
- **[ASSUMPTION A6]** The "I'm done bidding" label is a proposal.
- **[ASSUMPTION A8]** Trading numbers are illustrative (rolls, maximums, price moves) and do not follow the R14 formula.
- **[ASSUMPTION A9]** The "not a player in this game" wording mirrors E02-S04.
- **[ASSUMPTION A10]** Warehouse and factory figures are invented.
- **[ASSUMPTION A11 / A20]** The market labels now have **five** steps: high supply ≥ 95%, moderate supply ≥ 70%, in balance 40–60%, moderate demand ≥ 20%, high demand < 20%. The 20% split is my pick. The design system has only **four** stepped S/D colours (`--sd-easy/fair/hard/bad`), so it will need a fifth step or a different colour mapping. This is a design-system follow-up.
- **[ASSUMPTION A14]** Trading uses one screen that shows the current resolution and the queue.
- **[ASSUMPTION A15]** "Pinged" means an in-game alert only.
- **[ASSUMPTION A16]** There is no rule for a winner who doesn't decide in time, so it is not simulated.
- **[ASSUMPTION A17]** Other players' choices are fixed fixtures: their turn-order picks, jumps and landings.
- **[ASSUMPTION A19]** Players pick turn-order places one at a time, without a timer. The picking happens when Ship-jumps start, because that's when the order is first needed.
- **[ASSUMPTION A21]** The landing dialog has "Land at the port" pre-selected. Pressing Escape counts as "stay in space".
- **[ASSUMPTION A22]** **Resolve next** is available only after the current winner decides. While a winner is deciding, the remaining queue can still be reordered.

## Screen and state map

| From | Action | Expected next state | Rules / assumptions |
|---|---|---|---|
| Deck | Select a system, a fleet location or an offer's system | System details, with its heading focused | R03.3 |
| System | Fold or unfold a section | The section toggles | creator |
| System (Bidding) | Submit an offer that's empty, out of range, then valid | Inline error with focus; once valid, the offer is listed and that good leaves the form | R17, one per good |
| Deck (Bidding) | Enter an invalid bid, then a valid one | Error with focus, then the bid is shown | R21 |
| Test: phase → Ship-jumps | — | Players above you pick their turn-order places (logged), then you're asked "Choose your place in the turn order" | creator, A17, A19 |
| Deck (your pick) | Choose a place → Confirm | The others pick and the full turn order is logged | creator |
| Deck (your slot) | Pick a ship and destination → Jump | The landing dialog opens with focus inside it | creator |
| Landing dialog | Choose port, planet or space → Confirm (or press Escape) | The ship is placed. The map, fleet panel, system screen and log update, and the next jump decision appears. | creator, A21 |
| Deck (your slot) | Pick the ship's current system as destination | Inline error | — |
| Deck (your slot) | "Finish jumping", or jump your last ship | Your slot ends | creator |
| Deck (another player's slot) | "Other players act" | Their jumps and landings are logged | A17 |
| Test: phase → Trading | — | Everyone is taken to Trading | creator, A14 |
| Trading (you first in initiative) | Drag or use ↑/↓ → Resolve next | The top item resolves. The rest of the queue stays editable. | creator, R18 |
| Trading (another player first) | "Other players act" | Vega Lines resolves the next item | creator |
| Trading (another player wins) | "Other players act" | "X sold/bought n u paying p CR each; price moves a → b". Logged, and shown as the last result. | creator, R13 |
| Trading (Mu Herculis ISO) | Resolve it | A tie line appears: "Kessler Cargo wins on initiative order" | creator |
| Trading (you win) | Alert on other screens → enter an amount 1..max → Confirm | The result is shown and logged | creator, R13, A15 |
| Rail | Game log | Log screen grouped by turn, newest first | creator |
| Any | Unknown `#fragment` | "This screen doesn't exist", with a link to the Deck | — |
| Any | Reset | Bidding, no offers or bid, ships back at their start, Vega Lines first in initiative | — |

## Verification

- **Static review: passed.**
  - The JavaScript parses (`node --check`).
  - No external files, network calls, storage or cookies. Forms never submit.
  - User input is written into the page as plain text only (`textContent`).
  - The landing step uses the browser's built-in `<dialog>`, which keeps focus inside it and closes on Escape.
  - Drag-and-drop has a keyboard alternative.
- **Browser walk: not run.** The configured provider `agent-browser` isn't installed, and this skill doesn't install software. Headless-Chrome preview screenshots were taken outside this revision for the creator. They are not a browser walk, and no interaction was exercised.
- **Checks not run:** every interaction in the state map, mouse dragging, keyboard traversal and focus, how the dialog behaves with a screen reader, browser back/forward, the narrow viewport, and Reset.
