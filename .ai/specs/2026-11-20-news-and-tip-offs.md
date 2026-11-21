# News and tip-offs

**Brief:** `.ai/specs/product-brief.md`: R02 and R06 (confidential info), R26–R27 (initiative and turn order), R30–R31
(locations and their statistics), R34–R35 (price and S/D), R42 and R54 (cargo), R44 (turn phases), R51–R56 (hulls,
modules, military parts), R62 (ship names), R66 (ship value), R68 (jump chance), R81 (connections), R85–R86 (reputation),
R90–R95 (loans), R102 (Opportunity), R107–R108 (agents: deaths, Frank).
**Source:** the creator's vault note `10-projects/Trader/Intel.md` (the Opportunities, Events and Black market tables) and
their answers of 2026-11-20.
**Depends on:** `2026-11-20-opportunity-phase.md` (opportunities are taken there), `2026-11-20-smuggling-planet-landing.md` (landing on a
Planet past its security; police and Port legal systems are still unspecified), `2026-11-20-loans.md` (event E4), `2026-11-20-agents.md` (Frank).
**Status:** proposed, not started.

## 📝 TLDR

A new **News** phase comes after a new **Initiative** phase: Bidding → Initiative → **News** → Jumps → Trading →
Opportunity → Investment (→ Control, later). At the start of News, tip-offs that are due **take place** (events) or
**expire** (opportunities), and their details are announced to everyone. Then **d3 new tip-offs** are drawn from a pool of
40. Every player sees each tip-off's required connection (e.g. `CRIM 5`) and the turn it becomes effective, but not what it
is. A player whose connection is high enough may pay **5 HT** in News to learn it.
- **Events** (14) are intel: knowing that prices will fall or the ports will close lets a player prepare.
- **Opportunities** (26) are deals taken in the Opportunity phase, in turn order, by the **first taker only**. These include
  illegal goods (weapons, slaves, drugs) bought on a Planet and sold on another planet's **black market** at a d6 roll,
  military hulls and modules, delivery jobs, and a scientific expedition.

## 📝 Problem Statement

R02 and R06 have promised "secret opportunities, inside info" since the start. Connections (R81) have only one effect so
far (criminal → sabotage). Tip-offs give all three kinds a use, give military parts (R56) a way into the game, and
add the one force the market lacks: shocks that the well-connected see coming. A shared phase that everybody watches makes a
missed opportunity or an ignored warning visible, which is the point. The creator named it News because every tip-off ends
up announced to all.

## 📋 Acceptance criteria

### The turn
1. **Phase order** (creator, 2026-11-20, "the ultimate order from the game"): Bidding (initiative bids and offers) →
   **Initiative** → **News** → **Jumps** → Trading → Opportunity → Investment. A later **Control** phase will close the turn,
   but it's out of scope here.
   - **Initiative** is split out of Ship-jumps. It holds the initiative result (R27) and players picking their place in
     the turn order (R26), with nothing else changed. It closes into News once the last player has picked.
   - **Ship-jumps** is renamed **Jumps** everywhere (top bar, phase list, Game log). Only the name changes.
   - D15 routing: Initiative → the Deck (where picking happens today), News → the **News** screen (criterion 22).

### Wording (creator, 2026-11-20)
1a. A **tip-off** is only **information** about an opportunity or an event that's coming. It is never what a player takes.
    Players **learn** tip-offs, and they **take opportunities**. Events **take place**, and opportunities **expire**. Every
    screen and log line keeps to this. For example:
    - `Vega Lines took the opportunity to transport an envoy from Epsilon Eridani to Mu Herculis.`
    - `News: the opportunity to buy drugs at Beta Hydri (15 HT) has expired.`
    - never `took a tip-off`.

### Tip-offs and the pool
2. **The pool** is the creator's 40 tip-offs: opportunities O1–O26 and events E1–E14 (catalogue below). Each tip-off is in
   exactly one place: the **pool**, **in play** (drawn, not yet effective), or **retired** (a one-time event that has taken place).
