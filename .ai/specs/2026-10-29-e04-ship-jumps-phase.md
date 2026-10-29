# E04 — Ship-jump phase (#10)

**Prototype:** `.ai/prototypes/discovery/dashboard-map-system/revision-013/` (accepted as the first version, D07). Use the "Test state" bar → phase **Ship-jumps**.
**Brief:** `.ai/specs/product-brief.md` — R19, R23, R26, R27, R30, R31, D07.
**Siblings:** E03 Bidding (`2026-10-29-e03-bidding-phase.md` — screen IDs are defined there), E05 Trading (`2026-10-29-e05-trading-phase.md`).

## 📝 TLDR

After Bidding closes, players pick their place in this turn's turn order, one at a time in initiative order; the first player may pick any place, including last. Then each player, in turn order, jumps their ships one at a time to any system. After each jump they choose where the ship ends up: Port, Planet or Space. Everything here is public and shows up on the map, the fleet, s02 and the Game log as it happens. Ship positions at the end of this phase decide where each player can trade in E05 (R23).

## 📝 Screens used

s01 Deck (Turn order under the fleet on the left; Your next jump on the right), s02 System details (Locations and ships), s04 Game log, s05 Landing dialog. IDs are defined in the E03 spec.

## 📋 Stories and acceptance criteria

### E04-S01 — Players pick their place in the turn order

1. [—] [R26] When Bidding closes, the phase becomes Ship-jumps and picking starts. Players pick one at a time in initiative order (R27).
2. [s01 top bar] While someone else picks: "Ship-jumps — {corp} is choosing their place in the turn order". While you pick: "— choose your place in the turn order".
3. [s01, left column] [D07, proto-A27] "Turn order" lists the places 1st…Nth with who holds each, "—" for free ones. Note: "Chosen in initiative order; applies to the whole turn."
4. [s01] On your pick: radio buttons for free places only, the first free one pre-selected, **Confirm position**. Afterwards the status reads "You act {nth} this turn."
5. [s04] [R26] Each pick is logged ("{corp} chose 2nd"), then the full order: "Turn order (for this turn): A → B → C → D".
6. [—] The last player to pick gets the single remaining place without being asked (proposed, OQ-3).

### E04-S02 — Players jump their ships in turn order

1. [s01 top bar] Once the order is set: "— {corp} is jumping" / "— your jumps" / "— all jumps done". Turn order shows "jumping now" and "done" per slot.
2. [s01, right column] [D07, proto-A27] During your slot, "Your next jump": ship (only ships that haven't jumped this turn, shown with their current system), destination (any of the six systems), **Jump** and **Finish jumping**.
3. [s01] [R26] Every ship may jump at most once per turn. Jumps are separate decisions: the next one is made after the previous one has resolved.
4. [s01] Picking the ship's current system as destination → inline error "{ship} is already at {system}. Pick another destination." with focus on destination (OQ-4).
5. [—] [R19] Every jump succeeds instantly: no fuel, damage, transit time.
6. [s01] Jumping your last ship, or pressing **Finish jumping**, ends your slot and passes to the next player. Ships you didn't jump stay where they are.
7. [s01, s02, s04] [R26] Other players' jumps appear as they happen: map markers, s02's ship lists and the log ("Jump: {corp} — {ship} → {system}").
8. [—] After the last slot, the phase moves to Trading (E05).

### E04-S03 — After each jump the player chooses where the ship ends up

1. [s05] [R30] After a jump resolves, a modal dialog opens with focus inside: "{ship} arrived at {system}", "Where do you want to be?" with Land at the port (class, legal system), Land on the planet (security), Stay in space (police) — stats from R31. **Confirm**.
2. [s05] [proto-A21] Port is pre-selected. Escape counts as "Stay in space".
3. [—] [R30] A ship is always in exactly one location. The choice is logged ("Landing: {corp} — {ship} lands at the port ({system})") and shown in Your fleet and on s02.
4. [s01] After confirming, focus returns to "Your next jump" (or the slot ends if that was your last ship).
5. [—] [R31] Locations have no mechanics in v1: the choice changes where the ship is shown, nothing else.

### E04-S04 — Other players see the phase progress

1. [s01–s04] [D01] Picks, jumps and landings by others show up within the polling interval, without a refresh.
2. [s01] [proto-A27] Offers placed during Bidding stay visible to their owner in the right column, under Your next jump.

## 📝 States checklist (Ship-jumps)

| State | Where | Covered by | In prototype? |
|---|---|---|---|
| Waiting for others to pick | s01 | S01-2 | yes |
| Your pick | s01 | S01-4 | yes |
| Waiting for another player's jumps | s01 | S02-1, S02-7 | yes |
| Your slot | s01 | S02-2 | yes |
| Validation error (same system) | s01 | S02-4 | yes |
| Landing dialog | s05 | S03 | yes |
| All ships jumped / finished | s01 | S02-6 | yes |
| Player whose pick or slot it is doesn't act | s01 | — | **no — OQ-1** |
| Player disconnects mid-slot | s01 | — | **no — OQ-1** |
| Landing dialog closed by a lost connection | s05 | — | **no — OQ-2** |
| Poll failed / stale | s01 | E03-S02-12 | yes |

## 📝 Decisions in play

R19 (instant jumps), R23 (positions decide where you can trade), R26 (turn-order pick; per-ship jumps), R27 (initiative ordering), R30 (landing choice), R31 (location stats, no mechanics), D07 (layout per phase, proto-A27).

## ❓ Open questions — gaps the prototype didn't decide

| # | Question | Blocking? | Proposed default |
|---|---|---|---|
| OQ-1 | D04's timer covers simultaneous phases. What happens when the player whose **pick** or **jump slot** it is doesn't act? Without a rule, one absent player stalls the game in every turn, not just after D06's repeated absence. | **yes** | a per-slot timer (60 s?); on expiry the pick takes the first free place and the slot finishes with no further jumps |
| OQ-2 | If the landing choice is never confirmed (tab closed), where is the ship? | no | Space — same as Escape (proto-A21) |
| OQ-3 | Is the last picker asked, or given the remaining place automatically? | no | given automatically |
| OQ-4 | Can a ship move between Port, Planet and Space inside its current system without jumping? | no | no in v1 (matches the same-system error) |
| OQ-5 | Is there any reason to see another player's ship cargo? | no | no — cargo is private like money (R29), only position is public |

## 🚫 Out of scope

Fuel, damage, transit time, buying ships (N07); location mechanics (R31); timers beyond OQ-1's decision.

## 📋 Phasing and dependencies

- Depends on E03-S01 (phase clock) and E03-S05 (initiative order).
- Order: S01 → S02 → S03 → S04. S03 can ship with S02 if the dialog is small.

## 📝 Architecture notes

- Turn order and per-ship "jumped this turn" belong to the turn state; reset at the next Bidding.
- Ordered steps are server-gated: an action from a player out of turn is rejected (R04).

## Screens without a criterion in this spec

s03, s06, s07, s08 (covered by E03 and E05).
