# Battle: rockets from modules only, and a results dialog after each round's salvos

Date: 2026-11-17 · Status: in progress · Follows `.ai/specs/2026-11-17-e10-battle.md`

## Why

The creator's corrections after trying E10:

1. **Rockets come only from modules.** A rocket salvo's firepower is the sum of the working modules' rocket values. The
   hull's weapons and modules without a rocket value add nothing to it (R75 used to add both).
2. **A salvo rolls 1d6, not 2d6.** The vault note was wrong: a salvo is weapons (or rockets) + d6 against the
   target's shields, read on the same battle table. The log reads "6 + d6 4 = 10 vs shields 2: 1 hit".
3. **Every player gets a result window after each round's salvos**, e.g. "You hit Lantern with rockets for 4 damage,
   ship destroyed." or "You hit Lantern for 4 damage, wait for Star Dust to apply damage to ship."

## Decisions

- With d6, an unarmed trading ship barely hurts anyone (a starting Clarinet tops out at 12: 1 hit on shields 2). That
  is intended: military ships and ships loaded with weapon modules are the killers, not traders (creator,
  2026-11-17). The battle table stays as it is.
- R75 now reads: with rockets, the salvo's firepower is the working modules' rocket values only. A ship with no
  working rocket module can't fire rockets: the server refuses the order, and the order form only offers "Use rockets"
  to a ship that has some.
- Battle rounds fire together (R74), so the window comes after each **round**, not each single salvo. It lists every
  salvo that concerns the viewer: those their ships fired and those fired at their ships.
- The outcome of each salvo is decided on the server: `BROKE_AWAY` (the target broke away, so it missed), `NO_DAMAGE`,
  `DESTROYED` (a rocket kill, or more hits than the ship survives), `AWAITING_PLACEMENT` (the target's owner hasn't
  placed the hits yet), `PLACED`, and `DESTROYED_WHEN_PLACED`. Once the owner places the hits, the report updates, so a
  firer who reopens the game sees "Star Dust applied the damage" or "ship destroyed".
- The report lives on the `Turn`, not the `Battle`, so it survives the battle ending, including the case where the
  last ships destroy each other and the battle is gone at once. It is replaced by the next round's report, cleared
  when a new battle starts, and gone with the turn.
- It is sent under `you.battleReport` with only the viewer's salvos. Nothing in it is secret (the log already has
  every salvo, R78); the filter just keeps the window about the viewer.
- The browser shows it as a modal `<dialog>` with an OK button. A line the viewer has dismissed isn't shown again
  (kept per tab in `sessionStorage`); a new line, such as the firer learning the damage was applied, reopens it.
  For the target's owner the line doesn't change on placement, so placing their own hits doesn't reopen it.

## Placement (engineering standards §13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| A salvo rolls one d6 | the dice | 2 | `Salvo.fire` through the `Dice` port |
| Rocket firepower is the working modules' rocket values only | the ship's modules and damage | 4 | `Ship.firepower` |
| A ship without rocket weapons can't fire rockets | the ship's modules | 4 | `Ship.checkCanFireRockets`, called by `Game.giveBattleOrders` (row 7) |
| Each salvo's outcome after the round | the round's salvos, the battle's ships and what was destroyed | 5, 8 | `Battle.fired` builds a `BattleReport` of `SalvoResult`s, returned in `FiredRound` |
| Placing hits settles the outcome | the report's results | 4 | `BattleReport.hitsPlaced(ships, destroyed)` |
| The report is kept for the turn | the turn | 4 | `Turn.battleReport`, `Turn.withBattleRound` |
| Which salvos concern a player | the report's results | 4 | `BattleReport.reportFor(email)` |
| The sentence shown | the DTO | 9 | `battle.js` formats it with corporation names |

## Out of scope

- Animations or per-salvo dice detail in the window (the log has it).
