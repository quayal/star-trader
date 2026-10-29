# Prototype: dashboard with map, and system details (revision 001)

- **Brief:** `.ai/specs/product-brief.md`. Visual references: `.ai/specs/reference.md`. Read-only context: `.uxproof/`.
- **Flow:** the player's in-turn overview. From the combined dashboard/map, the player understands the turn and opens a system's details. Brief Key flows steps 6, 8 and 9, seen from the screen.
- **Date:** 2026-10-28. No previous revision. No panel report was supplied; the creator chose to skip synthetic users for this project.
- **Question this prototype answers:** how much information fits on the dashboard before it clogs. Compare the Minimal, Standard and Dense levels.

## How to open

Open `index.html` directly in a browser. It needs no server. Use the grey "Test state" bar to switch density, phase and conditions. **Reset** restores the start. All data is fictitious. Nothing here is research evidence or a sign that the design is production-ready.

## Simulation boundary

- **Actor:** one player, "Orion Freight", in a 4-player game during turn 4.
- **Start:** Deck (dashboard and map) during Bidding at Standard density.
- **Success:** the player can see their phase, money, fleet and offers, then open a system and place an offer that appears back on the dashboard.
- **Screens and states included:** the Deck in three densities and three phases, system details, loading, failed refresh with retry, "not a player in this game", and an unknown link.
- **Excluded destinations:** Fleet and Offers screens, sign-in, game creation and joining, the Industry screen, and resolving a trade. Fleet and Offers appear in the rail as "not in this prototype".

## Rules and decisions kept

| ID | Meaning kept | Source / owner |
|---|---|---|
| R17 | Your offers are visible only to you until Trading. No other player's offers are ever listed. | brief, creator |
| A03 | No "all offers" view during Bidding | reference.md, creator |
| R18 | Trading results are public. A results list is shown at Dense density. | brief, creator |
| R19, R20 | Jumps are instant, any system can be a destination, they happen in initiative order, and the destination is chosen openly | brief, creator |
| R21 | Initiative bid of 0 or more, capped by your money. Every bidder pays. | brief, creator |
| R22 | 3 ships per player | brief, creator |
| R23 | Trading needs your ship at the system. The system screen warns when you have none there. | brief, creator |
| R03.3 | Map is a graph of 6 systems and 15 edges, shows ships including other players', and a click opens the system | reference.md, creator |
| R03.5 | S/D always shows its number. The band comes from buy chance and is stepped (creator, 2026-10-28). | reference.md, creator |
| N03, N07 | No animation. No fuel, hull, ETA or ship purchasing. | brief, creator |
| **New: money is private** | Other players' money is hidden ("private"). This is a house rule that departs from the original board game. The engine enforces it. | **creator, 2026-10-28, in this session. Not yet in the brief.** Change path: creator approval, so add it to the brief as a business rule. Note: public trade results (R18) let attentive players estimate others' money. |
| Dashboard and map are one screen | The map sits in the middle, with overview panels around it | creator, 2026-10-28 (`.uxproof/conventions.md` manual section) |

## Assumptions

- **[ASSUMPTION A1]** Starting money (1,240 CR) and ship capacity (3,000 u) are placeholders. These numbers are open (Q06). Check: the creator answers Q06.
- **[ASSUMPTION A2]** Ship, player and corporation names are invented. A corporation name is collected at sign-in (Key flow 1). Check: none needed.
- **[ASSUMPTION A3]** Market tier per good (Retailer/Wholesaler) is shown on the system screen, but how a tier is earned is open (Q04). The values are invented. Check: the creator answers Q04.
- **[ASSUMPTION A4]** Systems on the map show nothing beyond ships, except the market lines at Dense density. Other numbers are "to be provided later" (Gap 4). Check: the creator decides after comparing the densities.
- **[ASSUMPTION A5]** The phase timer is a static "0:45 left". Its length (D04) isn't specified. Check: phase-engine spec.
- **[ASSUMPTION A6]** "I'm done bidding" is a readiness flag, and offers can still be added after it (R17). The button label is a proposal.
- **[ASSUMPTION A7]** During Ship-jumps the player picks one ship per turn (Key flow 8: "one of their ships"). Whether each player jumps once per turn or once per ship isn't explicit. Check: ask the creator.
- **[ASSUMPTION A8]** Trading progress ("resolving 2 of 7") and per-offer outcome text are invented to show where results would appear.
- **[ASSUMPTION A9]** The "not a player in this game" state mirrors the E02-S04 membership check (403). The wording is a proposal.

## Density levels (the comparison)

| Level | Deck shows |
|---|---|
| Minimal | Top bar (turn, phase and timer, money). The map with ship counts per system. The current phase's action under the map. |
| Standard | Minimal, plus initiative holder, your fleet (ship, location, cargo), your offers, and ship markers by name or owner on the map |
| Dense | Standard, plus a players panel (ships per player, money private), trading results, and every good's price and S/D band under each system on the map |

## Screen and state map

| From | Action | Expected next state | Rules / assumptions |
|---|---|---|---|
| Deck | Select a system on the map, or a ship's location in the fleet panel | System details for that system, heading focused | R03.3 |
| System details | "← Back to Deck" or browser Back | Deck | — |
| System (Bidding) | Place offer with an empty or out-of-range price | Inline error, focus on the price field | R15 bounds 1–20 |
| System (Bidding) | Place offer with a valid price | Offer is listed under "Your offers here" and on the Deck, with a status message | R17 |
| System (no ship of yours) | Open it | Notice that bought goods would be lost | R23 |
| System (not Bidding) | Open it | Form replaced by "Offers can only be placed during Bidding" | R17 |
| Deck (Bidding) | Initiative bid that is empty, negative or above your money | Inline error, focus on the field | R21 |
| Deck (Bidding) | Valid bid | Bid is shown in the offers panel | R21 |
| Deck (Bidding) | "I'm done bidding" | Button shows "Done ✓". Offers can still be added. | R17, A6 |
| Deck (Ship-jumps) | Jump a ship to the system it's already at | Inline error | A7 |
| Deck (Ship-jumps) | Valid jump | Ship moves on the map and in the fleet panel. The order advances. | R19, R20 |
| Test state | Simulate loading | "Loading game…" briefly, then back | — |
| Test state | Simulate failed refresh → Retry now | Notice on the Deck, then cleared | D01 |
| Test state | Simulate not in this game → Back | Denied screen, then back to the Deck | A9 |
| Any | Unknown `#fragment` | "This screen doesn't exist", with a link to the Deck | — |
| Any | Reset | Start state: Bidding, Standard, no offers, no bid, ships home | — |

## Verification

- **Static review: passed.**
  - JavaScript parses (`node --check`).
  - The page loads no external files, uses no network calls, storage or cookies, and forms never submit. The only URL in the code is the SVG namespace string.
  - User input is written into the page as plain text only (via `textContent`), never as HTML.
  - The page has headings, labelled controls, a visible focus outline and an announcement area for status messages.
  - Below 900 px the layout collapses to one column.
- **Browser walk: not run.** The configured provider `agent-browser` isn't installed, and this skill doesn't install software. Headless-Chrome preview screenshots were taken outside this revision for the creator to look at. They are not a browser walk, and no interaction was exercised in a browser.
- **Checks not run:** every interaction in the state map above, keyboard traversal, focus moving after navigation, browser back and forward, the narrow viewport, and Reset.
