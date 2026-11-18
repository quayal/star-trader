# Connections: political, business and criminal

**Brief:** `.ai/specs/product-brief.md` — R43 (market position gain), R44 (turn phases), R46 (factories), D12 (Investment happens on Infrastructure), D11 (loans stay out).
**Source:** the creator's vault note `10-projects/Trader/Connections.md` and their answers of 2026-11-18.
**Related:** `implemented/2026-11-18-corporations-tab.md` (where connections are shown) and `2026-11-18-reputation.md` (connection rises move reputation).

## 📝 TLDR

Proposed: every player gets three connections, **political**, **business** and **criminal**, each from 0 to 10 and visible to everyone. Today a player has none. Each player starts with 3 + d3 points to place freely. Once per Investment they can raise one connection by one point, paying the new level × 10 HT. A rise in market position also adds a business point. From the start, connections cap factories: a player can own at most political + business units of factory in total. Criminal connections have no effect yet; later features such as smuggling will use them.

## 📝 Problem Statement

The original game gives players standing with institutions, and later features (smuggling, police, opportunities) depend on it. The factory cap is the first rule that uses connections, and it changes something already built: today, factory size is limited only by money (R46).

## 📋 Acceptance criteria

### Starting points
1. **Roll at start.** When the game starts, each player rolls 1d3 and gets 3 + roll points to place (4–6). This is logged publicly: `Vega Lines rolled 2: 5 connection points to place`.
2. **Placing them.** During turn 1's Bidding, each player places all their points across the three kinds in one action: any whole numbers that add up to the total, each from 0 to 10. The placement is final. It is logged publicly: `Vega Lines's connections: political 2, business 3, criminal 0`. It doesn't count as a "rise" for reputation.
3. **The gate.** A player can't signal done bidding in turn 1 until they've placed their points. The Deck's Bidding area shows the placement form until they do. Refusal: `Place your 5 connection points first.`

### Raising connections
4. **Buy one point in Investment.** On Infrastructure, once per Investment, a player may raise one connection by one point. It costs the new level × 10 HT (3 → 4 costs 40 HT).
   - Refused at 10: `Your political connections are already at 10.`
   - Refused when they can't pay: `Raising business connections to 4 costs 40 HT; you have 35 HT.`
   - Refused when they've already bought one this Investment: `You've already raised a connection this Investment.`
5. **The log.** A raise is logged publicly: `Vega Lines raised political connections to 4`. The cost isn't logged (money is private, R29), even though the level gives it away.
6. **Market position.** When a position roll moves a player up a tier (R43, including none → Retailer), their business connections rise by 1, up to 10. The position-roll line says so: `…: now Wholesaler of ISO at Mu Herculis; business connections rise to 4`. A demotion takes nothing away.

### Factory cap
7. **Total factory units** a player owns, across all systems and goods, can't exceed political + business. A purchase that would go over is refused, naming the limit as the creator worded it: `You can only own 7 units of factory (POL: 3, BUSS: 4).`
8. **On Infrastructure,** the factory form shows the room left, `Factory units: 5 of 7 (political 3 + business 4)`, and its maximum is the smaller of what the player can afford and what the cap allows.
9. **Nothing removes factories.** No rule lowers connections yet. If a later one does, the factories a player already owns stay, and they just can't buy more until they're back under the cap.

### Visibility
10. Each player's `connections` (`political`, `business`, `criminal`) and their unplaced `connectionPoints` are part of the public `players[]` view. The Corporations tab shows them as a "Connections" section: `Political 3 · Business 4 · Criminal 0`, plus `5 points to place` while that's true.

## 📝 Proposed Solution

A `Connections` value class (three kinds, 0–10, owning the raise rules and the cost) and an unplaced-points count on `Player`. Starting points reuse the Bidding phase rather than adding a setup phase: Bidding is already simultaneous and closes only when everyone is done (D09), so the gate costs nothing new in the phase engine.

**Alternatives considered:**
- A separate "Setup" phase before turn 1 (rejected: a new phase and screen for a one-time 10-second action).
- Placing points in the lobby (rejected: the roll happens at start, and lobby players can still leave).

## 📝 Architecture and data model

