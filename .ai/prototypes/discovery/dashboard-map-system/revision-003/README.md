# Prototype: Deck with map, system details and Trading (revision 003)

- **Brief:** `.ai/specs/product-brief.md`. Visual references: `.ai/specs/reference.md`. Read-only context: `.uxproof/`.
- **Previous revision:** [`../revision-002/`](../revision-002/README.md), copied from there and then changed. Revisions 001 and 002 are unchanged; their SHA-256 checksums match their manifests.
- **Flow:** the player's turn as seen on screen: Bidding (Deck and system screens), Ship-jumps (Deck) and Trading (its own screen). Brief Key flows 6–9.
- **Date:** 2026-10-28. No panel report.

## How to open

Open `index.html` in a browser; no server is needed. Use the grey "Test state" bar to switch phase, let other players act, and put yourself first in initiative. It also simulates the loading, failed-refresh and not-in-this-game states. **Reset** returns to the start. All data is fictitious. This is not research evidence and doesn't show production readiness.

## What changed from revision 002 (creator feedback, 2026-10-28)

| Change | Creator's words / reason |
|---|---|
| Market positions table has **Dictator, Wholesaler and Retailer** columns. Retailer is an explicit level, not a default; players with no position aren't listed. | "retailer is the first level position, no advantage, but entry point for others. Not the default" |
| Warehouses and factories table: type, owner, goods, units. Warehouses show `stored / capacity u`. | "Factories: who, which good, how many units" |
| All unit amounts are small: ship capacity 12 u, cargo 0–8 u, trade maximums 2–5 u, warehouses 10–14 u | "Units of goods generally are one digits, 12 or 14 is a large amount" |
| Initiative is an **order of all players**, highest bid + d2 first, shown in the top bar and the log | "it goes from highest to lowest (not only one player with initiative)" |
| **Jump order:** players pick their jump position in initiative order. The top player may take the last position; the simulated Vega Lines does. | "Players, per initiative, decide the order of jumps (player with initiative may decide to jump the latest)" |
| **Jumps are per ship, one decision at a time.** Each jump resolves (always successfully in the MVP), then you choose the next ship or "Finish jumping". Each ship jumps at most once per turn. | "Jumps are per ship. Player makes jump decision after previous jump was successful… no all at once" |

## Rules and decisions kept

| ID | Meaning kept | Source / owner |
|---|---|---|
| R17, A03 | Your offers are visible only to you until Trading. No list of other players' offers. | brief / reference.md, creator |
| R18 | Trades resolve one at a time in the initiative holder's order. Results are public. | brief, creator |
| R13 | The winner chooses an amount from 1 to the maximum and is never forced to take the maximum | brief, creator |
| R19, R20 | Jumps are instant, any system is allowed, players act in initiative order, and choices are open (logged) | brief, creator |
| R21 | Initiative bid of 0 or more, capped by money; every bidder pays | brief, creator |
| R23 | Notice on a system where you have no ship | brief, creator |
| R03.3 | Map with 6 systems and 15 edges, ships shown including others'; a click opens the system | reference.md, creator |
| R03.5 | S/D always shows its number; stepped bands | reference.md, creator (stepped: 2026-10-28) |
| N03, N07 | No animation; no fuel, hull, ETA or ship purchasing | brief, creator |
| **Money is private** (house rule) | Other players' money is never shown | **creator, 2026-10-28, not yet in the brief.** Change path: creator approval, so add it to the brief. |
| **Jumps: every ship may jump once per turn, one decision at a time** | Each jump resolves before the next decision | **creator, 2026-10-28, not yet in the brief.** Clarifies Key flow 8; add it to the brief. |
| **Initiative is an ordering of all players** (highest bid + d2 first) | Shown and logged as an order | **creator, 2026-10-28, not yet in the brief.** Refines R21 ("the winner"). |
| **Jump positions are chosen in initiative order** | The top player may choose to jump last | **creator, 2026-10-28, not yet in the brief.** Refines R20. |
| **Market positions: Retailer is an explicit first level** | Listed like the other tiers | **creator, 2026-10-28.** Refines R11. |
| **Unit amounts are small** (single digits typical, 12–14 large) | All fixtures | **creator, 2026-10-28.** Feeds Q06. |
| **One offer per good per system** | Enforced in the offer form | **creator, 2026-10-28, not yet in the brief.** The creator will spec it. |
| **Trading order is set by drag and drop by the player first in initiative** | Shown as the first Trading step | **creator, 2026-10-28.** UI for R18. |
| **What initiative is for** | Initiative only sets order: (1) the order in which players choose jump positions, later also other ordered actions; (2) the trade order, decided by the player first in initiative | **creator, 2026-10-28, not yet in the brief.** Refines R18, R20, R21. |
| **System locations: Port, Space, Planet** with stats (class, legal system, police, security) | Ships are shown in one of the three; numbers only, no mechanics yet | **creator, 2026-10-28, not yet in the brief** |

## Assumptions

