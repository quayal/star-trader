# Run: E06-S03 — Collecting factory output at the start of Investment

Source doc: .ai/specs/2026-11-15-e06-factories-warehouses.md (on `main`)
Issue: #101 (epic #98)
Run type: autonomous (`om-auto-fix-issue`, feature route, one of two parallel forks; the other builds E06-S06, #104,
trading from a warehouse)
Based on: `origin/main` after E06-S01 (#108) and E06-S02 (#107).

## Asked to do

At the start of Investment, a player with factories places each factory's output into their warehouse at that system and
their ships in port there, and confirms. What doesn't fit or isn't placed is lost. They can't be done investing before
they've confirmed; a player with no factories goes straight on. The screen says what will be lost before they confirm,
and the game names who it waits for. No buying (S04, S05) and no changes to Trading (S06).

## What it did

- **Domain** (`game`): `Warehouse` gains `cargo` (units per good) and the same space rules as a ship (`cargoSpacesUsed`,
  `freeSpacesFor`, `unitsThatFit`, `load`; R42, R47); `Ship.freeSpacesFor` shares the wording. `OutputPlacement`
  (`system`, `goods`, `shipName` or null for the warehouse, `units`) and `InvalidCollectException` are new.
  `Player.collect(placements)` owns the rules: each placement names a factory the player has, a ship in port there or
  their warehouse there, and a factory's placements can't add up to more than it makes. `Turn.collected` (public, reset
  when Investment starts), `Turn.collectedBy` (once only), `Turn.isCollecting`. `Game.collectOutput` coordinates;
  `Game.signalDoneInvesting` refuses a player with factories who hasn't collected ("Collect your output first."), after
  the phase check so "Investment isn't open." still comes first. `InvestmentService.collect` runs it in `GameStore.update`
  (A04: a double press, or a replayed change, stores the output once).
- **Persistence**: `WarehouseItem.cargo` and `TurnItem.collected`, both defaulting to empty for older items (§7).
- **Web**: `POST /api/games/{id}/investment/collect`; `turn.collected` (public); `you.warehouses` (the owner's cargo,
  private) and `you.collect` (the factories to place, with each destination's free space, only while the player still has
  to). `players[].warehouses` still carries blocks and cargo spaces only.
- **Frontend**: a "Collect your output" panel on the Deck (s01) with one group per factory and an amount per
  destination, prefilled by filling destinations in order, a live loss warning, a check before sending, and the done
  button hidden (with a line saying why) until the player has confirmed. Pure helpers and tests in `js/investment.js`.
- **Docs**: BACKWARD_COMPATIBILITY.md §6 (endpoint, game shape, the new `409`) and §7 (rows for `warehouses`, `factories`
  and `turn.collected`, which E06-S02 hadn't added to §7).

## Decisions

- `OutputPlacement` names its destination by `shipName`, null for the warehouse: ship names are unique in a game and a
  player has at most one warehouse per system (R45).
- A placement above the destination's free space is accepted and the excess lost (R16), not refused: the screen warns
  first (S03-3). A factory's total above what it makes is refused.
- The done signal, not only building, is blocked until collecting: building doesn't exist yet (S04, S05), and letting a
  player finish without collecting would lose their output silently.
- Output is computed from the factories at confirm time. Nothing can be built before confirming (and nothing can be built
  yet), so that is the set owned at the start of Investment (S03-7, R48).
- The game log is not touched: collected output and builds are E06-S07.
- Warehouse and Factory still enforce no invariants (at least 1 block or unit, a producible good): nothing creates them
  yet, so S04 and S05 add them with the buying (from the E06-S02 review).
- No decision alert for Collect: it sits on the Deck, and the top bar names who the game waits for on every screen.

## Validation

- `./gradlew build` (ktlint, Kotlin tests with Docker up, JS tests): passes; 605 Kotlin tests, 0 skipped, JS tests pass.
- Browser walk (headless Chrome driven over the DevTools protocol, since agent-browser isn't installed; local app on port
  3131 with its own DynamoDB Local, `RESEND_API_KEY` blank; two players brought to Investment through the API, and one
  player's factories and warehouse seeded in DynamoDB Local because nothing can build them yet): the panel shows both
  factories with defaults 2/4/8; typing 6/0/5 shows "4 ISO at … will be lost (4 won't fit). 3 CHIPS at … will be lost (3
  not placed)."; the values survive a poll; 6 + 9 against 6 made shows the error; Confirm stores Wayfarer 4 ISO, the
  warehouse 2 ISO (beside its 8 POLY), Lantern 8 CHIPS; the panel goes, the done button appears with focus on it. The
  other player sees no panel and a done button at once. No horizontal overflow at 390 px. A first walk showed the first
  factory's rows wrapping unevenly in the narrow column; fixed with a CSS rule.
- Not seen: a screen reader, and a real mouse or touch (clicks were dispatched through the page).
- Not done here: the `om-auto-review-pr` stage (see the PR).

## Outcome

Done for the story's code and checks; the PR is open for review.
