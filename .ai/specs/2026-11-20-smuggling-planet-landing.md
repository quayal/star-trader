# Smuggling: landing on a Planet

**Brief:** `.ai/specs/product-brief.md`: R30 (choosing Port, Planet or Space), R31 (Planet security), R51–R56 (hulls,
modules, Aerodynamic and military flags), R69 (moving within a system), R76 (a damaged module counts as not there), R77
(placing ships after a battle), R85–R86 (reputation), R107–R108 (agents, Badger, JB).
**Source:** the creator's answers of 2026-11-20, including corrected Port and Planet statistics (criterion 13).
**Used by:** `2026-11-20-news-and-tip-offs.md` (illegal goods and military parts are bought and sold on Planets),
`2026-11-20-agents.md` (Badger).
**Status:** proposed, not started.

## 📝 TLDR

Landing on a Planet now means getting past its **security**. Every ship that tries to land rolls **2d6**, with **+2** for an
Aerodynamic hull and **+3** for a working Battle comm module.
- **Above** the Planet's security: it lands.
- **Equal:** it's forced into the **Port**. Its illegal goods are confiscated, along with the ship itself if it has any
  military part, and the owner loses **2d6** reputation.
- **Below:** the ship and everything aboard are confiscated, **even a legal ship**, and the owner loses **3d6** reputation.

## 📝 Problem Statement

Planets have had a security number since the prototype (R31) but no mechanics. Tip-offs (R112–R113) make the Planet where
illegal goods are bought and sold, and where military hulls appear, so it has to be guarded. This makes every landing a
gamble. It's safe-ish at Mu Herculis, Gamma Leporis and Sigma Draconis, a coin toss at Tau Ceti, and close to suicide at
Epsilon Eridani and Beta Hydri. In the creator's words, "smuggling is f… dangerous, especially Beta Hydri". The numbers in
the code were wrong, so this spec also corrects them.

## 📋 Acceptance criteria

### When the roll happens
1. **Every attempt to place a ship on a Planet** rolls, whatever the ship carries (creator: "even legal ship"):
   - after a jump (R30), including moving within the system (R69);
   - after a battle, when surviving ships are placed (R77).
2. **No roll** for a ship that is already on the Planet, one leaving it, or a military hull that **appears** there from an
   opportunity (R113). An expedition returns to a **Port** (R114), never to a Planet, so it doesn't roll either.

### The roll
3. **Landing roll:** 2d6, +2 if the hull is Aerodynamic (A, R56), +3 if the ship has a **working** Battle comm module (a
   damaged one counts as not there, R76), and +3 if Badger is aboard (R108: −3 to the security he faces). The total is
   compared with the Planet's **current security**: R31's number plus any event changes this turn (E10 +1, E12 +2).
