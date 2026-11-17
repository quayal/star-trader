# Buy a ship on the Fleet tab

Status: shipped · creator’s request, 2026-11-17

## Why

The creator wants buying a ship next to the ships the player already has, so they can see their fleet while choosing
what to buy. Refit, repair and sell are already on Fleet (E09-S06-2); only buying sat on Infrastructure.

## What changes

- The "Buy a ship" panel (form plus hull comparison, E09-S06-1, R51, R61) moves from Infrastructure to the Fleet tab,
  under the player's ships. It is usable only while the player may invest (`canBuy`: Investment, output collected);
  the server still decides.
- Outside that, Fleet shows one line in its place: during Investment with output still to place, "Place your factories'
  output on Infrastructure first, then buy a ship here."; otherwise "You can buy a ship here during Investment."
- Infrastructure shows a link in its place while the player may invest: "Buy a ship on the Fleet tab".
- Refines D12 (everything in Investment on Infrastructure): ship buying, like refit and sell, is on Fleet. "Done
  investing" stays on Infrastructure.

## Placement (§13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| The buy form is usable only while the player may invest | `you.invest` (server-sent only then), phase | 9 | `canBuy` (exists) in `index.html`'s Fleet view |
| Which "not now" line Fleet shows | phase, `you.invest` | 9 | `buyClosedText(game)` in `js/shipyard.js`, unit-tested |

No server change: what a ship costs and whether it may be bought stay in Kotlin (`Player.buyShip`, `Ship.price`).

## Verification

- `js/shipyard.js` unit test for `buyClosedText`.
- `./gradlew check`.
- Browser walk: Fleet in Investment shows the ships and the buy form; buying a ship adds it to the list above;
  Fleet in another phase shows the note and no form; Infrastructure shows the link.
