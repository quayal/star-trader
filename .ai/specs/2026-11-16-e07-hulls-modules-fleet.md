# E07 — Ships from hulls and modules, and the Fleet tab

**Brief:** `.ai/specs/product-brief.md` — R40, R42, R23, R29, R16, N07, plus the new R51–R57 and D13 added with this spec.
**Source:** the creator's vault note `10-projects/Trader/ships and modules.md` (hull, module and crew tables, additional rules) and `Battle.md` (read for context; battle is not built here).
**Siblings:** E04 Ship-jumps, E05 Trading, E06 Factories and warehouses (`2026-11-15-e06-factories-warehouses.md`).
**Prototype:** none. The Fleet screen is new and follows the design system (`docs/design-system.md`).

## 📝 TLDR

A ship is no longer one fixed thing with 10 cargo spaces. It is a **hull** plus up to *module capacity* **modules**, and its stats are the sum of both. This epic builds the data (13 hulls, 8 modules, 4 crew classes, copied from the creator's tables), makes a ship's cargo space come from its Cargo modules, and adds a **Fleet** tab where a player sees every ship they own: hull, modules, and all stats.

A hull's own cargo column is its built-in cargo spaces (`N` means none). A Cargo module adds 2 spaces. A ship with no cargo spaces can still carry CHIPS and FOOD, which take no space (R42), but not POLY or ISO.

The two starting ships become a **Clarinet with 4 Cargo modules, a Light weapons module and a Safe jump module**: 8 cargo spaces each, down from the fixed 10. Saved games keep working: their ships become a Clarinet with 5 Cargo modules, which is exactly the 10 spaces they have today.

**Out of scope here:** buying ships or modules (civilian or military), crews' cost, shipyards and build time, repair, battle and rockets, and the military and aerodynamic *rules*. Military hulls and modules exist in the catalogue and are labelled, but nothing in the game can give a player one, and their legal consequences (R56) are not enforced. The rules for shipyards, crews and repair from the note are recorded under **Later** so they aren't lost.

## 📝 Rules

| Id | Rule |
|---|---|
| R51 | **Hulls.** A hull has: module capacity, intercept, crew, weapons, shields, built-in cargo spaces (`N` = 0), price, repair cost, and two optional flags: military (`M`) and aerodynamic (`A`). The catalogue is the creator's table, verbatim (see *Catalogue*). Hulls are game data, not something players create. |
| R52 | **Modules.** A module has: weapons (and rocket weapons, shown in brackets in the note), crew, cargo spaces, passengers, price and an optional military flag. A ship holds at most as many modules as its hull's module capacity, and the same module may be fitted several times. **Safe jump** is the exception: it upgrades the standard jump module, takes no module capacity, and a ship has at most one. The catalogue is the creator's table, verbatim. |
| R53 | **A ship's stats** are derived, never stored: weapons = hull + all modules; rocket weapons = sum over modules; crew needed = hull + all modules; cargo spaces = hull's built-in + all modules; passengers = sum over modules; shields, intercept, module capacity = the hull's. Modules used / module capacity is shown with them. |
| R54 | **Cargo.** A ship's cargo spaces are its R53 cargo spaces (this replaces R42's fixed 10 per ship). POLY and ISO take one space per unit, CHIPS and FOOD none, as before. A ship with 0 cargo spaces carries no POLY or ISO, so a buy of them for that ship is lost like any overflow (R16). Everything else in R42 is unchanged. |
| R55 | **Starting ships.** Every player starts with 2 ships, each a **Clarinet with 4 Cargo modules, 1 Light weapons module and 1 Safe jump module** (5 of 5 module capacity, since Safe jump takes none; 8 cargo spaces, crew 1, no passengers). They are placed and named as in R40 and start empty. Their crew is class B and already paid for. This changes R40's "2 ships" only in what a ship is. |
| R56 | **Military and aerodynamic.** `M` hulls and modules are military: they can't be bought normally, only through an `opportunity` (not built), possessing one is illegal, and a ship with any can't enter a Port with legal system 3 or 4. `A` hulls make landing on a Planet easier. **None of this is enforced yet**: no mechanic gives a player a military part, and landing has no difficulty. The Fleet tab shows the flags. |
| R57 | **Visibility.** A ship's name and position stay public (R40, R30). Its hull, modules and stats are shown **only to its owner** on the Fleet tab for now, like its cargo (R29's spirit); nobody sees another player's hulls or modules. This is a default and cheap to widen later. |

