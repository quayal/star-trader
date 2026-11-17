# E09 — Shipyards: buying, refitting and selling ships

**Brief:** `.ai/specs/product-brief.md` — R26, R29, R39, R40, R44, R47, R48, R49, R50, R51–R58, N07, D13, plus the new R60–R67 and D16 added with this spec.
**Source:** the creator's answers of 2026-11-17 ("new feature, buying ships …" and the follow-up), and the **Later** list of `2026-11-16-e07-hulls-modules-fleet.md` ("later is now").
**Siblings:** E06 Factories and warehouses (the Investment phase), E07 Hulls, modules and the Fleet tab, E04 Ship-jumps.
**Prototype:** none. The new panels follow the design system (`docs/design-system.md`).

## 📝 TLDR

During Investment a player can now:

1. **Buy a ship.** They choose a name (their own or a suggested one), a shipyard (Beta Hydri, Epsilon Eridani or Tau Ceti), a civilian hull, its modules and a crew class, and pay hull + modules + crew. The ship waits in the yard for a whole turn and leaves it in the next Investment, after Collect. It makes its first jump the turn after that: bought in turn 2, it leaves the yard in turn 3's Investment and jumps in turn 4.
2. **Refit a ship at a shipyard.** They can add a module (price + its crew) or remove one (paid back half the module's price). This needs the ship in a shipyard: in the yard itself, or in Port at one of the three shipyard systems. There's no wait: the change is in place for the next turn.
3. **Sell a ship** for half its hull and modules.

For buying, the player is "everywhere": no ship needs to be at the yard, as for warehouses and factories (R45, R46).

**Fuel is never a concern** (refuelling happens between jumps). That part of N07 is dropped for good.

**Still out:** repair and battle (nothing damages a ship yet), military hulls and modules (only through an opportunity, R56, not built), and selling warehouses or factories.

## 📝 Rules

| Id | Rule |
|---|---|
| R60 | **Shipyards.** Beta Hydri, Epsilon Eridani and Tau Ceti have a shipyard; the other three systems don't. A ship in a shipyard is in a fourth location, **Shipyard**, besides Port, Planet and Space (R30). That location is public, like any ship's position (R57). |
| R61 | **Buying a ship.** During Investment, after their own Collect (R47, R48), a player may buy any number of ships, whether or not they have a ship anywhere. They choose: a name (R62), one of the three shipyards, a civilian hull (R56), its civilian modules within the hull's capacity (R52), and a crew class. The price is the hull's price, plus each module's price, plus the crew needed (R53) × the class's price per crew member (A 20, B 10, C 6, D 2 HT). It's refused when the player can't pay it all. The new ship is empty and stands in the yard (R60). |
| R62 | **Ship names.** A name is 1–20 characters after trimming, and unique in the game among every player's ships, ignoring case. The suggestions are 26 names: Spiral, Coral, Knight, Needle, Sphinx, Quasar, Flash, Luna, Alfa, Falcon, Pulsar, Tzar, Satyr, Regent (the original game's tokens) and Wayfarer, Lantern, Meridian, Halcyon, Bright Reach, Corvid, Ironside, Tallow, Ostrava, Kestrel, Solace, Pilgrim (today's list). Starting ships (R40) are named from this list; a suggestion is a name from it not yet used in the game. Once all 26 are taken, the player invents one. |
| R63 | **Leaving the yard.** A ship bought in turn N's Investment stays in the yard for the whole of turn N+1. In turn N+1's Investment, right after its owner's Collect, it moves into the Port of its shipyard's system. Its first jump is in turn N+2. Output comes before the ship (phase 6 before phase 7 of the board game), so the ship never receives Collect output in the Investment it leaves the yard. Leaving is a public log event. |
| R64 | **In the yard.** A ship in the yard doesn't jump, trade, take Collect output or take part in cargo moves (R58). It isn't presence at that system for Trading (R23, R49). In Ship-jumps, a player with no ship able to jump has nothing to do: their slot ends at once. |
| R65 | **Refitting.** During Investment a player may add civilian modules to, or remove modules from, a ship in a shipyard: in the yard, or in Port at Beta Hydri, Epsilon Eridani or Tau Ceti. Adding costs the module's price + its crew × the ship's crew class price. Removing pays back half the module's price, rounded down; the crew isn't paid back. A removal that would leave less cargo space than the POLY and ISO aboard is refused. No wait: the ship keeps its location, and the change applies from the next turn on. Refits are private (R57) and not logged. |
| R66 | **Selling a ship.** During Investment a player may sell any of their ships, wherever it is, including one in the yard: they get half of its hull's and modules' prices, rounded down; crew isn't paid back. Selling is never blocked by cargo: if the player has a warehouse at the ship's system, the cargo moves into it as far as its free space allows (CHIPS and FOOD always fit, R42); the rest is lost. The player is told exactly what moves and what's lost before they confirm and again after, never silently; cargo stays private (R29), so neither is logged. The sale is a public log event, without the amount (R29). A player may sell every ship they have. |
| R67 | **Crew class.** A ship has one crew class for all its crew. It's chosen when the ship is bought, and every crew member added later (R65) is paid at that class. Starting ships are class B, already paid (R55). The class has no effect yet in this epic; its effect on jumps is the next spec, still before the playtest. |

| Id | Decision |
|---|---|
| D16 | 2026-11-17 — Ships can be bought, refitted and sold (R60–R67). This lifts N07's ship purchasing and commissioning and D13's "buying ships … shipyards are later". Fuel is dropped for good. Repair, hull damage and battle stay out until a battle exists; military parts stay out until opportunities do. Money alone still decides the winner (R39), so selling ships in the last Investment is a way to turn them into cash. |

## 📋 Stories and acceptance criteria

### E09-S01 — Ship names come from the original game and are unique

1. [R62] `Ship.NAMES` becomes the 26 names. New games name starting ships from them; saved games keep the names they have.
2. [R62] A name is trimmed and must be 1–20 characters. "Choose a name of 1 to 20 characters." otherwise.
3. [R62] A name already used by any ship in the game, ignoring case, is refused: "There's already a ship called Spiral in this game."
4. [R62] The server suggests a free name from the list, or none once all 26 are used.

### E09-S02 — I can buy a ship at a shipyard during Investment

1. [R61] `POST /api/games/{id}/investment/ships` with `{ name, shipyard, hull, modules: [module], crewClass }` buys the ship and answers with the game. The cost goes out of `you.money`.
2. [R61, R60] Refused with `400`: a shipyard that isn't Beta Hydri, Epsilon Eridani or Tau Ceti; an unknown or military hull or module (R56); modules over the hull's capacity or two Safe jumps (R52); an unknown crew class; a bad or taken name (S01); more than the player's money ("This ship costs 233 HT; you have 180 HT.").
3. [R61, R48] Only during Investment, after the buyer's Collect; `409` otherwise, as for buying warehouses.
4. [R60, R57] The ship appears in `players[].ships` with `location: SHIPYARD` at its system. Its hull, modules and crew class appear only in the owner's Fleet data.
5. [R50] The log gets "Orion Freight ordered Spiral at the Tau Ceti shipyard". No hull, no price.
6. [R61] `you.invest` carries what the form needs: the civilian hulls and modules with their prices, the crew class prices, the three shipyards and a suggested name.

### E09-S03 — My new ship leaves the yard one turn later

1. [R63] A ship bought in turn N is in the yard during all of turn N+1. In turn N+1's Investment it moves to the Port of its system once its owner has confirmed Collect, or when Investment starts for an owner with no factories.
2. [R63] It isn't a Collect destination in that Investment. After leaving, it can take cargo moves (R58) and jump in turn N+2.
3. [R63] The log gets "Spiral leaves the Tau Ceti shipyard".
4. [R64] In the yard, the ship is refused as a jump ("Spiral is still in the shipyard."), isn't offered as a Trading or Collect destination, and isn't a cargo-move ship. A player whose only ship there is in the yard has no presence there.
5. [R64] In Ship-jumps, a player with no ship able to jump has their slot end at once, with a log line ("Orion Freight has no ship to jump"), so the turn doesn't wait for them.
6. Saved ships have no purchase turn and load as already out of the yard.

### E09-S04 — I can refit a ship at a shipyard

1. [R65] `POST /api/games/{id}/investment/ships/{name}/modules` with `{ module }` adds one module. `DELETE /api/games/{id}/investment/ships/{name}/modules/{module}` removes one.
2. [R65] Adding costs the module price + its crew × the class price. Removing pays back half the module price, rounded down.
3. [R65] Refused with `400`: a ship that isn't the caller's; a ship that isn't in the yard or in Port at a shipyard system ("Spiral must be at a shipyard to refit."); over capacity or a second Safe jump; a military module; not enough money; a removal that leaves too little cargo space for the POLY and ISO aboard.
4. [R65] Not logged. It changes only the owner's Fleet data and `you.money`.

### E09-S05 — I can sell a ship

1. [R66] `DELETE /api/games/{id}/investment/ships/{name}` sells it. The caller gets half the hull and module prices, rounded down (a starting ship: (160 + 4×4 + 17 + 10) / 2 = 101 HT).
2. [R66] A ship with cargo can be sold. With a warehouse of the seller's at the ship's system, the cargo moves into it up to its free space (POLY and ISO one space a unit, CHIPS and FOOD none); whatever doesn't fit, or all of it without a warehouse there, is lost. The response says what moved and what was lost, and the game shows it to the seller. `409` outside Investment.
3. [R66] The ship disappears from `players[].ships`, and the log gets "Orion Freight sold Coral".

### E09-S06 — The screens

1. A **Shipyard** panel on the Infrastructure screen (D12), under warehouses and factories, shows during the player's Investment. It has: a name field with **Suggest**, a shipyard select, a hull select, a module count for each civilian module, a crew-class choice, a live total ("Hull 160 + modules 43 + crew 1 × 10 = 213 HT") and **Buy ship**. Capacity left and money left are shown before Buy.
2. During Investment, each ship on the **Fleet** tab has **Sell** (with the amount, and a confirm step that names the cargo going to the warehouse and the cargo lost, as a `--warn` message) and, when it's at a shipyard, **Add module** / **Remove** per module, with the cost or refund on the button.
3. The map and system pages show a ship in a yard as "in shipyard". The Deck's jump form doesn't offer it.
4. [R39] In the last two Investments of a game, the Buy panel warns: "This ship would leave the yard only after the game ends." Buying stays allowed.
5. Phone width, keyboard, tokens only, as for every screen.

## 🧱 Data

- `Ship` gains `crewClass` (absent in a saved item → B, R55) and `boughtOnTurn` (absent → already out of the yard, S03-6). `ShipLocation` gains `SHIPYARD`.
- `CrewClass` becomes a type with its price per crew member. `Hull` and `Module` answer whether they can be bought (not military) and their sale value.
- `BACKWARD_COMPATIBILITY.md` gets the new endpoints, the `SHIPYARD` location value (a new enum value clients must handle) and the new item fields.

## Defaults I chose (change on review)

- **Selling a whole ship works anywhere**, since the player is "everywhere" in Investment. Only refits need a shipyard, as you said.
- **Crew isn't paid back** when selling a ship or removing a module. "Price is 50%" is read as half of hull and modules.
- **Half prices round down.**
- **Refit needs the Port** (confirmed): Space is for battles, Planets are for smuggling (both later).
- **Starting ships are named from all 26.**
- **Buy, refit and sell live in two places**: buying on Infrastructure, refit and sell on Fleet.

## Answered on review (2026-11-17)

- **Crew class** is chosen now. Its effect on jumps is the next spec, before the playtest.
- **Battles** are not before the playtest.
- **Names:** all 26, the original 14 and today's 12.
- **Selling with cargo** is allowed: the cargo goes to the warehouse at that system if there is one and it fits, otherwise it's lost. Never silently, never blocked.
- **Refit needs the Port.**

## Notes from implementation and review (2026-11-17)

- **S05-2 as built:** the sale's response is the game, not a report. The confirm step predicts what goes to the warehouse with the server's rule (`js/shipyard.js`); the line after the sale reads what the warehouse actually gained from the response, so it never relies on the prediction.
- **R62 tightened:** a name may use letters, digits, spaces, hyphens, apostrophes and full stops, with at least one letter or digit, because it is the ship's address in the refit and sell URLs ("A/B" or ".." could never be sold).
- **Names order:** starting ships keep the twelve names they had; the fourteen original-game names come after them in the suggestions.
- **Late buys:** in the second-last turn the form says the ship leaves the yard only in the last Investment, too late to jump; in the last turn, that it never leaves. Fleet says "in the shipyard until the game ends" for such a ship.
