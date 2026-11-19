# Investing from the tables that show what you own

Date: 2026-11-19 · Source: creator request, 2026-11-19 · Status: in progress (browser-walked 2026-11-19)

## Ask (creator)

> Adding warehouses and factories in investment phase should take place on the actual grids/tables showing warehouses
> and factories (both on system view and infra view) (only active in infrastructure phase). The same should happen to
> connections and reputation (on corporation tab). Basically, in investment phase certain things can be active/possible
> on respective tabs.

## Decisions

- **No server change.** `you.invest` already carries every system with the viewer's blocks there, every producible
  (system, goods) with the viewer's factory size (0 = none), block and unit costs, the factory cap, the connection
  raise options and the reputation purchase. The server keeps deciding; the page only places the actions.
- **Warehouses and factories: a row action opens one shared dialog.** Each of the viewer's warehouse rows gets an
  "Add blocks" button, each factory row "Add units". While investing, the tables also list where the viewer could build
  and doesn't own anything yet, with a "Build" button (a warehouse can be built in any system; a factory for any good a
  system produces). The dialog holds the amount (a `.slider` from 1 to the most the viewer can afford, and for factories
  the cap, R84), the cost line, the error and Confirm/Cancel. One static dialog, so a poll redrawing the tables doesn't
  lose what is being typed. The slider starts at 1, not at the most (the Move goods rule), because here the most spends
  money.
- **Where:** Infrastructure's "Your warehouses" / "Your factories" tables, and System details' "Warehouses" /
  "Factories" tables (the viewer's own row, plus a "You" row to build, for that system only). Outside Investment, or
  while output is still to collect, the tables are as before, with no buttons and no build rows.
- **Connections and reputation on the viewer's own Corporations card.** In Investment, the Connections section shows a
  button per kind ("Raise Political to 4 — 40 HT"), disabled with the reason once one was raised this Investment, at
  10, or short of money (R83); the Reputation section a "Buy 1d6 — 20 HT" button, disabled with the reason once bought,
  at 40, or short of money (R87). One click is the purchase, as the old Raise and Buy buttons were. Results show in a
  message line under the Corporations title.
- **Infrastructure loses its separate Add blocks, Add units, Raise and Buy reputation panels.** It keeps the status,
  "I'm done investing", Collect, the factory cap line, the pointer to Fleet for ships, and gets a pointer to the
  Corporations tab for connections and reputation.

## Placement

UI-only: the rules stay on the server. Pure helpers (what rows and buttons to show, labels, amounts) go in the JS module
that owns the subject, unit-tested in `src/test/js/`:

| Behaviour | Data it reads | Where it goes |
|---|---|---|
| Infrastructure warehouse rows with Add/Build | `you.warehouses`, `you.invest.systems` | `investment.js` `ownWarehouseRows` |
| Infrastructure factory rows with Add/Build | `players[].factories`, `you.invest.factories` | `investment.js` `ownFactoryRows` |
| System details: the viewer's build rows | `you.invest` for one system | `investment.js` `systemInvestRows` |
| Most blocks / units the viewer can add | money, block cost, unit cost, cap | `investment.js` `maxBlocks`; `connections.js` `maxFactoryUnits` (exists) |
| Raise buttons per kind | `you.invest.connections`, `raisedConnection`, money | `connections.js` `raiseButtons` |
| Buy reputation button | `reputationPurchase` (exists) | `reputation.js` `BUY_LABEL` |