4. **Above security:** the ship lands on the Planet. Nothing else happens.
5. **Equal to security:** the ship is **forced to land in the Port** of that system instead. There:
   - every **illegal good** aboard (weapons, slaves, drugs: R112's lots) is confiscated;
   - if the ship has **at least one military hull or module** (R56), the ship itself is confiscated, with all its cargo;
   - the owner loses **2d6** reputation.

   Legal cargo on a ship that isn't confiscated stays aboard, and the ship is in Port as if it had landed there. Being
   forced into a Port doesn't check R56's ban on military ships in Ports: such a ship is confiscated anyway.
   **While Ports are closed** (Epidemics, E10) the ship **stays in Space** instead of entering the Port (creator,
   2026-11-20). The confiscation and the 2d6 loss still apply.
6. **Below security:** the ship and **everything aboard** are confiscated, legal or not, and the owner loses **3d6**
   reputation.
7. **Confiscated** means gone for good, with nothing paid back, like a ship destroyed with everything aboard (R77). Delivery
   jobs aboard (R114) are lost. **Agents aboard step off into that system's Port** and survive, as for a ship lost in Port
   (R107).
8. **Reputation loss** is rolled and logged like any other (R86), clamped at 0. JB reduces it by 1 (R108).

### What everyone sees
9. **Public:** the landing roll, its modifiers and the outcome, since the ship's position is public (R57). For example:
   - `Starfarer (Vega Lines) landed on Tau Ceti's Planet: 9 vs security 7.`
   - `Starfarer (Vega Lines) was forced into Tau Ceti's Port: 7 vs security 7. Illegal cargo confiscated. Reputation −7 (2d6).`
   - `Starfarer (Vega Lines) was seized at Beta Hydri: 8 vs security 10. Reputation −11 (3d6).`

   When a modifier comes from Badger, the line names him, which reveals him (R105).
10. **Private:** what was confiscated, meaning which goods and how many. The owner is told. Others learn only that illegal
    cargo was taken, or the whole ship.

### Screens
11. **Choosing the Planet** (R30, after a jump or after a battle) shows the odds before the player commits. For example:
    `Land on Planet: 17% lands · 11% forced into Port · 72% seized (security 9, your +0)`. It also warns when the ship
    carries illegal goods or military parts.
12. **System panel:** the Planet's security, with this turn's event change if any (`Security 9 (+2 this turn)`).

### Corrected statistics (creator, 2026-11-20)
13. The values in `StarSystem` were wrong. They become:

    | System | Port class | Legal system | Planet security | Police |
    |---|---|---|---|---|
    | Gamma Leporis | 1 | 3 | 3 | 3 |
    | Beta Hydri | 4 | 4 | 10 | 8 |
    | Epsilon Eridani | 3 | 4 | 9 | 7 |
    | Sigma Draconis | 1 | 1 | 4 | 2 |
    | Tau Ceti | 3 | 2 | 7 | 5 |
    | Mu Herculis | 0 | 1 | 3 | 0 |

    Only the display reads them today (the system panel and the landing choice), so nothing else changes. Police is
    corrected too, but it still has no mechanic.

### Odds for reference (2d6, no modifiers)

| Security | Seized (below) | Forced into Port (equal) | Lands |
|---|---|---|---|
| 3 (Gamma Leporis, Mu Herculis) | 3 % | 6 % | 92 % |
| 4 (Sigma Draconis) | 8 % | 8 % | 83 % |
| 7 (Tau Ceti) | 42 % | 17 % | 42 % |
| 9 (Epsilon Eridani) | 72 % | 11 % | 17 % |
| 10 (Beta Hydri) | 83 % | 8 % | 8 % |
| 12 (Beta Hydri during E12) | 97 % | 3 % | 0 % |

Modifiers matter a lot. An Aerodynamic hull with a working Battle comm (+5) faces Beta Hydri as security 5: 17 % seized,
11 % forced into Port, 72 % lands. Adding Badger (+8) makes it security 2: 3 % forced into Port, 97 % lands.

## 📝 Proposed Solution

A `LandingRoll` value object, in a new `game/smuggling/` package, takes the dice, the ship's modifiers and the security, and
returns an outcome: `Landed`, `ForcedIntoPort`, or `Seized`. The rules sit with the data they read:
- `Ship.landingBonus()` reads the hull's Aerodynamic flag and a working Battle comm module.
- `Ship.isMilitary()` reads R56.
- `Ship.confiscateIllegalCargo()` removes the illegal lots.
- `Player` applies the reputation loss and the agents stepping off.

`Game` routes every Planet placement (jump, R69, battle placement, expedition return) through one
`placeOnPlanet(ship)`, so no path skips the roll. Events' this-turn security changes come from `Turn`.

**Alternative considered:** rolling only for ships with illegal cargo or parts. Rejected, because the creator wants legal
ships to risk seizure too.

## 📝 Architecture and data model

- `game/smuggling/`: `LandingRoll`, `LandingOutcome` (sealed).
- No new stored state apart from the log. Confiscation reuses ship removal.
- Web: the existing placement endpoints return the outcome. Placement views carry the odds.
- Frontend: the odds on the Planet choice, the system panel's security, and the log lines.

## 📝 Edge Cases

- **Several ships placed on the same Planet at once** (after a battle): each rolls separately, in the order the player
  placed them.
- **A ship forced into Port with a delivery job:** the job stays aboard. If that Port is the job's destination, it's
  delivered (R114).
- **A ship that can't land anywhere else** (Space is always available): no special case, because the player chose the
  Planet.
- **Reputation already at 0:** the loss is logged, and nothing more happens.

## Defaults I chose (override any)

- **Every placement on a Planet rolls**, after battles too. Ships that appear from an opportunity don't roll.
- **Badger** adds +3 to the roll, which equals −3 security, and is revealed by it.
- The roll and its outcome are **public**. What was confiscated is private.
- **Agents survive** confiscation and step off into the Port.
- **Forced while Ports are closed (E10):** the ship stays in Space, and the illegal goods and reputation are still lost.
- **Not in this spec:** police in Space and the legal system in Ports. R56's "military ships can't enter a Port with legal
  system 3 or 4" (Gamma Leporis, Beta Hydri and Epsilon Eridani with the corrected values) stays unenforced until those
  rules are given.

## 📋 Brief changes (with this spec)

- **R31:** the corrected Port class, legal system and Planet security values. Planet security now has a mechanic (R116).
  Police and the legal system still have none.
- **R116** the landing roll and its modifiers · **R117** the outcomes (above, equal, below), confiscation and reputation
  · **R118** visibility.
- **R108:** Badger can join the pool once this ships. Frank can join once the black market (R112) ships.
- **D27:** smuggling (Planet landing) is part of the game. Police acts only in the Control phase (creator, 2026-11-20), so it
  comes with that phase. Port legal systems come later.