- `game/Connections.kt` (new): `political`, `business`, `criminal`, each 0..10; `raised(kind)`, `raiseCost(kind)`, `factoryCap()`, `placed(political, business, criminal, points)`.
- `game/ConnectionKind.kt` (new enum): POLITICAL, BUSINESS, CRIMINAL, each with its display name.
- `Player` gains `connections: Connections` and `connectionPoints: Int`. Its `buyFactoryUnits` checks the cap. `promotedAt` raises business.
- `Turn` gains `connectionRaised: List<String>` (who raised one this Investment), emptied like `doneInvesting`.
- `Game.start` rolls the d3 through `Dice`. `Turn.signalDoneBidding` in turn 1 checks the gate through `Player`.
- Web: `POST /api/games/{id}/connections/placement` with `{ political, business, criminal }`, and `POST /api/games/{id}/connections/raise` with `{ kind }`. Both refuse with 400 or 409 in the existing refusal style. `PlayerResponseDto` gains `connections` and `connectionPoints`.
- DynamoDB: new attributes on the player item, no migration. The game isn't published, so saved games aren't kept compatible (creator, 2026-11-18). A missing attribute loads as 0.
- Frontend: placement form on the Deck (turn 1 Bidding), raise form and factory-cap line on Infrastructure, section on Corporations.

## 📝 Edge Cases

- **A player who never places their points** holds turn 1's Bidding open, like any player who isn't done (D09).
- **The cap at game start.** Starting points total 4–6, so a player's cap is 0–6 depending on how they split them. Putting everything into criminal means no factories until they buy political or business. That's a legitimate choice, so the placement form says so: `Factories are capped at political + business.`
- **Business at 10.** A market position rise adds nothing, and the position-roll line doesn't mention connections.
- **Several raises in one Investment** are refused (criterion 4), including a second one of a different kind.

## Defaults I chose

- Placement happens during turn 1's Bidding, and is public as soon as it's made.
- Starting placement doesn't move reputation, because it isn't a "rise".
- A demotion doesn't lower business connections; the note only covers rises.

## Brief changes

- New rules: R81 (connections: three kinds, 0–10, public), R82 (starting points 3 + d3, placed in turn 1's Bidding), R83 (raise one point once per Investment for the new level × 10 HT; a market position rise adds 1 business), R84 (factory cap: total units ≤ political + business). R84 extends R46.

## 📋 Phasing

1. **Connections exist:** starting roll, placement, the gate, shown on Corporations.
2. **Raising:** buy in Investment, plus +1 business on a market position rise.
3. **Factory cap:** refusal and the Infrastructure line.

Every phase leaves factories working: phase 3 lands with phases 1 and 2 already in place, so every player already has political + business ≥ 0 and can raise them.

## 📋 Implementation Plan

1. `Connections` and `ConnectionKind` with unit tests (range, placing, raise cost, cap).
2. `Player.connections` and `connectionPoints`. `Game.start` rolls them, with the log line. Persistence round-trip test.
3. Placement endpoint, the turn-1 Bidding gate, the log line. Controller test.
4. DTO fields. Deck placement form. Corporations "Connections" section. JS tests.
5. Raise endpoint, once per Investment, refusals, log. Infrastructure form.
6. Business +1 on a position rise, with the position-roll log line.
7. Factory cap in `Player.buyFactoryUnits`, its refusal text, and the Infrastructure cap line and max.
8. Record R81–R84 in the brief. Check it in the browser with 2 players through turn 1 Investment.

## Implementation notes (2026-11-18, E12 run)

Added when the work shipped, without changing the plan above. Decisions the spec didn't cover:

- **Refusal texts** the spec didn't word: a bad placement is `400` "Enter a whole number from 0 to 10 for each connection." or "Your connections must add up to 5 points."; a second placement is `409` "You have already placed your connection points."; an unknown kind is `400` "Choose political, business or criminal connections.". A refusal at 10 or for money is `400`; once per Investment is `409`.
- **A raise waits for Collect**, like every Investment purchase (S03-5): `409` "Collect your output first.".
- **Position-roll line order:** the business rise comes right after the new tier and before any demotion: "…: now Dictator of POLY at Gamma Leporis; business connections rise to 4; Orion Freight is demoted to Wholesaler".
- **Where the rules live:** `Connections` (range, raise cost, market rise, cap and its text, placement), `Player` (placement once, the gate, raise with money, the cap check in `buyFactoryUnits`, business on `promotedAt`), `Turn.connectionsRaised` (once per Investment, emptied when Investment starts). `Game` only coordinates: the d3 at start, phase checks and the log.
- **Infrastructure** gets `you.invest.factoryUnits`, `factoryCap`, `connections` (level and the next point's cost per kind, null at 10) and `raisedConnection`, so the page computes nothing the server decides. The units line says "You can add up to N units here." (the smaller of money and cap).
- **Tests:** every test that starts a game now passes `START_DICE` (every d3 a 3), and those that go past turn 1's Bidding place the points with `ConnectionsPlaced(game).byEveryone()` (political 3, business 3: a factory cap of 6).

