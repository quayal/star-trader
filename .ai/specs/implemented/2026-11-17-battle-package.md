# Battle gets its own package, and owns its round

Status: shipped · Refactor, no behaviour change

## Why

- `game` is a flat package of 65 files. Battle (R70–R80), its largest concept, is spread through it: `Battle`, `Salvo`,
  `AttackTotal`, `Interception`, `InvalidBattleException`. Standard §1 says package by game concept.
- `Game.withRoundFired` resolves a battle round itself: it adds up hits per target, decides rocket kills and
  overwhelmed ships, builds `PendingHits` and the round's log lines, then hands the result to `Battle.afterSalvos`.
  Those are rules about the battle's own data, so under §8 they belong to `Battle`; `Game` should only coordinate
  (look up ships, roll through `Salvo`, remove destroyed ships from players, append the log).
- Smaller §8 slips nearby: `Game.withBattleMovedOn` reads `battle.stage` and `battle.done` to decide the battle just
  ended; `placeBattleHits` and `landAfterBattle` check the player's answer against the battle's lists in `Game`;
  `InvestFactoryResponseDto` and `Player` find a factory by reading `Factory.system`/`goods` in four places.

## Decisions

1. New package `dev.adriangrzebyk.trader.game.battle` with `Battle` (and its small types), `Salvo`, `AttackTotal`,
   `Interception`, `InvalidBattleException`. `HitPlacement`/`ModuleHit` stay in `game`: they describe damage to a
   `Ship`. `game` and `game.battle` import each other; that is accepted because both are inside the one `Game`
   aggregate, saved as a unit. No `internal`: in a single-module app it hides nothing.
2. `Battle.fired(salvos, hitsSurvivable): FiredRound` takes the round's salvos and, by ship name, the hits each fighting
   ship can absorb: values, not `Ship`s, so `Battle` doesn't take another part (CODE_REVIEW). `FiredRound(battle,
   destroyed, log)` is what `Game` applies. `afterSalvos` becomes private.
3. `Game` still fires the salvos (`Salvo.fire(firer, target, …)`): that needs ships from two players and the dice, which
   is coordination.
4. `Battle.hitsToPlace(email, placed)`, `Battle.landedBy(email, placed)` and `Battle.isJustOver()` take over the checks
   `Game` made on battle data.
5. `Factory.isFor(system, goods)` and `Player.factoryAt(system, goods)` replace the four field-by-field lookups; the DTO
   reads the market price once.
6. The browser's own "you can't afford it" check on the factory-units form stays: it is S05-4's confirmed
   instant feedback, and the server still decides.

Persistence (`BattleItem`, `InterceptionItem`) maps field by field and is unchanged apart from imports; no stored
attribute changes, so `BACKWARD_COMPATIBILITY.md` is unaffected.

## Verification

- New `game/battle/BattleTest` covers `Battle.fired` directly: hits added up per target, rocket kill vs holds, a ship
  destroyed by hits it can't absorb, a salvo at a ship breaking away missing, the log lines.
- `GameBattleTest` passes with only import changes: the proof that behaviour didn't change.
- `./gradlew check` (ktlint, Kotlin tests, JS tests).