3. **Each tip-off has** a **required connection**: one kind and a level, such as `CRIM 5`, `BUSS 10` or `POL 4`. It also has
   a **lead**, the number of turns from its draw to when it becomes effective, at least 1. Both come from the Connections
   and Turn columns of `Intel.md` (filled in by the creator on 2026-11-20). In the catalogues below they're the **Needs** and
   **Lead** columns. They're code data like the hull table (R51).
4. **Effective:** a tip-off drawn in turn D with lead n becomes effective at the **start of turn D+n's News**. An event then
   **takes place**, and an opportunity **expires**. An opportunity can be taken in the Opportunity phases of turns D to
   D+n−1.
5. **Back to the pool:** a tip-off returns to the pool the moment it becomes effective, whether its opportunity was taken or not. The
   exception is an event marked **one time only** (E2, E4, E7, E8, E9, E10, E14), which is retired for the rest of the game.
   A tip-off that just returned can be drawn again in the same News (creator, 2026-11-20). Taking an opportunity doesn't
   return it early.

### The News phase
6. **Opening News** runs these steps for everyone at once, in this order, all logged publicly:
   1. **Effective tip-offs** in the order they were drawn. For each one, its details are announced to every player and
      the effect applies, as listed in the catalogue:
      - an **event** takes place: `News: Epidemics. Prices up by 4 everywhere; Ports are closed this turn.`
      - an **opportunity** expires: `News: the opportunity to buy drugs at Beta Hydri (15 HT) for sale at Gamma Leporis has
        expired.`
        A taken **illegal** opportunity reads `… was taken`, without naming the taker. A taken legal one was already
        announced when it was taken (criterion 11).
   2. The **d3 roll** (public), then that many tip-offs are drawn from the pool at random. If the pool holds fewer, all of it
      is drawn.