### Catalogue

Hulls (from the note; `-` is an empty flag cell):

| Hull | Mod cap | Intercept | Crew | Weapons | Shields | Cargo | Price | Repair | Flags |
|---|---|---|---|---|---|---|---|---|---|
| Dagger | 1 | 2 | 1 | 5 | 4 | N | 250 | 50 | M A |
| Corco Gamma | 2 | 1 | 1 | 2 | 1 | N | 80 | 10 | A |
| Flute | 3 | 3 | 1 | 3 | 3 | N | 225 | 30 | A |
| Sword | 3 | 3 | 1 | 5 | 4 | N | 500 | 80 | M |
| Corco Zeta | 4 | 1 | 1 | 1 | 1 | N | 80 | 10 | - |
| Clarinet | 5 | 2 | 1 | 3 | 2 | N | 160 | 20 | - |
| Spear | 5 | 1 | 2 | 5 | 4 | 1 | 600 | 100 | M |
| Corco Iota | 6 | 2 | 1 | 3 | 3 | 1 | 190 | 30 | - |
| Phoenix | 6 | 1 | 1 | 1 | 1 | 1 | 85 | 10 | - |
| Corco Mu | 9 | 1 | 1 | 2 | 1 | 1 | 160 | 30 | - |
| Monarch | 12 | 1 | 3 | 1 | 1 | 4 | 150 | 15 | - |
| Leviathan | 18 | 1 | 4 | 1 | 1 | 6 | 250 | 20 | - |
| Piccolo | 0 | 3 | 1 | 2 | 2 | N | 70 | 10 | A |

Modules (`(n)` is rocket weapons):

| Module | Weapons | Crew | Cargo | Passengers | Price | Military |
|---|---|---|---|---|---|---|
| Arsenal | 7 (14) | 1 | 0 | 0 | 100 | M |
| Safe jump | 0 | 0 | 0 | 0 | 10 | - |
| Battle comm | 6 | 2 | 0 | 0 | 50 | M |
| Cargo | 0 | 0 | 2 | 0 | 4 | - |
| Heavy weapons | 7 (10) | 1 | 0 | 0 | 80 | M |
| Dogfight | 4 (7) | 0 | 0 | 0 | 30 | M |
| Light weapons | 3 (5) | 0 | 0 | 0 | 17 | - |
| Passenger | 0 | 1 | 0 | 2 | 15 | - |

Crew classes (cost per crew member, for Later): A 20, B 10, C 6, D 2.

## 📋 Stories and acceptance criteria

### E07-S01 — Hulls and modules exist as game data, and a ship is made of them

1. [R51, R52] The 13 hulls and 8 modules above are game data with exactly those numbers and flags. Hull and module are types of their own; a hull or module owns its own rules (§8 of the engineering standards).
2. [R52] A ship can't hold more modules than its hull's module capacity, counting every module but Safe jump, and can't hold two Safe jump modules. A violation is refused with a message naming the hull and its capacity.
3. [R53] A ship answers its weapons, rocket weapons, crew needed, cargo spaces, passengers, shields, intercept and modules used / capacity. A test per derived stat, with at least one ship that mixes hull and module contributions (e.g. a Flute with Arsenal, Battle comm and Passenger needs 5 crew, as in the note).
4. [R55] A ship is saved as its hull and its modules (by name), and loads back identical (E02-S03).
5. [BACKWARD_COMPATIBILITY.md] A ship saved before this change has no hull; it loads as a Clarinet with 5 Cargo modules, so it keeps its 10 cargo spaces and its cargo. A test loads such an item.

