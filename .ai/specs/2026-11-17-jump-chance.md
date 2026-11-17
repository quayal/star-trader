# Jump chance: a jump can fail

**Brief:** `.ai/specs/product-brief.md` — R19 (jumps are instant), R26 (each ship jumps once per turn), R30 (landing), R52 (Safe jump module), R57 (a ship's stats are private), R67 (crew class).
**Source:** the creator's vault note `10-projects/Trader/jump chance.md` (the route table) and their request of 2026-11-17.

## 📝 TLDR

A jump no longer always succeeds. Every route between two systems has a base chance, and the two directions can differ: Gamma Leporis → Beta Hydri is 7, Beta Hydri → Gamma Leporis is 5. A ship adds its crew's modifier (A +5, B +3, C +1, D +0) and +2 if it has a Safe jump module. The game rolls 2d6. A roll equal to or under the total succeeds. Before jumping, the player sees the chance of success as a percentage.

## 📋 Acceptance criteria

1. **Base chance per route and direction** (the creator's table; the left number is for the left-to-right direction):

   | Route | → | ← |
   | --- | --- | --- |
   | Gamma Leporis ↔ Beta Hydri | 7 | 5 |
   | Gamma Leporis ↔ Sigma Draconis | 4 | 4 |
   | Gamma Leporis ↔ Mu Herculis | 2 | 2 |
   | Gamma Leporis ↔ Tau Ceti | 7 | 5 |
   | Gamma Leporis ↔ Epsilon Eridani | 7 | 6 |
   | Beta Hydri ↔ Sigma Draconis | 4 | 6 |
   | Beta Hydri ↔ Mu Herculis | 3 | 6 |
   | Beta Hydri ↔ Tau Ceti | 8 | 8 |
   | Beta Hydri ↔ Epsilon Eridani | 8 | 8 |
   | Sigma Draconis ↔ Mu Herculis | 5 | 6 |
   | Sigma Draconis ↔ Tau Ceti | 7 | 5 |
   | Sigma Draconis ↔ Epsilon Eridani | 7 | 5 |
   | Mu Herculis ↔ Epsilon Eridani | 6 | 4 |
   | Mu Herculis ↔ Tau Ceti | 9 | 4 |
   | Tau Ceti ↔ Epsilon Eridani | 9 | 9 |

2. **Modifiers.** Add at most two: the ship's crew (A +5, B +3, C +1, D +0; a ship has one class, R67) and +2 for a Safe jump module (a ship has at most one, R52). The total is the ship's **jump target** for that destination.
3. **The roll.** The game rolls 2d6 when the player jumps. A roll ≤ target succeeds and the jump goes on as before: the ship arrives in Space and its player chooses where it lands (R30).
4. **A failed jump.** The ship stays where it was, in the same system and location, and has used its jump for this turn (R26). It doesn't wait to land. If it was the player's last ship to jump, their slot ends, just as it does after a landing.
5. **The log.** Both outcomes are public, like the jump itself. Success: `Jump: Vega Lines — Wayfarer → Tau Ceti (rolled 5)`. Failure: `Jump: Vega Lines — Wayfarer failed to jump to Tau Ceti (rolled 9) and stays at Gamma Leporis`. The target isn't logged, because it would reveal the crew and modules, which are private (R57).
6. **Shown before jumping.** On the jump form, under the destination, the player sees their chosen ship's chance for the chosen destination: `Chance of success: 58% (2d6 roll of 7 or less)`. It updates when either choice changes. A target of 12 or more shows 100%. Nothing shows for the system the ship is already at.
7. **The server computes it.** Each of the viewer's own ships (`you.ships[]`) carries `jumpChances`: for every other system, its `target` and `percent` (P(2d6 ≤ target), rounded to the nearest whole percent). Only the owner gets it, like the ship's other stats (R57).
8. **After a failed jump** the form announces `Wayfarer's jump failed: it stays at Gamma Leporis.` instead of opening the landing dialog.

9. **On the map** (the creator's follow-up, 2026-11-17): every system has an arrowhead just outside its circle on each route, pointing at the other system and holding the base chance of a jump that way. Everyone sees them. The labels move clear of them: the top row's ship lines go above its name, and the bottom row's name and ships go under its circle. A line under the map explains the arrowheads. Each system's link tells a screen reader the same chances. The board sends them as `board[].jumpChances` (base chances, public).

## Defaults I chose

- **A failed jump leaves the ship where it was and uses its jump** (the creator's answer, 2026-11-17).
- **Crew D adds 0.** The rule names A, B and C only.
- **The roll is logged, the target isn't**: the roll is public like the position roll (R43), and the target would leak private stats.
- **The percentage is rounded to a whole percent**, so 1/36 shows as 3%.
- **The map shows base chances, not your ship's.** They're the same for everyone and don't depend on which ship is chosen; the jump form gives the chance for your ship.
- **Nothing stored changes.** A failed jump is a name in `jumpedShips` with no `landingShip`, which the turn can already hold.

## Brief changes

- New rule R68 (jump chance), recorded with this spec. R19 stays: a jump that succeeds is still instant.

## Out of scope

- Other effects of a failed jump (damage, being lost, drifting elsewhere).
- Other modifiers (weather, events, police).
