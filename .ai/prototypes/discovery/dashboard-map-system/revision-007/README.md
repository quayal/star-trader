# Prototype: Deck with map, system details, Trading and Game log (revision 007)

- **Brief:** `.ai/specs/product-brief.md` (updated 2026-10-28: R24–R33, D07, D08, and the HT money unit in the glossary). Visual references: `.ai/specs/reference.md`. Read-only context: `.uxproof/`.
- **Previous revision:** [`../revision-006/`](../revision-006/README.md), copied and then changed. Revisions 001–006 are unchanged; their SHA-256 checksums match their manifests.
- **Flow:** a player's turn on screen: Bidding on the Deck, Ship-jumps on the Deck, Trading and the Game log on their own screens, and System details. Brief Key flows 6–9.
- **Date:** 2026-10-28. No panel report.

## How to open

Open `index.html` in a browser; no server is needed. The grey "Test state" bar switches phase, lets other players act, and can put you first in initiative. It also simulates loading, a failed refresh, and "not in this game". **Reset** returns to the start. All data is fictitious. This is not research evidence and doesn't show production readiness.

## What changed from revision 006 (creator feedback, 2026-10-28)

| Change | Creator's words |
|---|---|
| The goods picker in the Deck offer form lists names only, because the price and market level are already on the map above it | "goods picker on placing offer: no need for price and demand -- it's above on map" |
| The map is now three rows of two (2:2:2), from top left: Gamma Leporis, Beta Hydri / Sigma Draconis, Mu Herculis / Tau Ceti, Epsilon Eridani | "I want (2:2:2), order from left top: Gamma, Beta, Sigma, Mu, Tau, Epsilon" |
| The money unit is **HT** everywhere: prices, money, bids, amounts and log entries | "Money units are HT, not CR" |

## Decisions in play

All of the creator's decisions from 2026-10-28 are now recorded in the brief:

| Brief ID | What it covers |
|---|---|
| R24 | Tie-break by initiative order (supersedes R08's tie-break) |
| R25 | Trading order decided during Trading, editable, "Resolve next" (supersedes R18) |
| R26 | Turn order picked in initiative order; per-ship jumps, one after another (supersedes R20) |
| R27 | Initiative is an ordering of all players (supersedes R21) |
| R28 | Retailer is an explicit first tier |
| R29 | Money is private |
| R30 | Landing choice after each jump |
| R31 | Port, Space and Planet with their stats |
| R32 | Factory output lost unless stored |
| R33 | One offer per good per system |
| D07 | Screen structure |
| D08 | Market labels |

Rules from earlier still apply: R13, R17, R19, R23, A03, R03.3, R03.5, N03 and N07.

## Assumptions

- **[ASSUMPTION A1]** Starting money (1,240 HT) and ship capacity (12 u) are placeholders until Q06 is answered.
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
- **[ASSUMPTION A23]** The five price colours are prototype placeholders, not design tokens. Colour is no longer the only cue (WCAG 1.4.1) because each level also has an arrow. The bright green (#16a34a) is about 3.3:1 on the prototype's white background, below 4.5:1 for small text. On the product's dark navy theme the bright token colours sit at 7:1 or more, so the final contrast check belongs to the visual design pass.
- **[ASSUMPTION A26]** In the 2:2:2 grid, the two edges that run top-to-bottom within a column (Gamma–Tau and Beta–Epsilon) would pass through the middle system, so they are drawn bowed outward. The other 13 edges are straight.
- **[ASSUMPTION A27]** The taller map pushes "Place an offer" below the fold at about 900 px of screen height. Moving the form (for example into the right column next to "Your offers") is a layout choice for the creator.
- **[ASSUMPTION A25]** The arrows read as "where the price is likely to go": ↑ high demand, ↗ moderate demand, → in balance, ↘ moderate supply, ↓ high supply. They are derived from the same buy-chance bands as the labels (D08).
- **[ASSUMPTION A24]** The offer form on the system screen is kept as well, so offers can be placed from either the Deck or the system screen.
- **[ASSUMPTION A21]** The landing dialog has "Land at the port" pre-selected. Pressing Escape counts as "stay in space".
- **[ASSUMPTION A22]** **Resolve next** is available only after the current winner decides. While a winner is deciding, the remaining queue can still be reordered.

## Screen and state map

| From | Action | Expected next state | Rules / assumptions |
|---|---|---|---|
| Deck | Select a system, a fleet location or an offer's system | System details, with its heading focused | R03.3 |
| System | Fold or unfold a section | The section toggles | creator |
| System (Bidding) | Submit an offer that's empty, out of range, then valid | Inline error with focus; once valid, the offer is listed and that good leaves the form | R17, one per good |
| Deck (Bidding) | Read a system on the map | Inside the larger circle, each good is listed with an arrow and a price in the market colour. The legend under the map explains the arrows. | D07, D08 |
| Deck (Bidding) | Place an offer: pick a system, then a good (names only; goods already offered there are left out), a price and Buy/Sell | Inline error on a bad price. A valid offer is listed in "Your offers" and that good leaves the list for that system. | D07, R33 |
| Deck (Bidding) | Enter an invalid bid, then a valid one | Error with focus, then the bid is shown | R27 |
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
| Trading (another player wins) | "Other players act" | "X sold/bought n u paying p HT each; price moves a → b". Logged, and shown as the last result. | creator, R13 |
| Trading (any resolution) | Resolve it | Plain result: "+1 → highest offer wins" or "−3 → lowest offer wins". The dice arithmetic is not shown. | D07 |
| Trading (Mu Herculis ISO) | Resolve it | Tie line: "Tie at 12 HT — Kessler Cargo wins on initiative order" | R24 |
| Trading (you win) | Alert on other screens → enter an amount 1..max → Confirm | The result is shown and logged | creator, R13, A15 |
| Rail | Game log | Log screen grouped by turn, newest first | creator |
| Any | Unknown `#fragment` | "This screen doesn't exist", with a link to the Deck | — |
| Any | Reset | Bidding, no offers or bid, ships back at their start, Vega Lines first in initiative | — |

## Verification

- **Static review: passed.**
  - The JavaScript parses (`node --check`).
  - No external files, network calls, storage or cookies; forms never submit.
  - User input is written into the page as plain text only (`textContent`).
  - The map's accessible names include each good, its price and its market label.
  - Drag-and-drop has a keyboard alternative, and the landing dialog uses the browser's built-in `<dialog>`.
- **Browser walk: not run.** The configured provider, `agent-browser`, isn't installed, and this skill doesn't install software. Headless-Chrome preview screenshots were taken outside this revision for the creator. They are not a browser walk, and no interaction was exercised.
- **Checks not run:**
  - every interaction in the state map;
  - mouse dragging;
  - keyboard traversal and focus;
  - the dialog with a screen reader;
  - browser back and forward;
  - the narrow viewport;
  - Reset.
