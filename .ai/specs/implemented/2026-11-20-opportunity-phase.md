# Opportunity phase

**Brief:** `.ai/specs/product-brief.md` — R26 (turn order, used by Ship-jumps), R44 (turn phases; "a production phase can
later be inserted before Investment"), R47–R48 (Collect), R61, R87 (actions "after their Collect"), D12 (Infrastructure),
D15 (the screen follows the phase).
**Source:** the creator's answers of 2026-11-20. Correction the same day: only factory output moves into the phase, ships
still leave the yard in Investment, and players act in turn order as in Ship-jumps.
**Used by:** `2026-11-20-sabotage.md`, `2026-11-20-agents.md`.
**Status:** proposed, not started.

## 📝 TLDR

A new phase, **Opportunity**, comes between Trading and Investment: Bidding → Ship-jumps → Trading → **Opportunity** →
Investment. Players act **one at a time in the turn order** (R26), as in Ship-jumps. Factory output (**Collect**) moves into
it from Investment. **Sabotage and agents** are played in it. Ships still leave the yard in Investment.

## 📝 Problem Statement

Sabotage and agents need a moment of their own. It has to come after the turn's trading, so the board is settled, and
before Investment, so a player can't react to a sabotage by buying a warehouse in time. The board game has the same
order: production (phase 6) comes before investment (phase 7), and R44 already left room for it. Acting in turn order
makes the sequence matter: an early player can sabotage a warehouse before its owner collects into it.

## 📋 Acceptance criteria

### The phase
1. **Order:** Trading closes into Opportunity, and Opportunity closes into Investment. `TurnPhase.OPPORTUNITY` is inserted
   between `TRADING` and `INVESTMENT`.
2. **Turn order:** players act one at a time in the turn's order (R26), as in Ship-jumps. On their turn a player:
   1. **collects** their factories' output (R47), if they have factories. This is required before anything else.
   2. may do any of the phase's actions (sabotage, agents), in any order.
   3. signals **"I'm done with opportunities"**, which passes the turn to the next player.

   After the last player is done, Investment starts.
3. **Planning while waiting:** a player who isn't on turn sees every form (Collect placements, sabotage target, agent moves)
   and can fill it in. Only the submit buttons wait for their turn (`Your turn comes after Vega Lines`), so they can plan
   ahead.
4. **Choices owed by other players:** some actions leave a choice to another player, such as placing hits after a ship
   sabotage. That player makes it at once, out of turn, from a notification. The phase doesn't close into Investment while
   any such choice is open, and the top bar says who it waits for.
5. **Every turn, the last one included:** the game still ends after the last turn's Investment.

### Collect moves here
6. **Collect** (R47, otherwise unchanged) happens on each player's own Opportunity turn, not at the start of Investment.
7. **Investment no longer contains Collect.** Its "after their Collect" conditions (R48, R61, R87) become "in Investment".
   R48's reasoning still holds: a warehouse or factory bought in Investment first matters at the next Opportunity.
8. **Leaving the yard is unchanged in when it happens** (creator, 2026-11-20): a ship bought in turn N moves to Port during
   turn N+1's Investment. It's no longer tied to Collect, so it happens for every player when Investment starts. A ship
   still in the yard during Opportunity can't be sabotaged, and agents can't board it.
9. **No warehouse can be bought during Opportunity** to catch output or cargo. Buying happens only in Investment.

### Screens
10. **Where it's played:** Infrastructure, following D15. When Opportunity starts, everyone is taken there. The page shows
    the Collect panel, the Sabotage card and the Agents card. The Investment panels are visible but disabled with
    `Opens in Investment`.
11. The Deck's top bar shows whose Opportunity turn it is, and the current player's "I'm done with opportunities" button,
    as in Ship-jumps.
12. The top bar, the phase list and the Game log name the phase **Opportunity**.

## 📝 Proposed Solution

Add the enum entry and reuse the Ship-jumps turn-order machinery: the current player and passing the turn. Move `Turn`'s
Collect bookkeeping from Investment to Opportunity. `Game.collectOutput` requires Opportunity and the caller's turn. Leaving
the yard runs for all players when Investment opens.

**Alternative considered:** a separate "Opportunities" screen. Rejected for now, because Collect already lives on
Infrastructure. If agents make the page crowded, D12 can be refined then.

## 📝 Architecture and data model

- `TurnPhase.OPPORTUNITY("Opportunity")`, between `TRADING` and `INVESTMENT`.
- `Turn`: `requireOpportunityTurnOf(email)`; the current Opportunity player from the R26 order, as in Ship-jumps; and
  `pendingChoices` (who owes what). `collected` moves to Opportunity.
- `Game.finishOpportunities(email)` passes the turn, and closes into Investment after the last player once no choice is open.
  Opening Investment moves waiting ships out of the yard (R63).
- `OpportunityService` (new): collect and finish. Sabotage and agents add to it.
- Web: `POST /api/games/{id}/opportunity/done`. The collect endpoint is gated on the new phase and turn.
- DynamoDB: a new phase value and attributes, no migration. A game mid-Investment when this ships finishes that Investment as
  before.
- Frontend: phase routing (D15), Infrastructure's phase-dependent panels, the turn indicator, and forms that can be filled
  in out of turn.

## 📝 Edge Cases

- **No player has factories:** Opportunity still runs, because sabotage and agents are available. A player with nothing to
  do signals done.
- **A player sabotaged before their own turn:** they collect into whatever is left, and a disabled factory produces
  nothing this turn.

## Defaults I chose (override any)

- It's played **on Infrastructure**, not on a new screen.
- Choices owed out of turn (hits, cargo) are made **at once**, and the phase waits for them before closing.
- Ships leave the yard **for everyone when Investment opens**, now that Collect isn't in Investment.

## 📋 Brief changes (with this spec)

- R44: Bidding, Ship-jumps, Trading, **Opportunity**, Investment.
- R47: Collect happens on the player's Opportunity turn.
- R48, R61, R87: "after their Collect" → "in Investment". R63: the ship leaves the yard when Investment opens.
- **R102** the Opportunity phase (turn order, Collect first, owed choices). **D24**.