- **[ASSUMPTION A1]** Starting money (1,240 CR) and ship capacity (12 u) are placeholders. Q06 is open.
- **[ASSUMPTION A2]** Ship, player and corporation names are invented.
- **[ASSUMPTION A3]** Market positions are invented, and are assumed to be public. How a tier is earned is open (Q04).
- **[ASSUMPTION A5]** The phase timer is a static "0:45 left". Its length (D04) isn't set.
- **[ASSUMPTION A6]** The label "I'm done bidding" is a proposal. It acts as a readiness flag (R17).
- **[ASSUMPTION A8]** Trading numbers are illustrative: rolls, maximums, and the price move after your decision. The price move is **not** the R14 formula.
- **[ASSUMPTION A9]** The "not a player in this game" wording mirrors the 403 response in E02-S04.
- **[ASSUMPTION A10]** Warehouse and factory figures are invented. What a factory's "units" mean (stock or output per turn) is undecided. Mechanics come later (Q03).
- **[ASSUMPTION A11]** Market label mapping: easy → high supply, fair → moderate supply, hard → moderate demand, bad → high demand. Unconfirmed.
- **[ASSUMPTION A12]** A jumping ship arrives in **Space**. Movement between Port, Space and Planet is undecided.
- **[ASSUMPTION A13]** The trading order is set at the start of Trading. The brief's glossary says "during Bidding", but initiative is only known after Bidding closes. Unconfirmed.
- **[ASSUMPTION A14]** One Trading screen shows the current resolution and the queue.
- **[ASSUMPTION A15]** "Pinged" is an in-game alert only.
- **[ASSUMPTION A16]** What happens if a winner doesn't decide in time isn't simulated, because no rule exists.
- **[ASSUMPTION A17]** Other players' simulated choices are fixed: Vega Lines takes the last jump position, the others take the earliest free one, and their jumps are fixed fixtures.
- **[ASSUMPTION A19]** Positions are picked one at a time, so a player picks only after everyone above them in initiative has picked. The picking step itself has no timer.

## Screen and state map

| From | Action | Expected next state | Rules / assumptions |
|---|---|---|---|
| Deck | Select a system on the map, a fleet location, or an offer's system | System details, with the heading focused | R03.3 |
| System | Fold or unfold a section | The section toggles (native `<details>`) | creator |
| System (Bidding) | Offer with an empty or out-of-range price | Inline error, focus moves to the price field | 1–20 bound |
| System (Bidding) | Valid offer | Listed on the system screen and the Deck; that good leaves the form | R17, one offer per good |
| System (not Bidding) | Open it | No offer form | creator |
| Deck (Bidding) | Invalid bid, then a valid bid | Error with focus, then the bid is shown | R21 |
| Test: phase → Ship-jumps | — | Players above you in initiative pick positions (logged); you're asked "Choose when you jump" with the free positions | creator, A17, A19 |
| Deck (your pick) | Choose a position → Confirm | The remaining players pick; the full jump order is logged | creator |
| Deck (not your slot) | "Other players act" | That player's jumps are logged and the map updates; the next slot starts | creator, A17 |
| Deck (your slot) | Ship + destination → Jump | That ship moves (map, fleet, log ✓); the next decision is offered with the remaining ships | creator, R19, A12 |
| Deck (your slot) | Destination equals the ship's current system | Inline error | — |
| Deck (your slot) | "Finish jumping", or jumping the last ship | Your slot ends; the next player starts | creator |
| Test: phase → Trading | — | Everyone is taken to Trading | creator, A14 |
| Trading (you first in initiative) | Drag or use ↑/↓ → Confirm order | Order logged; resolution 1 starts | creator, R18, A13 |
| Trading (another player first) | "Other players act" | Vega Lines confirms the order | R18 |
| Trading (another winner) | "Other players act" | "X sold/bought n u paying p CR each; price moves a → b", logged | creator, R13 |
| Trading (you win) | Alert on other screens → amount 1..max → Confirm | Result shown and logged → "Next resolution" | creator, R13, A15 |
| Any | Unknown `#fragment` | "This screen doesn't exist", with a link to the Deck | — |
| Any | Reset | Bidding, no offers or bid, ships back where they started, Vega Lines first in initiative | — |

## Verification

- **Static review: passed.**
  - The JavaScript parses (`node --check`).
  - No external files, network calls, storage or cookies; forms never submit. The only URL string is the SVG namespace.
  - User input is written into the page as plain text only (`textContent`).
  - Headings, labelled controls, visible focus and a live status region are in place.
  - The drag-and-drop list has a keyboard alternative.
- **Browser walk: not run.** The configured provider `agent-browser` isn't installed, and this skill doesn't install software. Headless-Chrome preview screenshots were taken outside this revision for the creator. They are not a browser walk; no interaction was exercised.
- **Checks not run:** every interaction in the state map, dragging with a mouse, keyboard traversal and focus, browser back and forward, the narrow viewport, and Reset.
