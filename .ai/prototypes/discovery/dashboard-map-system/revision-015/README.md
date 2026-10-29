# Prototype: Deck with map, system details, Trading and Game log (revision 015)

- **Brief:** `.ai/specs/product-brief.md` (updated 2026-10-29: D09 no timers; earlier R24–R33, D07, D08, money unit HT). Visual references: `.ai/specs/reference.md`. Read-only context: `.uxproof/`.
- **Previous revision:** [`../revision-014/`](../revision-014/README.md). This revision replaces single-winner Trading with the counter resolution (R34–R38) and blocks a Dictator's offer in Bidding. Revisions 001–014 are unchanged.
- **Made by hand**, following `om-mockup-prototype`'s revision format; the skill was not run for this revision.
- **Flow:** a player's turn as it appears on screen. Bidding and Ship-jumps happen on the Deck, Trading and the Game log have their own screens, and System details shows one system. Brief Key flows 6–9.
- **Date:** 2026-10-29. No panel report.

## How to open

Open `index.html` in a browser; no server is needed. The grey "Test state" bar switches phase, lets other players act, and can put you first in initiative. It also simulates loading, a failed refresh, and being "not in this game". **Reset** restores the start. All data is fictitious; nothing here is research evidence or shows production readiness.

## What changed from revision 014 (creator decisions, 2026-10-29)

| Change | Rule |
|---|---|
| Every good at every system is queued (18 goods), not only goods with offers. Goods without offers still change price. | R36 |
| A resolution discloses all offers first. If the good has a Dictator at that system, they then place an offer or pass, before the roll. | R34, R38 |
| The counter starts at S/D + 2d6. The winning offer trades first, then whichever offer allows the most units at the current counter, each offer once. Every amount uses the start price, and each unit moves the counter. The screen lists each step ("Vega Lines bought 3 u at 11 HT → market 0"). | R34 |
| The price changes once, at the end: start price + modifier of the final counter, clamped to 1–20 HT (Gamma Leporis POLY drops 2 → 1). Map and system screen show the new prices. | R35 |
| After the price change, Wholesalers with no offer for that good there, and the Dictator, may buy or sell up to 5 u at the new price, or pass. | R37 |
| In Bidding, a Dictator's good is left out of the offer forms with "You're Dictator of CHIPS here — you place that offer when it resolves in Trading." | R38 |
| Your money now changes with your trades. | R29 |

## Decisions in play

All of the creator's decisions from 2026-10-28 and 2026-10-29 are recorded in the brief:

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
| D09 | No timers; the game waits for every player and names who it is waiting for (supersedes D04, D06) |
| R34–R38 | Counter resolution, price change, every good every turn, Wholesalers, Dictator (supersede R08, R11, R13, R14, R24) |

Rules from earlier still apply: R13, R17, R19, R23, A03, R03.3, R03.5, N03 and N07.

## Assumptions