### E07-S02 — Cargo space comes from the ship's modules, and new games start with the new ships

1. [R55] A new game gives each player 2 ships, each a Clarinet with 4 Cargo modules, 1 Light weapons module and 1 Safe jump module, placed and named as before (R40).
2. [R54] Everything that used the fixed `CARGO_SPACES = 10` — trading loads, Collect's destinations and free space, the Trading and Infrastructure screens — uses the ship's own cargo spaces. `CARGO_SPACES` is removed.
3. [R54] A ship with 0 cargo spaces holds no POLY or ISO: loading them keeps 0 and loses the rest (R16), and the screens show "0 free". CHIPS and FOOD still load in full.
4. [R54] A ship holding more than its spaces (possible only if its spaces shrank) never shows negative free space; nothing already held is removed.
5. [R42] The rest of R42 and R49 behaves as before (choosing which ship loads, splitting across ships and a warehouse); the existing tests pass with the new capacity where they depended on 10.

### E07-S03 — A Fleet tab shows my ships with all their stats

1. [R57] The top navigation has a **Fleet** destination beside Deck, Trading and the Game log. It is visible to a player in any phase of a running game.
2. [R53, R57] It lists the signed-in player's ships. Each shows: name; hull (name, flags); modules, one row per module with its own stats, repeated modules grouped with a count; and the stat block from R53 (weapons, rockets, shields, intercept, crew needed, cargo spaces used / total, passengers, modules used / capacity).
3. [R30, R40] Each ship also shows where it is (system and location, or "in Space").
4. [R56] Military and aerodynamic hulls and modules carry a small label with a text explanation ("Military: illegal to possess", "Aerodynamic: easier landing"). The labels are not colour-only.
5. [R54] Each ship's cargo is listed per good, with used / total spaces; a ship with 0 spaces says "No cargo space — fit Cargo modules to carry POLY or ISO" (the sentence is shown; nothing can act on it yet).
6. [R57] No response or screen carries another player's hulls, modules or stats. A test asks the game endpoint as player B and finds none of A's hull or module data (engineering standards §10).
7. [D13] The tab works at phone width, is reachable with the keyboard, and follows the design system: tokens only, shared table classes, no new colour literals.
8. [E02-S04] It updates with the regular poll, and a player with no ships (not possible today) sees "You have no ships".

## Later (not filed; recorded from the note so it isn't lost)

- **Shipyards.** Ships are built only in a shipyard: Beta, Epsilon and Tau. A ship commissioned in turn 1 leaves the yard in turn 2 and can jump in turn 3; it can't carry factory output in turn 2. Buying needs the player at the yard.
- **Crews.** A ship needs crew = hull + all modules, all of one class (A 20, B 10, C 6, D 2 per head); the full crew price is paid at commissioning. The note's example: a Flute with Arsenal, Battle comm and Passenger needs 5 crew.
- **Buying and refitting civilian hulls and modules**, price from the tables; **military ones** only via opportunities (R56).
- **Repair**: hull repair cost from the table, 10 HT per module (4 HT for a Cargo module, with no crew cost).
- **Battle** (see `Battle.md`): attack = weapons of hull + modules against the hull's shields on 2d6, hits distributed over hull and modules, module damage and destruction, rockets. N07 stays in force until a later spec lifts it.
- **Legal and aerodynamic rules** of R56, once ports have legal systems that matter.

## Defaults I chose (change on review)

- Fleet shows only the owner's hulls and modules (R57), the conservative reading of "money and cargo are private".
- Starting ships carry 8 cargo spaces, not 10, as a direct result of Clarinet 5 slots = 4 Cargo + 1 Light weapons. The Light weapons module was the creator's pick; swap it for a fifth Cargo module to keep 10.
- Legacy ships become Clarinet + 5 Cargo (no Safe jump) so games saved mid-play lose nothing.
