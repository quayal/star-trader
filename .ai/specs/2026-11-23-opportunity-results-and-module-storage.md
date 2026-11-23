# Opportunity results in the Opportunity phase; moving modules between ships and warehouses

Date: 2026-11-23. Status: in progress. Source: the creator's request, 2026-11-23, and answers to three questions the same day.

## The request

> All taken opportunities bring results in opportunities phase, which includes selling both legal and illegal goods and
> completing missions. Make sure this is like that. Also, illegal modules, once on a ship in a port where this is
> possible, can be moved between ships (swapped). Also, legal modules can be stored in warehouses (5 cargo units).

Answers (creator, 2026-11-23):

- Swaps of military modules happen only in a Port with legal system 1 or 2 (Sigma Draconis, Mu Herculis, Tau Ceti),
  the Ports a ship with military parts may enter under R56.
- Swapping, storing and fitting a stored module happen in Investment, like refits (R65).
- Moving a module is free: no price, and its crew comes with it (no crew payment on the receiving ship).

## What the code did before this change

| Result of an opportunity | When it happened | Change needed |
|---|---|---|
| Illegal goods sold on the black market (R112) | Opportunity, on the seller's turn | none |
| Furs sold in Beta Hydri Port (R112) | Opportunity, on the seller's turn | none |
| Expedition back and paid (R114) | Opportunity, on the owner's turn | none |
| Military hull or modules (R113) | at once, when taken in Opportunity | none: that's the taking itself |
| **Delivery paid (R114)** | **the moment the ship was placed in the destination Port, in Jumps (or after a battle)** | **moves to Opportunity** |

## Rules

- **R121 (opportunity results).** Every result of a taken opportunity happens in the Opportunity phase, on the player's
  own Opportunity turn after their Collect: selling illegal goods and furs, bringing back the expedition, and completing
  a delivery. A delivery is completed by the player's action "Deliver" while the ship carrying it is in the destination
  Port; it pays and changes reputation as R114 says, and the public log names the corporation, the cargo and the system,
  as before. A ship placed in the destination Port during Jumps no longer delivers by itself. No deadline (R114), so a
  player who doesn't deliver this turn may deliver in a later Opportunity, as long as the ship is still in that Port.
- **R122 (legal modules in warehouses).** In Investment, a player may take a legal (non-military) module off their ship
  in Port at a system where they have a warehouse and store it there, and fit a module stored there to their ship in
  that Port. A stored module takes 5 of the warehouse's cargo spaces, the same space POLY and ISO use. Storing is
  refused when the warehouse has fewer than 5 free spaces, when the module is military, when the ship has no working
  module of that kind (a damaged one is repaired first), and when what's left on the ship couldn't hold the POLY and ISO
  it carries (as R65's removal). Fitting is refused when the hull has no room (R52). Both are free: nothing is paid back
  and nothing is paid, and the module's crew comes with it. Private, not logged (like R65).
- **R123 (military module swaps).** In Investment, a player may move a military module from one of their ships to
  another of their ships, both in Port at the same system whose legal system is 1 or 2. Refused for a legal module (it
  goes through a warehouse, R122), when the source has no working one of it, when the receiving hull has no room, or
  when what's left couldn't hold the source's POLY and ISO. Free, the crew comes along; private, not logged. Military
  modules can't be stored in a warehouse (as illegal goods can't, R112).

Defaults taken without asking (tunables, not foundations; say if any is wrong):

- Only a working module moves; a damaged one is refused with "repair it first" rather than moving its damage.
- Pirates (E12) still take only goods from a warehouse; stored modules stay.
- Legal modules move ship to ship only through a warehouse, since the request named swapping for illegal ones only.

## Placement (engineering standards §13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| A stored module takes 5 spaces; store/take refused for military or no room | the warehouse's modules and cargo | own data | `Warehouse.withModuleStored`, `Warehouse.withModuleTaken`, `Warehouse.cargoSpacesUsed` |
| Only a working module comes off; what's left must hold the cargo | the ship's modules, damage, cargo | own data | `Ship.withModuleTakenOff` |
| A moved-in module must fit the hull | the ship's hull and modules | own data | `Ship.withModuleFitted` |
| A Port admits military ships only at legal system 1–2 (R56, R123) | the system's location stats | own data | `LocationStats.admitsMilitaryShips` |
| Store / fit-from-warehouse need the ship in Port with a warehouse there | the player's ships and warehouses | parts of one player | `Player.storeModule`, `Player.fitStoredModule` |
| Swap needs two own ships in the same Port that admits military ships | the player's ships | parts of one player | `Player.moveMilitaryModule` |
| Delivery pays when the ship is in the destination Port | the ship's job, the player's money and reputation | parts of one player | `Player.deliver` (refuses instead of returning null) |
| Deliver only on the player's Opportunity turn after Collect; logged | whose turn it is, the log | spans parts | `Game.deliver` |
| Store / fit / swap only in Investment | the phase | spans parts | `Game` via `withInvestment` |

## API (additive)

- `POST /games/{id}/opportunity/deliveries` `{ ship }`: deliver (R121).
- `POST /games/{id}/investment/ships/{ship}/modules/{module}/store`: store a legal module in the warehouse there (R122).
- `POST /games/{id}/investment/ships/{ship}/modules/stored` `{ module }`: fit a stored module (R122).
- `POST /games/{id}/investment/ships/{ship}/modules/{module}/move` `{ ship }`: move a military module to another ship (R123).
- `you.warehouses[].modules: [{ id, name, count }]`; `cargoUsed` counts stored modules' spaces.
- `you.ships[].deliveryTo`: the delivery's destination `StarSystem` constant, or null.
- Stored modules persist in the warehouse item as `modules` (list of `Module` names), read as empty when missing.

## UI

- Opportunity panel: one "Deliver" button per ship of the viewer's in its delivery's destination Port.
- Fleet tab, in Investment, per ship in Port: "Store one X in the warehouse" for each legal working module when the
  viewer has a warehouse there with room; "Fit a stored X" for each module stored there; and, at a legal system 1–2
  Port, "Move one X to <ship>" for each military working module and each other ship of theirs in that Port.

## Phasing

One PR: domain and tests, then persistence and API, then the UI, then the brief (R114 amended, R121–R123).
