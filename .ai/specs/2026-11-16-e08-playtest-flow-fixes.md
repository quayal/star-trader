# E08 — Playtest flow fixes: my offers in the Trading queue, moving goods, and following the phase

**Brief:** `.ai/specs/product-brief.md` — R17, R25, R29, R42, R45, R47, R49, D09, D12, plus the new R58 and D15 added with this spec.
**Source:** the creator's request of 2026-11-16, after reviewing the build: three things that get in the way of playing a turn.
**Siblings:** E05 Trading, E06 Factories and warehouses, E07 Hulls, modules and the Fleet tab.
**Prototype:** none. The new panel follows the design system (`docs/design-system.md`).

## 📝 TLDR

Three changes, each its own story:

1. **My offers in the Trading queue.** The first player in initiative orders the goods still to resolve (R25), but the Trading screen showed no offers until a good resolved, so they ordered blind. Each remaining good now shows the viewer's **own** offer for it. Nobody else's: offers stay secret until their good resolves (R17).
2. **Moving goods between a ship in port and a warehouse.** Output collected into a warehouse (R47) could only leave it by being sold there. A player can now move goods between one of their ships in port and their warehouse at the same system, in any phase, so what a factory made in turn 2 can be loaded and carried elsewhere in turn 3.
3. **The game follows the phase.** When the phase or the turn changes, every player is taken to the screen where that phase is played. This already happened for Trading, Investment and Game over; it now also happens for Bidding and Ship-jumps (the Deck), so nobody is left on Infrastructure when the next turn has started.

## 📝 Rules

| Id | Rule |
|---|---|
| R58 | **Moving goods.** A player may move goods between one of their ships **in port** and their own warehouse **at the same system**, in either direction, at any time while the game is running, whatever the phase and whoever's turn it is. A move is a whole number of units, 1 or more, of one good. It never loses goods: it is refused when the source holds fewer, or when the destination has too little free cargo space (POLY and ISO take one space per unit, CHIPS and FOOD none, R42). It is free, private like cargo (R29), and not logged. Ship to ship, and anything involving a ship that isn't in port, is not a move. |
| D15 | **The screen follows the phase.** When a poll or the player's own action shows that the phase or the turn has changed, the page opens the screen that phase is played on, once: Bidding and Ship-jumps → the Deck, Trading → Trading, Investment → Infrastructure, a finished game → Game over. Going elsewhere afterwards sticks until the next change. The first state a page load sees never moves the player. This generalises E05-S01-1, E06-S08-2 and E05-S08-2. |

Why a move during Trading is safe: a trade is capped by what the player holds at that system in ships in port **and** their warehouse there (R49), and a move keeps that sum.

## 📋 Stories and acceptance criteria

### E08-S01 — I see my own offers on the goods still to resolve

1. [R17, R25] On the Trading screen, each good in "Remaining goods" that the viewer has an offer on this turn shows it: "Your offer: Buy at 11 HT". It shows for every player, not only the one who orders the queue.
2. [R17] Only the viewer's own offers appear, read from `you.offers`. No response or screen carries another player's offer before its good resolves; nothing changes on the server.
3. [R25] The marks stay with their goods while the first in initiative reorders, by drag or by the arrows, and the arrows' names still say only the good.
4. A line under the list says what the marks are and who can see them; with no offer of the viewer's left in the queue it says so instead.
5. The marks use the shared `trade` classes, so Buy and Sell are told apart by the word, not the colour alone.

### E08-S02 — I can move goods between a ship in port and my warehouse there

1. [R58] `POST /api/games/{id}/cargo/moves` with `{ ship, goods, units, to }` (`to` is `WAREHOUSE` or `SHIP`) moves the units and answers with the game. It works in every phase of a running game.
2. [R58] Refused with `400` and a message the player can act on: a ship that isn't theirs, a ship that isn't in port, no warehouse at the ship's system, units that aren't a whole number of 1 or more, more units than the source holds, and more POLY or ISO than the destination has free space for. Nothing is ever lost.
3. [R42] CHIPS and FOOD always fit.
4. [R58] `409` before the game starts and once it is over; `403`, `404` and `401` as for the other actions.
5. [A04] The move runs inside `GameStore.update`, so a double press moves once or is refused, never duplicates goods.
6. [R29] Nothing about a move reaches another player: no log line, and their view of the game is unchanged.
7. [D12] The Infrastructure screen has a **Move goods** panel whenever the player owns a warehouse, in every phase: pick a ship (those in port at a system where they have a warehouse), the direction, the good and the units. It says how many units can move. With no such ship it says so instead of the form.
8. What the player picked and typed survives the poll, and belongs to one game (lesson 2026-11-04).
9. The Fleet tab links to the panel for a player who owns a warehouse.

### E08-S03 — The game takes me to where the phase is played

1. [D15] When Bidding closes and Ship-jumps starts, a player on any other screen is taken to the Deck, where the turn order and the jumps are.
2. [D15] When Investment closes and the next turn starts, a player on any other screen (usually Infrastructure) is taken to the Deck, where bids and offers are placed.
3. [D15] Trading, Investment and Game over keep opening their screens as before.
4. [D15] Opening the Deck this way moves focus to the heading of the panel the phase needs: "Bid for initiative" in Bidding, "Turn order" in Ship-jumps. The other screens focus their title, as they do today.
5. [D15] It happens once per change, never on the first state a page load sees, never for another game's state, and not when the player is already on the right screen.

## Defaults I chose (change on review)

- **Where the Move goods panel lives:** Infrastructure, next to "Your warehouses", because a move always involves a warehouse and that screen already lists what each holds. The Fleet tab only links there.
- **A move that doesn't fit is refused**, unlike a buy, whose overflow is lost (R16). Losing goods to a typo in a free, optional action helps nobody.
- **Not logged.** Cargo is private (R29), and a move changes nothing other players can see.
- **Allowed while a decision is pending** (a trade amount, a wholesale answer, a collect). It can't change what the player may trade, and it lets them make room before collecting.
- **Ship to ship is out.** The request was ship ↔ warehouse; two ships in port at one system can still swap through the warehouse there.
- **The phase pulls from every screen**, including the Game log and Fleet, as Trading already did.

## Added on 2026-11-17 (were "not in this spec")

### E08-S04 — The queue also marks the goods I'm Dictator of

1. [R38, R28] A good in "Remaining goods" that the viewer is Dictator of at that system, and so has no offer on yet, says "You're Dictator here: you place your offer when it resolves". It matters for the order as an offer does. Market positions are public, so nothing new is sent.
2. A good with an offer of the viewer's shows the offer, as before.

### E08-S05 — A step of mine on the Deck is announced on the other screens

1. [E05-S01-9] The decision alert, which already says "Your decision is needed" for Trading decisions on every screen but Trading, also says so on every screen but the Deck when it is the viewer's pick of a place in the turn order, or their jump slot, with a link to the Deck.
2. A ship waiting to land needs no alert: its dialog already opens on every screen (E04-S03).
3. Bidding and Investment get none: "done" is a readiness flag, and the top bar already says who the game waits for.