7. **Then players act at the same time,** like Investment (no turn order, since learning doesn't compete). Each player may
   learn tip-offs (criterion 9), then signals **"Done with the news"**. When everyone is done, Jumps begins.
8. **What everyone sees** of a tip-off in play: its slot number on the board (`Tip-off #3`), the required connection, and the
   turn it becomes effective (`CRIM 5 · effective turn 7`). Not its kind (opportunity or event) and not its details. Who has
   learned it is private.

### Learning
9. **Learning** is allowed **only in News**, for any tip-off in play: drawn this turn or earlier and not yet effective. The
   player's connection of that kind must be **at or above the level now**. Connections are raised only in Investment
   (R83), so the level counts if it was reached by the previous turn at the latest. Each tip-off costs **5 HT**, private
   (R29), and is refused if the player can't pay or the connection is too low:
   `You need CRIM 5 to learn this (yours: 3).`
10. **Once learned,** the player sees the full details on the News screen for as long as the tip-off is in play, in every
    phase. Learning is never logged. A player who missed a News can learn an older tip-off in a later News while it's still
    in play.

### Taking opportunities (in the Opportunity phase)
11. **Who and when:** on their own Opportunity turn, after their Collect, a player who **has learned** an opportunity may
    take it. The first player in turn order to take it gets it. Any number of opportunities per turn. Refused if it's taken
    or not learned: `Vega Lines already took this opportunity.`
    - **Legal opportunities** (Furs O5, deliveries O8, O9 and O22, the expedition O15) are **announced to everyone when
      taken**, with the taker and the details (creator, 2026-11-20):
      `Vega Lines took the opportunity to transport an envoy from Epsilon Eridani to Mu Herculis.` The opportunity shows as
      taken to every player.
    - **Illegal opportunities** (illegal goods, military hulls and modules) stay **secret**. The opportunity shows as taken only
      to those who learned it, and the taker is never named. A military ship appearing on a Planet is still public, as any
      ship is (R57).
12. **Taking means at least buying or accepting it** before it expires (creator, 2026-11-20). What follows (selling the
    goods, delivering, the expedition's return) can happen in later turns, after the opportunity has expired.
13. **The kinds of opportunity:**
    - **Illegal goods** (weapons, slaves, drugs: O1–O3, O6, O7, O10–O12, O17–O19, O23, O25). The buyer needs a ship **on the
      Planet** of the buy system. Getting there is the smuggling and security spec's job. They buy **1 to 10 units** at the
      card's price per unit, paid at once, which takes the opportunity, all of it, even if fewer than 10 are bought.
      **Holding** (creator, 2026-11-20): only aboard ships, never in a warehouse.
      - **weapons** take 1 cargo space per unit, like POLY and ISO;
      - **drugs** take no cargo space, like CHIPS and FOOD;
      - **slaves** take 1 passenger seat each, from Passenger modules (2 seats each, R52).

      Units that don't fit can't be bought (unlike R16, since the seller hands over only what you can load). Each lot keeps
      its good and its **target planet**.
    - **Furs** (O5): bought the same way but in the **Port** of Mu Herculis, up to 10 units at 10 HT, taking no space. Sold for
      a **fixed 30 HT per unit** in the Port of Beta Hydri, on the owner's Opportunity turn, all of them at once.
    - **Military hull** (O13, O16, O20, O21, O24). No ship is needed. The buyer names the ship (R62), picks a crew class, and
      pays the hull's and modules' catalogue prices (R51–R52) plus crew (R67). The ship **appears on the Planet** of the
      card's system at once: it can't jump until next turn's Jumps, but it can take part in this Opportunity. The modules
      are the card's military modules within the hull's capacity. Civilian modules may fill the remaining capacity at
      catalogue price. "Any military hull" (O20) means Dagger, Sword or Spear.
    - **Military modules only** (O4, O14, O26, and O13's "or modules" branch). The buyer needs one of their ships **on the
      Planet** of the card's system, with free module capacity. They pay catalogue price plus crew at the ship's class, as in
      a refit (R65). Modules that don't fit can't be bought.
    - **Delivery jobs** (O8, O9, O22). The buyer accepts with a ship in the pickup **Port** that has the space the card asks
      for. That space is then occupied until delivery:
      - O8, the envoy: one whole Passenger module, and the player needs reputation 20+ when accepting;
      - O9, the plants: one whole Cargo module, 2 spaces of a Cargo module, not the hull's built-in space;
      - O22, the animals: one whole Passenger module.

      **Delivery** happens automatically when that ship is placed in the destination Port after a jump (R30). The payment,
      plus O9's +3 reputation, comes at once and is logged publicly: `Vega Lines delivered the envoy to Mu Herculis`. There's
      no deadline. If the ship is destroyed or sold, the job is lost, with no penalty. The occupied space can't be used for
      anything else, and Move goods (R58) can't move it.
    - **Scientific expedition** (O15): see criterion 15.
14. **Black market** (selling illegal goods). On the owner's Opportunity turn, with a ship holding a lot **on that lot's target
    planet**, the player sells: one **d6** roll per sale, +2 if Frank is on that planet (R108), capped at 6. The price per unit
    comes from the creator's table. **Every** unit of that good aboard the player's ships on that planet, from lots with that
    target, is sold at that price, however bad the roll (creator, 2026-11-20). Allowed in any later Opportunity, even after
    the opportunity has expired. Logged publicly without units or price: `Vega Lines sold on the black market at Gamma Leporis`.
    The roll and the takings are private (R29).

    | d6 | Slaves | Weapons | Drugs |
    |---|---|---|---|
    | 1 | 5 | 8 | 12 |
    | 2 | 8 | 10 | 15 |
    | 3 | 10 | 15 | 25 |
    | 4 | 15 | 25 | 35 |
    | 5 | 20 | 30 | 50 |
    | 6 | 25 | 40 | 70 |

15. **Scientific expedition** (O15). The taker sends **one legal ship** (no military parts) of theirs that isn't in the yard,
    from wherever it is.
    - It **leaves the board** with its cargo and agents aboard. It can't be traded with, attacked or sabotaged, and it
      isn't presence anywhere.
    - A d6 is rolled when it leaves, **publicly**, as part of the announcement (criterion 11). On a **1** the ship is lost
      at once: `The Starfarer expedition was lost`. Agents aboard are killed, as in Space (R107).
    - Otherwise, on a roll of r, it returns on the owner's Opportunity turn of turn T+r, to the **Port** of a system the
      owner chooses (creator, 2026-11-20), with no landing roll. If Ports are closed that turn (E10), it returns to that
      system's Space.
    - On return the owner is paid **2d6 × 50 HT** (100–600), private, and the log reads
      `The Starfarer expedition returned to Tau Ceti`. A loan seizure (R94) can't take a ship that's away.

### Opportunities catalogue (creator's table)

Systems: BH Beta Hydri, EE Epsilon Eridani, GL Gamma Leporis, MH Mu Herculis, SD Sigma Draconis, TC Tau Ceti. "Buy on X"
means on X's Planet, and goods are sold on the target system's Planet.

| # | Kind | Details | Needs | Lead |
|---|---|---|---|---|
| O1 | Illegal goods | Slaves at 1 HT on MH → EE | CRIM 4 | 3 |
| O2 | Illegal goods | Weapons at 3 HT on EE → MH | CRIM 8 | 2 |
| O3 | Illegal goods | Weapons at 10 HT on TC → GL | CRIM 1 | 1 |
| O4 | Military modules | Battle comm, on GL | CRIM 1 | 1 |
| O5 | Furs | 10 HT in MH Port → 30 HT in BH Port, up to 10 u, no space | BUSS 2 | 2 |
| O6 | Illegal goods | Slaves at 2 HT on MH → BH | CRIM 5 | 2 |
| O7 | Illegal goods | Drugs at 15 HT on BH → GL | CRIM 6 | 1 |
| O8 | Delivery | Envoy, EE Port → MH Port; one Passenger module; reputation 20+; 200 HT | POL 5 | 1 |
| O9 | Delivery | Exotic plants, GL Port → EE Port; one full Cargo module (not hull space); 150 HT and +3 reputation | BUSS 10 | 2 |
| O10 | Illegal goods | Slaves at 1 HT on MH → GL | POL 10 | 1 |
| O11 | Illegal goods | Slaves at 4 HT on MH → GL | CRIM 3 | 3 |
| O12 | Illegal goods | Drugs at 12 HT on EE → GL | CRIM 6 | 1 |
| O13 | Military hull and/or modules | Spear hull and/or up to 5 military modules (any mix), on BH | CRIM 10 | 1 |
| O14 | Military modules | Up to one of each military module (Arsenal, Battle comm, Heavy weapons, Dogfight), on TC | POL 8 | 4 |
| O15 | Expedition | Scientific expedition (criterion 15) | POL 2 | 2 |
| O16 | Military hull | Dagger + one military module, on TC | BUSS 3 | 4 |
| O17 | Illegal goods | Weapons at 8 HT on EE → GL | CRIM 8 | 3 |
| O18 | Illegal goods | Weapons at 3 HT on BH → GL | BUSS 2 | 2 |
| O19 | Illegal goods | Drugs at 10 HT on TC → SD | CRIM 8 | 2 |
| O20 | Military hull | Any military hull + up to 5 military modules (within capacity), on EE | BUSS 10 | 2 |
| O21 | Military hull | Sword + up to 3 military modules, on MH | CRIM 8 | 2 |
| O22 | Delivery | Exotic animals, MH Port → EE Port; one Passenger module; 150 HT | BUSS 5 | 1 |
| O23 | Illegal goods | Weapons at 3 HT on EE → MH | CRIM 5 | 2 |
| O24 | Military hull | Dagger + one military module, on SD | CRIM 4 | 3 |
| O25 | Illegal goods | Weapons at 5 HT on BH → MH | POL 7 | 1 |
| O26 | Military modules | Up to one of each military module, on EE | POL 5 | 1 |

### Events (taking place at the start of News)
16. "**This turn**" means the turn the event takes place in, from this News through Investment. **Price changes** move the
    current price of the named goods at the named systems once, clamped to 1–20 HT (R35). **S/D changes** move the S/D
    indicator permanently. **Police and security** changes from events last **this turn only** (creator, 2026-11-20). They
    adjust R31's numbers, which get their mechanics from the security spec. Until then the changes are recorded and shown on
    the system panel for the turn.
17. **Effects** (the creator's table, with how each lands in the game):

    | # | Event | Needs | Lead | Effect in the game |
    |---|---|---|---|---|
    | E1 | Psionic field | BUSS 5 | 1 | This turn every jump's target is 4 lower (R68), so jumps are harder. The manual says to subtract 4 from the roll, which would make a roll-under easier and contradicts the description, so the creator corrected it on 2026-11-20. Novak and the other bonuses still add. |
    | E2 | Alien attack, panic | POL 10 | 1 | Every good's price at every system −3. *One time only.* |
    | E3 | Galactic war | POL 3 | 4 | Prices everywhere: CHIPS +3, POLY +6, ISO +2. This turn and next: no ship can be bought (Investment R61, and O13/O16/O20/O21/O24's hulls; module-only opportunities still work): `No ships can be bought during the galactic war.` Police +4 everywhere this turn. |
    | E4 | Money fraud, inflation | CRIM 3 | 3 | Every player's cash halved, rounded down. Every open loan's outstanding amount halved, rounded down, interest unchanged (R91). If loans aren't built yet, cash only. *One time only.* |
    | E5 | Civil war in Gamma Leporis | POL 2 | 2 | This turn black-market **weapons** at Gamma Leporis sell for 3× the table price (+200%). Confiscated at Gamma Leporis: every ship in its Port with its cargo (agents step off into the Port, R107), every factory and every warehouse with its goods. Owners are paid 50 %: R66 for ships, 10 HT per warehouse block, and half the price paid for factory units. Each owner is told privately; the log lists what was confiscated, not the amounts. |
    | E6 | New colony at Mu Herculis | POL 3 | 4 | Every good's price at Mu Herculis +5. |
    | E7 | Chips breakthrough | BUSS 6 | 2 | CHIPS S/D +3 at every system, for the rest of the game. *One time only.* |
    | E8 | Synthetic food | BUSS 8 | 3 | FOOD S/D +2 at every system, for the rest of the game. *One time only.* |
    | E9 | Iso breakthrough | BUSS 6 | 2 | ISO S/D −3 at every system, never below −10, for the rest of the game. *One time only.* |
    | E10 | Epidemics | POL 7 | 1 | Every good's price everywhere +4. This turn no ship may jump **from** a Port or end a jump **in** a Port (it chooses Planet or Space), and moving within a system (R69) can't reach a Port. Police and security +1 everywhere this turn. *One time only.* |
    | E11 | Government screening | POL 8 | 4 | Every player loses 2d6 reputation, each rolled and logged separately (JB applies, R108). |
    | E12 | Pirates at Mu Herculis | CRIM 10 | 1 | Every warehouse at Mu Herculis loses all its goods. Each ship in its Port rolls d6: 4–6 escapes into Mu Herculis Space, 1–3 is lost with its cargo (destroyed in Port: agents step off, R107). Police and security +2 everywhere this turn. |
    | E13 | Special tax | POL 3 | 4 | Each player owes 1 HT per warehouse block, 2 HT per factory unit and 5 HT per ship. A player whose cash doesn't cover it pays nothing (creator: "waived"). Private amounts; the log says who paid. |
    | E14 | New polymer system | BUSS 3 | 4 | POLY price −5 everywhere; ISO and CHIPS +2 everywhere. *One time only.* |

### Endgame and edge timing
18. Tip-offs that would become effective after the last turn never do, and nothing is announced. Goods, jobs and expeditions
    still open at game end are worth nothing (R39).
19. **Starting the game:** turn 1 has a News like every turn. The pool starts full, with all 40 tip-offs.

### What other players see
20. Public: the tip-offs in play (slot, required connection, effective turn), the d3 roll, announcements, legal opportunities when
    taken (taker and details), the taken status of illegal ones (only to those who learned them), military ships appearing
    (as any ship, R57), delivery and black-market sale lines, and expedition departures, rolls, losses and returns.
21. Private: who learned what, the payments, quantities and prices of illegal goods (held lots appear only on the owner's
    Fleet ship cards), who took an illegal opportunity, payments received (R29), and the black-market roll and takings.

### Screens
22. **News** is a new destination beside the Deck, Trading, Infrastructure, Fleet, Corporations and the Game log, reachable
    in every phase. It lists:
    - the tip-offs in play, each with its required connection, effective turn, and a **Learn (5 HT)** button that works only
      in News and is disabled with the reason when the connection is too low: `Needs CRIM 5 (yours: 3)`;
    - the details of what the viewer has learned;
    - **this turn's announcements**.

    In News the top bar shows who is still reading and the viewer's "Done with the news" button.
23. **Opportunity phase, on Infrastructure** (R102), gets an **Opportunities** card. It lists the viewer's learned, untaken
    opportunities with a form for each: units and ship, hull configuration, target ship for modules, delivery ship, or
    expedition ship. It also lists **black-market sales** and **Furs sales** the viewer can make there now. As with the
    other forms, it can be filled in out of turn, and only the submit waits for the player's turn.
24. **Fleet:** each ship card shows its illegal lots (good, units, target planet), occupied delivery space, and an expedition
    ship as `Away on expedition` with no position.

## 📝 Proposed Solution

A `game/news/` package holds the catalogue and the pool. `TipOff` is a sealed set of definitions: the id, the required
connection, the lead, the kind, and for opportunities a typed **offer** (`IllegalGoods`, `Furs`, `MilitaryHull`,
`MilitaryModules`, `Delivery`, `Expedition`), each with the data from its row. Events are `NewsEvent` entries, each with an
`apply(game)` that coordinates through the owning classes, never reaching inside them. `TipOffPool` holds the pool and the
retired cards. `ActiveTipOff` holds a drawn card, its draw turn, its slot, who learned it (private) and who took it
(private). `Game.openNews()` resolves due cards, then draws.

Rules sit where their data lives:
- `Player`: learning (connection check and fee), illegal lots and delivery jobs held on its ships, black-market sales.
- `Ship`: what it holds, and passenger seats and cargo-module space free.
- `StarSystem` state: price shifts and police and security modifiers with their durations.
- `Turn`: the "this turn" flags (jump malus, closed ports, ship-buying ban, black-market multiplier).

The Initiative split moves R26's picking out of `ShipJumpsService` into an Initiative step with the same machinery.

**Alternatives considered:**
- *Learning automatically once the connection is high enough:* rejected. The creator wants it paid and limited to News, so
  intel is a decision every turn.
- *Illegal goods stock shared between players:* rejected. The creator chose first taker takes all, which makes turn order
  matter.
- *Building planet security inside this spec:* rejected. The creator chose a separate smuggling and security spec first.

## 📝 Architecture and data model

- `TurnPhase`: `BIDDING`, `INITIATIVE("Initiative")`, `NEWS("News")`, `JUMPS("Jumps")` (renamed from `SHIP_JUMPS`; the stored
  value is migrated on read), `TRADING`, `OPPORTUNITY`, `INVESTMENT`.
- `game/news/`: `TipOff`, `TipOffOffer` (sealed), `NewsEvent`, `TipOffPool`, `ActiveTipOff`, `BlackMarket` (the table),
  `IllegalLot(good, units, targetSystem)`, `DeliveryJob(tipOff, ship, destination, payment)`, `Expedition(ship, returnTurn | lost)`.
- `Game.news` (the pool, active, retired); `Ship.illegalLots`, `Ship.deliveryJob`; `Player.expeditions`; system modifiers
  with expiry turns; `Turn` event flags.
- `NewsService` (learn, done); `OpportunityService` gains take, sell on the black market, sell furs, and expedition return.
- Web: `POST /api/games/{id}/news/{slot}/learn`, `…/news/done`, `…/opportunity/opportunities/{slot}/take` (a body per offer kind),
  `…/opportunity/black-market/sell`, `…/opportunity/furs/sell`, `…/opportunity/expeditions/{ship}/return`. The public view
  carries the tip-offs in play without details. The viewer's view adds the details they've learned.
- DynamoDB: new attributes, no migration beyond reading `SHIP_JUMPS` as `JUMPS`. A game started before this ships gets a
  full pool on its next turn.
- Frontend: the News screen, Infrastructure's Tip-offs card, Fleet's lots, jobs and expeditions, system-panel modifiers,
  phase routing, the Initiative phase on the Deck, and the rename.

## 📝 Phasing

1. **Turn structure:** Initiative split, Jumps rename, an empty News phase that everyone marks done.
2. **Pool and learning:** catalogue, draw, effective/return/retire, the News screen, learning, public announcements
   (opportunities only expire).
3. **Events:** E1–E14 with the system modifiers and "this turn" flags (E4's loan part once loans exist).
4. **Military parts and deliveries:** hull and module opportunities, delivery jobs, the expedition. These need the
   Opportunity phase.
5. **Illegal goods and the black market:** needs the smuggling and security spec (landing on Planets), and Frank from agents.

## 📝 Edge Cases

- **A tip-off is drawn and becomes effective in the same News:** impossible, since the lead is at least 1.
- **The same card returns and is redrawn in one News:** it's a new draw, with fresh learners and a fresh taker. Lots and jobs
  from the earlier copy are unaffected.
- **Galactic war (E3) while a hull opportunity is in play:** it can't be taken this turn or next, and may expire unused.
- **Civil war (E5) confiscates a ship carrying a delivery job or illegal lots:** they're lost with it. A ship on Gamma
  Leporis's Planet keeps them and can sell weapons at 3× this turn.
- **Epidemics (E10) and a delivery to a Port:** the delivery waits for a turn when the ship can enter the Port.
- **A player's connection drops below a learned tip-off's level:** they keep what they learned. Connections don't fall today
  anyway.
- **Selling a ship with illegal lots or a job aboard:** the lots and job are lost. Lots never move into a warehouse (R66's
  overflow rule doesn't apply to them).
- **The expedition ship is the player's only ship:** allowed, and their Jumps slot ends at once (R64).

## Defaults I chose (override any)

- **Learning costs 5 HT per tip-off**, and anyone can learn the same tip-off.
- Players learn **at the same time**, not in turn order. Learning isn't logged.
- A tip-off's **kind is hidden** until learned or announced.
- **Payments from legal opportunities** (delivery fees, the expedition's 2d6 × 50) stay **private** (R29); only the event is public.
- **E5:** a ship is valued at its R66 price, a warehouse block at 10 HT, and a factory at half the price paid for it.
- **E12:** a lost ship is destroyed in Port, so its agents step off and survive.
- **E4:** rounded down, for both cash and loans.
- Illegal-goods units that don't fit **can't be bought**. A sale sells **every unit of that good bound for that planet** at once.
- **Military hulls** cost catalogue price plus crew, can take civilian modules too, and can't jump until next turn.
- **Delivery jobs** have no deadline, pay on arrival in the destination Port, and are lost without penalty with their ship.
- **Agents aboard an expedition** travel with the ship.
- The News screen is **a new destination**, and opportunities are taken on **Infrastructure** in Opportunity.

## 📋 Brief changes (with this spec)

- **R44:** Bidding, Initiative, News, Jumps, Trading, Opportunity, Investment (Control later). **R26:** turn-order picking
  happens in Initiative. Ship-jumps is renamed Jumps.
- **R56:** military parts enter via opportunities. Legal-system enforcement comes with the security spec.
- **R109** the News phase and the pool · **R110** learning · **R111** taking opportunities · **R112** illegal goods and the
  black market · **R113** military hulls and modules · **R114** delivery jobs and the expedition · **R115** events.
- R02 and R06: tip-offs are the secret opportunities and inside info.
- **D26**: News and tip-offs join the game; the Initiative phase is split out.

## Implementation notes (2026-11-21)

Built on `feat/news-smuggling`, on top of the playtest-feedback branch (#170). Where the build differs from the criteria:

- **A News with no tip-off in play closes at once** into Jumps: there's nothing to read. Test games start with an empty pool
  (`Game.lobby`'s default); every real game is created with all 40.
- **Opportunities are taken with one endpoint**, `POST …/opportunity/tip-offs/{slot}/take`, whose body carries what the
  kind needs; the card on Infrastructure builds it.
- **Military hulls** may carry civilian modules too; only the military ones count against the card's number.
- **The expedition:** agents aboard step off at the ship's place before it leaves (the spec had them travel with it), and a
  player must bring a due expedition back before finishing their Opportunity turn (no automatic return).
- **E5 (civil war):** factories are paid half of what building them would cost now, since a factory doesn't keep its price.
- **Confiscated goods** reach their owner only through Fleet and the ledger, not as a separate private notice.
- **The jump chance** a ship shows doesn't include E1's −4; the map's modifier list says "This turn −4 (news)".
- **A game saved mid-Ship-jumps** reads as Jumps; one saved while players still picked doesn't ask them again (saved games
  may lapse, creator).
