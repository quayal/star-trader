# Jumps screen and the phase rail

**Brief:** `.ai/specs/product-brief.md`: R19/R20 (jumps), R23 (trading needs a ship present), R26 (turn order), R30
(landing choice), R31 (location statistics), R44 (the phases), R57 (a ship's stats are private), R68 (jump chance), R70
(interception), R108 (Novak), R112 (where illegal goods sell), R114/R121 (deliveries), R115 (events), R116–R118 (Planet
landing), R125 (passengers), R127 (Parking Hangars), R128 (police in Control), R131 (Port ban), D15 (a phase change opens
its screen).
**Source:** the creator's request, 2026-11-26: during Jumps a player needs to know what each of their ships owes and
what waits at each destination. Passengers are the first example: miss the destination Port and you pay a penalty. The
creator also asked for a mechanism, possibly hidden tabs, that gives each phase its own screen.

## 📝 TLDR

Jumps gets its own screen, like Trading and Opportunity already have. It answers two questions for every ship the player
can still jump: **what does this ship owe, and where?** and **what waits for it at each destination?** A destination
table puts every system side by side: the chance of the jump; who is in Space there; the Planet's odds for this ship;
the Port's status; police; and the player's own business there (bids, passengers, a delivery, goods that sell there).
The player picks the ship and the destination on the same screen.

The Deck stays the overview (map, prices). A **phase rail** in the top bar shows every phase of the turn and which one
is running, so the sidebar doesn't need one item per phase. It's built for Jumps now and is ready for the other phases.

## 🧭 Navigation: the phase rail (D34, confirmed by the creator 2026-11-26)

The creator chose the rail and confirmed that Control needs no screen: it stays a label on the rail. Bidding gets its
own screen in `2026-11-26-bidding-market-screen.md`, which moves the rail's Bidding link off the Deck.

The sidebar today mixes two kinds of tab. **Reference tabs** are useful at any time: Deck, Fleet, Assets, News,
Corporations, Guide, Log. **Phase workspaces** are where the player acts: Trading, Opportunity, Infrastructure for
Investment. Bidding, Initiative and Jumps are panels on the Deck. A phase change opens that phase's screen (D15).

Options considered:

| Option | For | Against |
| --- | --- | --- |
| One sidebar item per phase, always visible | Easy to find | 8 phase items plus 7 reference items; most of them have nothing to do most of the time; the sidebar stops telling the player what matters now |
| Sidebar shows only the current phase's item (the "hidden tab") | Short sidebar | The item jumps around and disappears with the phase; no way back to the Trading results once Opportunity starts |
| **Phase rail in the top bar + the current phase's screen (recommended)** | Shows where the turn is (done, now, next) at a glance, the pattern turn-based strategy games and multi-step forms use (a stepper); the sidebar keeps only stable reference tabs | One more row in the top bar; the phase screens need building one by one |

**Recommendation:** the phase rail. It's a row of the turn's phases in R44 order, in the top bar on every game screen:
`Bidding · Initiative · News · Jumps · Trading · Opportunity · Investment · Control`.

- The running phase is highlighted. Phases already played this turn have a tick, and later phases are muted.
- Each phase that has a screen is a link to it. A past phase's screen is read-only and shows its results, so Trading's
  trades can still be read during Opportunity.
- A phase change still opens its screen (D15). The rail is how the player gets back to it after visiting Fleet or the
  log.
- Over time the phase items (Trading, Opportunity) leave the sidebar. The sidebar keeps the reference tabs.
- The "I'm done" button stays at the right of the top bar, as now.

This spec builds the rail and the Jumps screen. Bidding and Initiative keep their Deck panels and their rail items link
to the Deck until they get screens of their own (Out of scope). Trading, Opportunity and Investment get rail links at once,
and their sidebar items go (criterion 12).

## 📋 Acceptance criteria

### The Jumps screen

1. **Route.** `#game/{id}/jumps`. A change into the Jumps phase opens it instead of the Deck (`screenForPhaseChange`
   gains `JUMPS: 'jumps'`). Focus goes to its heading. The jump form and its messages move off the Deck. The turn-order
   panel stays on the Deck for Initiative and appears on the Jumps screen too (criterion 7).
2. **Ship picker.** One card per ship of the viewer. Ships that can still jump this turn come first, the rest go last
   and can't be chosen. Each card says why, e.g. `Jumped to Tau Ceti`, `In the yard until turn 7`, `Jump failed`, `On an
   expedition`. A ship's card shows:
   - where it is (system and location) and whether it's parked;
   - cargo used / spaces, with each good;
   - free passenger seats / seats;
   - damaged modules, and the hull's damage if any;
   - agents aboard by name (the viewer's own, so not secret to them);
   - its **obligations** (criterion 3), each with its destination.

   The first ship that can jump and has an obligation is selected when the screen opens; if none has one, the first
   ship that can jump.
3. **Obligations of a ship** (only the viewer's, private):
   - **Passengers.** `2 groups → Tau Ceti Port, by the next Opportunity · 20 HT penalty if missed`. The penalty is
     groups × 10 HT (R125). A booking made this turn falls due at the next Opportunity, so in Jumps this is the last
     chance. Highlight it as **due this turn**.
   - **Delivery job.** `Delivery → Beta Hydri Port` (R114). No deadline, so it isn't highlighted.
   - **Goods that sell in one place.** `4 drugs sell on Sigma Draconis Planet`, `3 furs sell at Beta Hydri Port`
     (R112), from `specialLots`.
   - **Contraband aboard.** A ship with illegal goods or a military part gets the note `Carries contraband: a forced
     landing confiscates it`, because it changes what a Planet landing risks (R117).
4. **The player's business by system.** A box over the table lists the viewer's offers this turn by system, e.g.
   `Tau Ceti: buy Food at 7, sell Ore at 9`, with what each needs. A buy needs free cargo space aboard a ship there. A
   sell needs the goods aboard a ship there. A system with an offer and **no ship of the viewer there or heading there**
   is flagged: `No ship at Tau Ceti: goods bought there are lost (R23)`. "Heading there" counts ships already jumped
   there this turn. The flag goes away once one is.
5. **Destination table.** One row per system for the selected ship, the ship's own system included (R69: moving within
   the system, always succeeds). Columns:
   - **Chance.** The ship's `jumpChances` percent (it already includes crew, Safe jump, Novak and this turn's E1, R68,
     R108, R115); `100%, no roll` for its own system.
   - **In Space.** Every other player's ship in Space there, by corporation and name (public, R70): these may intercept.
     Ships of players still to jump this turn are a risk too, so the row says `2 ships · 1 player still to jump`. The
     viewer's own ships in Space are listed separately (no risk).
   - **Planet.** This ship's landing odds there, `lands 72% · forced 14% · seized 14%`, with this turn's security change
     (R116–R118). Server-computed (criterion 9).
   - **Port.** One of `Open`, `Closed this turn` (E10), `Refused: military parts` (R131); `Hangar 5 HT` where one exists
     (R127); `Shipyard` where repairs and refits happen.
   - **Space.** The police chance of being forced into Port in Control if the ship stays in Space (R128), this turn's
     change included. Server-computed.
   - **Your business.** Icons with text for the row's system: passengers due, delivery, goods selling there, offers
     there (criterion 4).

   Rows where the selected ship has an obligation show it first in the Your business cell and are marked. The table
   is a real `<table>` with a header row; on a phone it becomes one card per row (the design system's stacked-table
   pattern).
6. **Choosing.** Clicking a row's `Jump here` (one button per row, visible on focus and hover, always on touch) selects
   that destination. The confirmation shows ship, destination, chance and the ship's obligations that this jump leaves
   undone, e.g. `Wayfarer jumps to Sigma Draconis (58%). 2 passenger groups for Tau Ceti stay aboard: 20 HT penalty if
   no other way gets them there.` Then the player confirms. After the jump the landing dialog opens as now (R30), and a
   failed jump shows the message as now (R68 criterion 8). Nothing about the jump rules changes.
7. **Turn order.** A compact strip shows the Jumps order: done, now jumping, still to jump. It also shows which other
   players' ships have jumped where this turn. These are public: the log already says them.
8. **Waiting.** When it isn't the viewer's slot, the screen stays readable and the `Jump here` buttons are disabled with
   `Waiting for Vega Lines`. The player can still pick a ship and study rows.

### Server

9. **Per-destination odds for own ships.** Each `you.ships[]` gains `destinations`: for every system, `landing`
   (`LandingOddsResponseDto` against that system's Planet security this turn, with the ship's bonus), `policePercent`
   (the chance a ship in Space there is forced into Port in Control, R128, this turn's change and a working Battle comm
   included), and `portRefused` (R131 for that Port). Only the owner gets it (R57). The existing `landing` and
   `portRefused` stay for the ship's own system.
10. **Rules stay where they are.** The landing roll's odds come from `LandingOdds`. The police roll's odds come from the
    class that owns the Control police roll. The Port ban comes from `Ship.mayEnterPort` with a destination parameter.
    No percentage is computed in JavaScript.

### Phase rail

11. **The rail.** On every screen of a running game, under the title: the R44 phases in order. It shows done (tick),
    now (highlighted, `aria-current="step"`) and later (muted), as an ordered list. Each phase with a screen is a link:
    Bidding, Initiative → Deck; News → News; Jumps → Jumps; Trading → Trading; Opportunity → Opportunity; Investment →
    Infrastructure. Control has no screen and is a label. Tokens only, no new colors.
12. **The sidebar** loses Trading and Opportunity, which are now on the rail. It keeps Deck, Infrastructure (for
    warehouses and factories outside Investment), Fleet, Assets, News, Corporations, Guide, Game log, Lobby. A past
    phase's screen is reachable from the rail and is read-only.

### Tests

13. JS unit tests for the obligations list, the offers-without-a-ship flag, the row ordering and the rail's states.
    Kotlin tests for `destinations` (a Planet with an event's security change, a military ship at a legal-3 Port, Battle
    comm against police). `GameControllerTest` covers the new fields reaching only their owner.

## Defaults I chose

- **Only real obligations are highlighted.** Passengers are the one obligation with a deadline that falls in this
  Jumps. A delivery has none, and goods for sale can wait, so they are listed but not alarmed.
- **Offers are shown as needs, not quantities.** An offer has no quantity until Trading (`tradeMax`), so the screen says
  what's needed (a ship there, free space, the goods aboard) rather than a number.
- **Other players' ships show where they are, not what they carry.** Their weapons and crew are private (R57). The row
  doesn't guess an interception chance.
- **Police odds show even though Control is later.** A ship that stays in Space after this jump meets them in this
  turn's Control.
- **Nothing about jumping changes.** Same rolls, same landing dialog, same log lines.

## Brief changes

- New decision D34: phase workspaces are reached through the phase rail; the sidebar holds reference tabs. D15 still
  holds: a phase change opens its screen, and Jumps' screen is now its own.
- New rule R137: during Jumps, the player sees each of their ships' obligations and, for every destination, the jump
  chance, ships in Space, this ship's Planet and police odds and Port status, before choosing.

## Out of scope

- Screens of their own for Bidding, Initiative and Control. Their rail items link to the Deck or are labels until then.
- Showing the map on the Jumps screen. The Deck keeps it, and the table replaces it for comparing destinations.
- Any estimate of another player's interception or weapons.
- Market prices at the destination. They are on the Deck and System details; a link per row goes there.
- Changes to jump, landing, interception or police rules.

## Implementation notes (2026-11-26)

- **Rule number.** R137 was taken before this spec merged (learned opportunities, PR 190), so this rule is **R139** in the
  brief and the code.
- **Placement** (engineering standards §13): the police odds are `PoliceOdds` in `game.control`, beside `PoliceRoll`
  (row 6); the Port ban at a destination is `Ship.mayEnterPort(at)` (row 4); each ship's destinations are
  `Player.destinationsOf(shipName, security, police)` returning `ShipDestination` (rows 5, 8), with `Game.viewFor`
  passing this turn's security and police per system, events included (row 7), and `PlayerView.destinations` keeping
  them the owner's (row 0).
- **Extra fields** the screen needed, so no rule sits in JS: `you.ships[].deliveryTo` (a delivery's destination) and
  `you.passengerGroups[].penalty` (R125's penalty from `PassengerGroup.penalty()`).
- **Presence** counts a warehouse as well as a ship for the "no ship at X" flag (R23, R49), so a warehouse doesn't raise
  a false alarm.
- **Row order:** rows where the selected ship owes something come first, then the board's order.
- A ship that has jumped says `Jumped this turn · now at X`: the client can't tell a failed jump from a move within the
  system once the turn moves on. When all of the viewer's ships have jumped, `Jump here` is disabled and the status line
  points to Finish jumping.
- The "Your move: jump" card and the decision alert now take the player to the Jumps screen.
