# Corporations tab: everything public about every player, in one place

**Brief:** `.ai/specs/product-brief.md` — R28 (market positions are public), R29 (money is private), R50 (warehouses and factories are public), R57 (a ship's name and position are public; its hull, modules and cargo aren't), D12/D13/D18 (one tab per topic).
**Source:** the creator's request of 2026-11-18.
**Related:** `2026-11-18-connections.md` and `2026-11-18-reputation.md` add their values to this tab.

## 📝 TLDR

Proposed: a new **Corporations** tab, beside Deck, Infrastructure, Fleet and the Game log, shows everything public about every player in one place. That's each corporation's ships and where they are, market positions, warehouses and factories. Reputation and connections join when their specs ship. Today this information is spread across the map, the Trading screen and Infrastructure, and some of it, like other players' positions and factories, has no screen at all. Players keep asking "who owns what, where?". The data is already public and already sent to every browser, so this is a frontend-only change.

## 📝 Problem Statement

Each feature has added public facts about players: ships on the map (R57), market positions (R28), warehouses and factories (R50). Soon reputation and connections will be added too. No single screen answers "what does Kessler Cargo have?". A player who wants to plan a trade, a factory or an interception has to piece it together from the map and the log. The `players[]` part of the game view (`PlayerResponseDto`) already carries everything listed here, except reputation and connections.

## 📝 Proposed Solution

One tab with one card per corporation, built from `players[]`.

**Alternatives considered:** extending the Deck's side panels (rejected: the Deck is already the turn's working screen, and D15 sends players there in Bidding and Ship-jumps); a popover per corporation on the map (rejected: it only shows one corporation at a time and hides from keyboard users).

## 📋 Acceptance criteria

1. **Navigation.** A `Corporations` nav item sits after `Fleet` and before `Game log`. It shows only while a started or finished game is open, like Fleet. The screen gets its own URL, in the same scheme as Fleet's. The phase never sends players here (D15 is unchanged).
2. **One card per corporation**, in the order players joined, with the viewer's own card first and marked `(you)`. The card's heading is the corporation's name, with the player's name under it.
3. **Ships.** Each ship's name, system and location, e.g. `Wayfarer — Tau Ceti, Port`. A ship in a shipyard shows `Shipyard`. Sorted by name. Its hull, modules, crew and cargo are not shown, even for the viewer's own ships: those live on Fleet (R57). A corporation with none shows `No ships`.
4. **Market positions.** Each one as `Dictator — ISO at Mu Herculis`, sorted Dictator, Wholesaler, Retailer, then by system and good. A corporation with none shows `No market positions`.
5. **Warehouses.** System and capacity: `Tau Ceti — 3 blocks, 30 cargo spaces`. Contents are private and not shown. A corporation with none shows `No warehouses`.
6. **Factories.** Good, system and size: `ISO at Sigma Draconis — 4 u per turn`. A corporation with none shows `No factories`.
7. **Never shown:** money (R29) and anything private listed in criterion 3. Once a game is over, the existing standings already show money, and this tab doesn't repeat it.
8. **Live.** The tab refreshes on the same polling as the rest of the game, keeping the viewer's scroll position and focus (the same keyed-redraw pattern as the counter lines).
9. **Room to grow.** Every card has its sections in this fixed order: Reputation, Connections, Ships, Market positions, Warehouses, Factories. The first two appear when their specs ship. Until then nothing is shown for them: no empty placeholder rows.
10. **Design system.** The tab uses `docs/design-system.md` and existing tokens and classes, with no new colour literals. Goods names use their goods colours (R04.1), and tiers reuse the market-position styling where one exists.

## 📝 Architecture

- Frontend only: a new module `static/js/corporations.js` with pure functions (for example `corporationCards(game, viewerId)` returning plain data), unit-tested in `src/test/js/corporations.test.mjs`. Rendering lives in `index.html`, like the other screens.
- No API change: `players[]` already carries ships, market positions, warehouses and factories. `connections` and `reputation` are added to `PlayerResponseDto` by their own specs.

## 📝 Edge Cases

- A game still in its lobby: the nav item is hidden.
- A ship destroyed in battle simply disappears from the list on the next poll.
- A corporation with nothing at all still gets a card, with every section showing its "No …" line.
- Six players on a phone-width screen: cards stack in a single column with no horizontal scroll.

## Defaults I chose

- **Join order, you first.** Turn order changes every turn, and a fixed order is easier to scan.
- **No money, not even after the game.** Standings already cover it.
- **Your own ships are listed like everyone else's** (position only), so the card shows what others see. Fleet is where your own details are.

## Brief changes

- New decision D20: the Corporations tab holds every public fact about every player. A new public per-player fact belongs on it.

## 📋 Phasing

One phase: frontend only and independently shippable.

## 📋 Implementation Plan

1. `corporations.js`: `corporationCards` (order, `(you)`, ship, position, warehouse and factory lines, "No …" lines), test first.
2. The nav item and the screen in `index.html`: hidden outside started or finished games, a URL like Fleet's, keyed redraw.
3. `index-html.test.mjs`: the nav item exists, is in the right order and is hidden in the lobby.
4. Check it in the browser with 3 players, including a narrow viewport. Record D20 in the product brief.