- **[ASSUMPTION A1]** Starting money (1,240 HT) and ship capacity (12 u) are placeholders until Q06 is answered.
- **[ASSUMPTION A2]** Ship, player and corporation names are invented.
- **[ASSUMPTION A3]** Market positions are invented, and they are assumed to be public. How a player earns a tier is still open (Q04).
- **[ASSUMPTION A6]** The "I'm done bidding" label is a proposal.
- **[ASSUMPTION A8]** Trading numbers follow R34/R35 and the `SDCalculator` table. Rolls are fixed per good (Mu Herculis ISO rolls 2, Tau Ceti POLY 7, Epsilon Eridani FOOD 9, Sigma Draconis CHIPS 5, Gamma Leporis POLY 6; others follow a fixed pattern). If you reach Trading without placing offers, two sample offers of yours are used (sell POLY at Tau Ceti for 5, buy FOOD at Epsilon Eridani for 15). Cargo limits (R16, R23, R10) aren't simulated; your buys are capped by money (R12).
- **[ASSUMPTION A9]** The "not a player in this game" wording mirrors E02-S04.
- **[ASSUMPTION A10]** Warehouse and factory figures are invented.
- **[ASSUMPTION A11 / A20]** The market labels now have **five** steps: high supply ≥ 95%, moderate supply ≥ 70%, in balance 40–60%, moderate demand ≥ 20%, high demand < 20%. The 20% split is my pick. The design system has only **four** stepped S/D colours (`--sd-easy/fair/hard/bad`), so it will need a fifth step or a different colour mapping. This is a design-system follow-up.
- **[ASSUMPTION A14]** Trading uses one screen that shows the current resolution and the queue.
- **[ASSUMPTION A15]** "Pinged" means an in-game alert only.
- **[ASSUMPTION A17]** Other players' choices are fixed fixtures: turn-order picks, jumps, landings, offers, Dictator offers (Kessler buys POLY at Tau Ceti for 9; Altair sells ISO at Mu Herculis for 10), amounts (Vega takes up to 3 u, Kessler and Altair up to 4) and wholesale choices.
- **[ASSUMPTION A30]** Positions added for the demo, per good per system: you are Wholesaler of ISO at Mu Herculis and Dictator of CHIPS at Sigma Draconis.
- **[ASSUMPTION A31]** When several players may trade wholesale, they are asked in initiative order. "Other players act" runs every other player's pending step until you must act or the good is finished.
- **[ASSUMPTION A19]** Players pick turn-order places one at a time, without a timer. The picking happens when Ship-jumps start, because that's when the order is first needed.
- **[ASSUMPTION A23]** The five price colours are prototype placeholders, not design tokens. Colour is no longer the only cue (WCAG 1.4.1) because each level also has an arrow. The bright green (#16a34a) is about 3.3:1 on the prototype's white background, below 4.5:1 for small text. On the product's dark navy theme the bright token colours sit at 7:1 or more, so the final contrast check belongs to the visual design pass.
- **[ASSUMPTION A27]** Deck side columns by phase. **Bidding:** the right column holds Bid for initiative, Place an offer and Your offers. **Ship-jumps:** the left column holds Your fleet, then Turn order (including the "choose your place" picker); the right column holds Your next jump (during your slot), then Your offers. **Trading:** the right column holds Your offers. Nothing sits under the map in any phase.
- **[ASSUMPTION A29]** On the Trading and System screens, the top bar sits inside the main (left) column instead of spanning the full width, so the main and side columns start on the same line, as on the Deck. The Game log screen has one column, so it is unchanged.
- **[ASSUMPTION A25]** The arrows read as "where the price is likely to go": ↑ high demand, ↗ moderate demand, → in balance, ↘ moderate supply, ↓ high supply. They are derived from the same buy-chance bands as the labels (D08).
- **[ASSUMPTION A24]** The offer form on the system screen is kept as well, so offers can be placed from either the Deck or the system screen.
- **[ASSUMPTION A21]** The landing dialog has "Land at the port" pre-selected. Pressing Escape counts as "stay in space".
- **[ASSUMPTION A22]** **Resolve next** is available once the current good is finished, including its Dictator and wholesale steps. The queue can be reordered meanwhile.

## Screen and state map

| From | Action | Expected next state | Rules / assumptions |
|---|---|---|---|
| Deck | Select a system, a fleet location or an offer's system | System details, with its heading focused | R03.3 |
| System | Fold or unfold a section | The section toggles | creator |
| System (Bidding) | Submit an offer that's empty, out of range, then valid | Inline error with focus; once valid, the offer is listed and that good leaves the form | R17, one per good |
| Deck (Bidding) | Read a system on the map | Inside the larger circle, each good is listed with an arrow and a price in the market colour. The legend under the map explains the arrows. | D07, D08 |
| Deck (Bidding) | Place an offer: pick a system, then a good (names only; goods already offered there are left out), a price and Buy/Sell | Inline error on a bad price. A valid offer is listed in "Your offers" and that good leaves the list for that system. | D07, R33 |
| Deck (Bidding) | Enter an invalid bid, then a valid one (top of the right column) | Error with focus, then the bid is shown | R27 |
| Deck (Bidding) | "Other players act" (repeat) | Each press marks the next other player as done; the top bar's waiting list shrinks | D09 |
| Deck (Bidding) | "I'm done bidding" when everyone else is done (or the last other player finishing after you) | Bidding closes and the phase becomes Ship-jumps | R17, D09 |
| Test: phase → Ship-jumps | — | Players above you pick their turn-order places (logged), then you're asked "Choose your place in the turn order" | creator, A17, A19 |
| Deck (your pick) | Choose a place → Confirm | The others pick and the full turn order is logged | creator |
| Deck (your slot) | Pick a ship and destination → Jump | The landing dialog opens with focus inside it | creator |
| Landing dialog | Choose port, planet or space → Confirm (or press Escape) | The ship is placed. The map, fleet panel, system screen and log update, and the next jump decision appears. | creator, A21 |
| Deck (your slot) | Pick the ship's current system as destination | Inline error | — |
| Deck (your slot) | "Finish jumping", or jump your last ship | Your slot ends | creator |
| Deck (another player's slot) | "Other players act" | Their jumps and landings are logged | A17 |
| Test: phase → Trading | — | Everyone is taken to Trading | creator, A14 |
| Trading (you first in initiative) | Drag or use ↑/↓ → Resolve next | The top good resolves; the rest of the queue stays editable | R25, R36 |
| Trading (another player first) | "Other players act" | Vega Lines resolves the next good; other players' steps run until you must act | R25, A31 |
| Trading (any good) | — | Offers are disclosed with a Result column; steps list "Market +3 → buyers first", each trade with the new market value, then "No one else can trade at this market. {good} price …: a → b HT" | R34, R35 |
| Trading (Gamma Leporis POLY, no offers) | Resolve it | "No trades. POLY price at Gamma Leporis: 2 → 1 HT" (clamped) | R35, R36 |
| Trading (Tau Ceti POLY) | Your sell wins first → enter 1..7 → Confirm | Kessler (Dictator, buy at 9) and Vega (buy at 8) then trade against the moved market; price 6 → 7 if you sell 7. Kessler then buys 2 u wholesale as Dictator | R34, R37, R38 |
| Trading (Epsilon Eridani FOOD) | Your buy at 15 wins → choose an amount | Buy 4 and Kessler (buy at 14) can't trade; buy 1 and Kessler buys 1 | R34 |
| Trading (Mu Herculis ISO) | Vega buys, Altair (Dictator) sells → you're asked as Wholesaler | Buy or sell up to 5 u at 10 HT, or Pass; Altair then sells 2 u as Dictator | R37, A30 |
| Trading (Sigma Draconis CHIPS) | You're Dictator → place an offer (e.g. buy at 16) or Pass, before the roll | Vega sells 1, then your buy trades up to 3 u; price 14 → 16; then your Dictator wholesale prompt | R38, R37 |
| Trading (your decision, on another screen) | — | Alert "Your decision is needed: {system} {good}" with a link to Trading | A15 |
| Deck or System (Bidding, Sigma Draconis) | Pick Sigma Draconis in the offer form | CHIPS is left out, with the Dictator note | R38 |
| Rail | Game log | Log screen grouped by turn, newest first | creator |
| Any | Unknown `#fragment` | "This screen doesn't exist", with a link to the Deck | — |
| Any | Reset | Bidding, nobody done, no offers or bid, prices and money as at the start, ships back at their start, Vega Lines first in initiative | — |

## Verification

- **Static review: passed.**
  - Revision 014 adds no new inputs; only the top bar text and the simulated "done" state changed.
  - The JavaScript parses (`node --check`).
  - No external files, network calls, storage or cookies; forms never submit.
  - User input is written into the page as plain text only (`textContent`).
  - The map's accessible names include each good, its price and its market label.
  - Drag-and-drop has a keyboard alternative, and the landing dialog uses the browser's built-in `<dialog>`.
- **Scripted headless run: passed.** Headless Chrome drove a full Trading phase (all 18 goods, other players simulated, your decisions taken at max). Every logged step matched a hand calculation, for example Tau Ceti POLY −3 → you sell 7 → +4 → Kessler buys 4 → 0 → Vega buys 3 → −3, price 6 → 7. Money ended at 1,115 HT. Screenshots of the amount, Dictator and wholesale prompts and of the Bidding Dictator note were checked.
- **Browser walk: not run.** `agent-browser` still isn't installed, so no one clicked through by hand: mouse dragging, keyboard focus after each step and the narrow viewport are unchecked.
- **Checks not run:**
  - every interaction in the state map;
  - mouse dragging;
  - keyboard traversal and focus;
  - the dialog with a screen reader;
  - browser back and forward;
  - the narrow viewport;
  - Reset.
