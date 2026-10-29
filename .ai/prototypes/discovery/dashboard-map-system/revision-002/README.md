# Prototype: Deck with map, system details and Trading (revision 002)

- **Brief:** `.ai/specs/product-brief.md`. Visual references: `.ai/specs/reference.md`. Read-only context: `.uxproof/`.
- **Previous revision:** [`../revision-001/`](../revision-001/README.md). Its files are unchanged; the SHA-256 checksums match its manifest.
- **Flow:** the player's turn as seen on screen: Bidding on the Deck and system screens, Ship-jumps on the Deck, and Trading on its own screen (brief Key flows 6–9).
- **Date:** 2026-10-28. No panel report (the creator skipped synthetic users for this project).

## How to open

Open `index.html` in a browser. It needs no server. Use the grey "Test state" bar to switch phase, let other players act, take initiative yourself, and trigger the loading, failed-refresh and not-in-this-game states. **Reset** returns to the start. All data is fictitious, so the prototype is not research evidence and not a sign of production readiness.

## What changed from revision 001 (creator feedback, 2026-10-28)

| Change | Creator's words / reason |
|---|---|
| Standard density only; the density switch is removed | "standard density" |
| Unavailable actions are hidden, not explained (e.g. no offer form outside Bidding, no jump form when it's not your slot) | "When action isn't available, it shouldn't be visible" |
| New Trading screen; everyone is taken there when Trading starts. Each resolution shows: roll → "Highest bidder (X) can buy up to N u, waiting for their decision" → "X bought n u paying p CR each; price moves a → b". The winner gets an alert on any screen. | creator's description of resolution |
| The initiative holder sets the trading order by drag and drop, with ↑/↓ buttons as the keyboard alternative. Other players see "waiting for X to set the order". | "drag/drop list so that player with initiative can rearrange" |
| Buy-chance % removed; S/D shows its number plus a market label (high supply … high demand) | "Buy chance percentage is useless" |
| All your ships can jump in one turn: one form, a destination or "Stay" for each ship | "you can make as many jumps as you have ships", "All ships can jump in one turn" |
| Offers for all goods, but **one offer per good per system**. A good you've already offered disappears from that system's form. | creator; the rule will be specced later (it closes a market-position exploit) |
| Game log of public info: initiative, jumps, resolved trades, trading order | "last turns jumps and resolved trades should be seen in log; together with all other public info" |
| System screen in foldable sections: Locations and ships (Port: class and legal system; Space: police; Planet: security, as plain numbers), Market, Market positions (who is Dictator or Wholesaler per good), Warehouses and factories | creator; mechanics come later but the information must be there |
| Initiative isn't shown during Bidding, because it's decided when Bidding closes | Key flow 7 (consistency fix) |

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
| **Jumps: all ships per turn** | Each player may jump every ship in their slot | **creator, 2026-10-28, not yet in the brief.** Clarifies Key flow 8 ("one of their ships"); add it to the brief. |
| **One offer per good per system** | Enforced in the offer form | **creator, 2026-10-28, not yet in the brief.** The creator will spec it. |
| **Initiative holder reorders the trading queue by drag and drop** | Shown as the first Trading step | **creator, 2026-10-28.** UI for R18. |
| **System locations: Port, Space, Planet** with stats (class, legal system, police, security) | Ships are shown in one of the three; numbers only, no mechanics yet | **creator, 2026-10-28, not yet in the brief** |

## Assumptions

- **[ASSUMPTION A1]** Starting money (1,240 CR) and ship capacity (3,000 u) are placeholders. Q06 is open.
- **[ASSUMPTION A2]** Ship, player and corporation names are invented.
- **[ASSUMPTION A3]** Market positions (who is Dictator or Wholesaler where) are invented, and market positions are assumed to be public. How a tier is earned is open (Q04).
- **[ASSUMPTION A5]** The phase timer is a static "0:45 left". Its length (D04) isn't set.
- **[ASSUMPTION A6]** The label "I'm done bidding" is a proposal. It acts as a readiness flag (R17).
- **[ASSUMPTION A8]** Trading numbers are illustrative: rolls, maximum amounts, and the price move after your decision. The price move is **not** R14's formula.
- **[ASSUMPTION A9]** The "not a player in this game" wording mirrors E02-S04's 403 response.
- **[ASSUMPTION A10]** Warehouse and factory entries are invented: capacity, recipe and owner. Mechanics are later (Q03, N07-adjacent).
- **[ASSUMPTION A11]** Market label wording maps the stepped bands by how easily a buy wins: easy → high supply, fair → moderate supply, hard → moderate demand, bad → high demand. The creator asked for "high supply / moderate demand"-style labels; the exact mapping is unconfirmed.
- **[ASSUMPTION A12]** A jumping ship arrives in **Space**. How ships move between Port, Space and Planet is undecided.
- **[ASSUMPTION A13]** The initiative holder sets the trading order at the start of Trading. The brief's glossary says the order is set "during Bidding", but initiative is only known after Bidding closes (Key flow 7). The creator should confirm when ordering happens.
- **[ASSUMPTION A14]** Trading takes the form of one Trading screen with the current resolution and the queue, rather than jumping between system screens.
- **[ASSUMPTION A15]** "Pinged" is shown as an in-game alert only. Email or browser notifications are undecided.
- **[ASSUMPTION A16]** Not simulated: what happens if the winner doesn't decide in time (no rule exists). The ship-jump order is set by the initiative holder (R20) but isn't editable here; only the trading order is.

## Screen and state map

| From | Action | Expected next state | Rules / assumptions |
|---|---|---|---|
| Deck | Select a system on the map, a fleet location, or an offer's system | System details, heading focused | R03.3 |
| System | Fold or unfold a section | The section toggles; native `<details>` is keyboard operable | creator |
| System (Bidding) | Offer with an empty or out-of-range price | Inline error, focus on price | 1–20 bound |
| System (Bidding) | Valid offer | Listed under "Your offers here" and on the Deck; that good leaves the form | R17, one-per-good |
| System (Bidding) | Offer on every good | The form is replaced by "You have an offer on every good here this turn" | one-per-good |
| System (not Bidding) | Open it | No offer form | creator: hide unavailable actions |
| Deck (Bidding) | Invalid initiative bid, then a valid one | Inline error with focus; then the bid is shown in the offers panel | R21 |
| Deck (Bidding) | "I'm done bidding" | Replaced by "You signalled done — you can still add offers" | R17, A6 |
| Deck (Ship-jumps) | Not your slot | No jump form; the top bar names who is choosing; "Other players act" advances | R20 |
| Deck (Ship-jumps, your slot) | Pick a destination or Stay per ship → Confirm jumps | Ships move on the map and fleet; logged; the order advances | creator, R19, A12 |
| Test: phase → Trading | — | Everyone is taken to the Trading screen | creator, A14 |
| Trading (you hold initiative) | Drag rows or use ↑/↓ → Confirm order | Order logged; resolution 1 starts | creator, R18, A13 |
| Trading (someone else holds initiative) | "Other players act" | Vega Lines confirms the order; resolution 1 starts | R18 |
| Trading (another winner) | "Other players act" | "X sold/bought n u paying p CR each; price moves a → b", logged | creator, R13 |
| Trading (you win) | Alert on other screens → amount 1..max → Confirm | Result shown and logged; "Next resolution" | creator, R13, A15 |
| Trading | Amount 0 or above max | Inline error, focus | R13 |
| Trading | Last resolution done → Next | "All trades resolved" | — |
| Any | Unknown `#fragment` | "This screen doesn't exist", with a link to the Deck | — |
| Any | Reset | Bidding, no offers, no bid, ships home, initiative to Vega Lines, Deck | — |

## Verification

- **Static review: passed.**
  - The JavaScript parses (`node --check`).
  - No external files, network calls, storage or cookies; forms never submit. The only URL string is the SVG namespace.
  - User input is written into the page as plain text only (`textContent`).
  - Headings, labelled controls, visible focus and a live status region are present.
  - The drag-and-drop list has ↑/↓ buttons as a non-drag alternative.
  - The layout drops to one column below 900 px.
- **Browser walk: not run.** The configured provider `agent-browser` isn't installed, and this skill doesn't install software. Headless-Chrome preview screenshots were taken outside this revision for the creator. They aren't a browser walk, and no interaction was exercised.
- **Checks not run:**
  - every interaction in the state map;
  - dragging with a mouse;
  - keyboard traversal and focus after navigation;
  - browser back and forward;
  - the narrow viewport;
  - Reset.
