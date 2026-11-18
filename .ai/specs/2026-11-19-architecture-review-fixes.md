# Architecture review fixes

- Date: 2026-11-19
- Status: ready for implementation
- Branch / worktree: `refactor/architecture-review-fixes` in `../trader-arch-fixes`
- Source: `.ai/analysis/2026-11-19-architecture-review.md`. The creator asked for all of its findings to be fixed
  (2026-11-19).
- Delivery: one PR, one commit per phase, in the order below. Every phase leaves `./gradlew check` green and the HTTP
  API unchanged. Phase 7 is the only one that can be dropped from the PR if it grows (see its note).

## Goal

Fix the review's findings without changing any game behaviour, HTTP response or stored item:

| Phase | Finding | What changes |
|---|---|---|
| 1 | 4 | Delete the dead first-attempt trading code |
| 2 | 4 | Rename what's left of `trading` to `board` |
| 3 | 5 | Add a Konsist test for the package boundaries |
| 4 | 1 | Decide what each viewer may see in the domain (`Game.viewFor`), not in the DTOs |
| 5 | 2 | Make `Game.kt` smaller: log text and step checks move to the parts that own them |
| 6 | 6 | Resolve the session once, with an argument resolver |
| 7 | 3 | Give `Turn` a per-phase state, with no change to how turns are stored |

## Non-goals

- No change to any rule, dice roll, log line, error message or status code. Log text and `NotYourTurnException`
  messages must stay **byte-identical**: the existing `Game*Test`, `GameControllerTest` and `WholeGameTest` assertions
  are the oracle.
- No change to any JSON field (`GameResponseDto`, `/api/options`) or to any DynamoDB attribute.
- `Game` is not split into several aggregates (the review says why).
- The app-service layer (`*Service`) stays as it is.

## Before you start (every phase)

1. Follow `.claude/skills/trader-implement/SKILL.md`: read `.ai/engineering-standards.md`, `CODE_REVIEW.md` and
   `.ai/lessons.md`, and run the diff audit in its §3 before each commit.
2. Check `pwd` and `git status`: you work in `../trader-arch-fixes` on `refactor/architecture-review-fixes`.
3. Verify with `env -u AWS_REGION ./gradlew check`, with Docker running so the DynamoDB and controller tests aren't
   skipped. Phase 6 also needs a browser smoke test: log in and poll a game.
4. A pure move is proven when the existing tests pass with only import and package changes. If a test needs more than
   that, stop and work out why before going on.

---

## Phase 1: delete the dead trading code

**Why.** About 13 classes in `trading` are referenced only by each other. Most notably, `RandomDiceRoller` is a live
`@Component` that implements a second dice port next to `game.Dice`. Agents copy whatever code they find.

**Delete these from `src/main/kotlin/dev/adriangrzebyk/trader/trading/`:**
- `TradeOfferBook.kt`, `TradeOffer.kt`, `TradeDesk.kt`, `BestOfferCalculator.kt`, `TransactionAmountCalculator.kt`,
  `TradeResult.kt`, `DiceRoller.kt`, `Player.kt`, `Loan.kt`, `MarketPosition.kt`, `CompetitivePosition.kt`,
  `InvalidTradeOfferException.kt` (delete it if nothing else uses it once the rest is gone)
- `adapter/memory/` (`InMemoryTradeOfferBook.kt`, `TradeOfferSequence.kt`) and `adapter/random/RandomDiceRoller.kt`

**Delete these from `src/test/kotlin/dev/adriangrzebyk/trader/trading/`:** `BestOfferCalculatorTest.kt`,
`TradeDeskTest.kt`, `TransactionAmountCalculatorTest.kt`, `FakeDiceRoller.kt`, and anything under `adapter/memory` and
`adapter/random` that tests the deleted classes.

**Keep:** `StarSystem`, `Goods`, `OfferType`, `SupplyDemand`, `LocationStats`, `SDIndicator` (delete it if it has no
user; grep first), `TradeOfferController` and `TradeOfferDtos`.

**Change:**
- `TradeOfferController` takes its price bounds from `game.Offer.MIN_PRICE`/`MAX_PRICE`, which hold the same values
  (1 and 20).
- Check any Spring wiring or `@Bean` that referred to the deleted classes.

**Docs, in the same commit:**
- `BACKWARD_COMPATIBILITY.md` §1: remove `TradeDesk` / `BestOfferCalculator` / `DiceRoller` from the protected rules
  and say they were deleted as dead code on 2026-11-19, at the creator's request. No game used them, so no game's
  outcome changes.
- The product brief's citations of these files (R03, R08, R09) are history: leave them.

**Done when:** `grep -rwE 'TradeDesk|TradeOfferBook|DiceRoller|BestOfferCalculator' src` returns nothing, `/api/options`
gives the same JSON as before (`TradeOfferControllerTest`, or add a test if none covers it), and `check` is green.

## Phase 2: rename `trading` to `board`

**Why.** What's left of `trading` is the board's fixed catalogue (systems, goods, offer types, S/D), and `game`
imports it about 75 times. It's shared by everything that plays on the board; trading is only one of its users.

**Steps:**
1. Move `trading/{StarSystem,Goods,OfferType,SupplyDemand,LocationStats}.kt` and their tests (`StarSystemTest`,
   `SupplyDemandTest`) to `dev.adriangrzebyk.trader.board`, using IntelliJ-style package moves. In practice that means
   `sed` over the imports followed by a compile.
2. Move `TradeOfferController` and `TradeOfferDtos` to `game/adapter/web/`, and rename the class to
   `BoardOptionsController`. **Keep the JSON class names and fields exactly as they are.** It reads `Offer` (from
   phase 1), so it can't live under `board` without `board` depending on `game`. The URL stays `GET /api/options`.
3. Update the package references in `.ai/engineering-standards.md` (§1 example `trading`), `CODE_REVIEW.md`,
   `BACKWARD_COMPATIBILITY.md` §1–2, `docs/` and `ktlint-rules/src/test/.../FunctionsInClassesRuleTest.kt` if its
   fixtures name the real package.
4. `trading/` should now be empty: delete the directory.

**Done when:** there is no `dev.adriangrzebyk.trader.trading` anywhere in `src/` or `ktlint-rules/`, and `check` is
green.

## Phase 3: Konsist boundary test

**Why.** Standards §1–§2 (the domain imports no framework; adapters point inward) hold today, but only by habit. The
"Not decided yet" item that postponed Konsist is lifted by the creator's request.

**Steps:**
1. Add `testImplementation("com.lemonappdev:konsist:<latest stable>")` to `build.gradle.kts`. Pin an exact version;
   check Maven Central for the newest one.
2. Add `src/test/kotlin/dev/adriangrzebyk/trader/ArchitectureTest.kt` (plain JUnit, no Spring) with these rules:
   1. No file in a non-`adapter` package under `game`, `game.battle`, `login` or `board` imports `org.springframework`,
      `software.amazon`, `com.fasterxml` or `jakarta`.
   2. No file outside an `..adapter..` package imports from an `..adapter..` package.
   3. `board` imports nothing from `game` or `login`. `game`'s domain imports nothing from `login` (its web adapter
      may, for `SessionStore`).
   4. `web` (the top-level Spring config package) is exempt from rule 1.
3. Test the test: add a temporary Spring import to a domain file, watch the test go red, then revert.
4. Update standards "Not decided yet": move the Konsist bullet into a new §14 "Boundaries are tested". It names
   `ArchitectureTest` and its rules and says a new package or adapter kind extends the test. Credit: creator,
   2026-11-19.

**Done when:** `ArchitectureTest` passes, and fails on a planted violation.

## Phase 4: `Game.viewFor(email)` decides what a viewer sees

**Why.** Confidentiality (R17, R29, A05, standards §10) is decided today inside `YouResponseDto`, `GameResponseDto`
and `BattleDtos`, which breaks §13 row 1. Each confidential feature has been adding another `takeIf` there.

**Placement (§13):**

| Rule | Reads | §13 row | Goes to |
|---|---|---|---|
| A viewer's trade max is shown only while the counter waits for their offer | `Resolution.awaitingTrade`, `Player.tradeMax` | 7 | `Game.viewFor` looks up and passes the values; `Resolution.awaitingTradeOf(email)` decides |
| The wholesale max is shown only to the wholesaler being asked | `Resolution.awaitingWholesaler` | 4 | `Resolution.isAskingWholesaler(email)` |
| Collect is shown while collecting, to a player with factories | `Turn.isCollecting`, `Player.hasFactories` | 7 | `Game.viewFor` |
| The interception question goes only to the players asked | `Interception.asked` | 4 | `Interception.asks(email)` |
| The battle panel goes to a lander or to the owner of pending hits | `Battle.landers`, `Battle.pendingHits` | 4 | `Battle.involves(email)` |
| A viewer sees only their own ships that may break away, and their own pending hits | `Battle` | 4 | `Battle.breakAwayShipsOf(email)`, `Battle.pendingHitsOf(email)` |
| Shipyard lists only hulls, modules and systems that are for sale | enum entries | 4/6 | `Hull.forSale()`, `Module.forSale()`, `StarSystem.shipyards()` (companion factories that list their own type) |

**Steps:**
1. Test first. Create `game/PlayerViewTest.kt` (plain JUnit). For each row above, add a case where the viewer gets
   the data, plus the §10 case where **another player in the same game doesn't**. Copy the existing confidentiality
   cases from `GameResponseDtoTest` as the starting fixtures.
2. Add `game/PlayerView.kt`: a data class holding the viewer's `Player` and everything `YouResponseDto` takes today,
   already decided. That means `offers` (own), `bid` (own), `tradeMax: Int?`, `wholesaleAsk: Resolution?`,
   `collecting: Boolean`, `investing: Boolean`, `interception: Interception?` (only when asked), `battle: Battle?`
   (only when involved), `battleReport: BattleReport?` (`reportFor(email)`), `raisedConnection`, `boughtReputation`,
   `freeShipNames`, `prices`.
3. Add `Game.viewFor(email): PlayerView?`, which is null for a non-member. It replaces the inline block in
   `GameResponseDto`'s private constructor (`GameDtos.kt` ~88–104).
4. Change `YouResponseDto` to a constructor `YouResponseDto(id, view: PlayerView)` that only maps fields, with no
   `takeIf`, `filter`, `in` or `any`. Do the same in `BattleDtos.kt` ~138–140 and in the shipyard lists in
   `GameDtos.kt` ~639–641.
5. Delete the KDoc contract "the caller passes `turn.offersOf(player.email)`…": the type now enforces it.
6. Run the trader-implement adapter grep: `git diff … 'src/main/**/adapter/**/*.kt' | grep takeIf|filter|…`. It must
   find nothing in the lines you added. The lookups by name in `BattleDtos.kt` ~65–70 can stay: they find a ship by its
   name and make no decision.

**Done when:** `PlayerViewTest` covers every row, including its negative case. `GameResponseDtoTest` and
`GameControllerTest` pass unchanged (the JSON is the same), and no DTO in `game/adapter/web` contains a condition over
domain fields.

## Phase 5: make `Game.kt` smaller

**Why.** `Game.kt` is 1,117 lines and is touched by 23 % of commits. Most of its size is English log text and
"whose step is it" checks, which belong to the parts.

**5a. Corporation names as a value.** Log text needs email → corporation name, which is the main reason it sits in
`Game`. Add `game/CorporationNames.kt`, a class that wraps `Map<String, String>` and has `of(email)` and
`list(emails)` (joined with ", " and " and ", as `gameOverText` does now). Add `Game.corporationNames()`. Parts take
this value, not `Player`s (§13 row 5).

**5b. Move log text to the class whose data it describes:**

| Today in `Game` | Moves to |
|---|---|
| `disclosure(resolution)` | `Resolution.disclosure(names)` |
| `resolutionLog(turn, before, resolution)`: the started, trades, ended and wholesale lines | `Resolution.logSince(before, names): List<ResolutionLine>`. `Game` turns each into a `LogEntry` with the turn number, and the trade records stay attached. |
| `waitingForTrader`, `waitingForDictator`, `waitingForWholesaler` | `AwaitedTrade.waitingText(names)`, `Resolution.waitingForDictatorText(names)`, `Resolution.waitingForWholesalerText(names)` |
| the text half of `positionRollLines` | `PositionRoll.logText(trader, units, good, names)` (the trader as a value). The reputation lines stay in `Game` because they need `Player.reputationLog`, which is coordination. |
| `gameOverText` | `CorporationNames`, given the winners' emails (`names.winText(winners)`) |
| initiative and tie text in `closeBidding` | `Turn`/`InitiativeRoll`, which already have `logText`: finish the move |

**5c. Step checks move into `Turn`.** `wholesalerTurnOf`, `dictatorTurnOf`, `traderTurnOf`, `resolverTurnOf` and
`jumpSlotOf` all follow the same pattern: check membership, take the running turn, compare an email that `Turn`
already computes, and throw `NotYourTurnException(text)`. Give `Turn` one method per step that returns the email it
waits for. Most exist already: `wholesalerToDecide`, `dictatorToDecide`, `tradeAwaited().offer.playerEmail`,
`firstInInitiative`, `currentJumper`. Replace the five functions with one private helper in `Game`:
`turnAwaiting(email, awaited: String, waitingText: () -> String): Turn`. Write the text with 5b's methods.

**Steps:**
1. Test first. Unit-test each text-producing method on its own class (`ResolutionTest`, `PositionRollTest`,
   `CorporationNamesTest`, `AwaitedTradeTest`) with the **exact strings** that the `Game*Test` files assert today.
2. Move one block at a time and run the `Game*Test` files after each move.
3. The trader-implement audit: `Game.kt` must have shrunk. Note in the PR its line count before and after.

**Done when:** `Game.kt` is at most about 850 lines (target, not a hard gate), no `"…"` log template remains in `Game.kt` except
for coordination lines that name several parts (e.g. "has no ship to jump"), and every log and error string is
unchanged (the existing tests pass with no assertion edits).

## Phase 6: resolve the session once

**Why.** Each of `GameController`'s 38 endpoints takes an `Authorization` header and calls `sessionFor`, and
`LoginController` parses the bearer token again.

**Steps:**
1. Add `login/adapter/web/SessionArgumentResolver.kt`, a `HandlerMethodArgumentResolver` for a `Session` parameter.
   It parses `Bearer <token>` (case-insensitive, trimmed) and returns `sessionStore.find(token)`; when that fails it
   throws `ResponseStatusException(UNAUTHORIZED)`, exactly as `sessionFor` does now. Register it in a
   `WebMvcConfigurer`.
2. Replace `@RequestHeader(AUTHORIZATION) authorization: String?` + `sessionFor(...)` with a `session: Session`
   parameter in every endpoint, and delete `sessionFor` and `BEARER_PREFIX`.
3. `LoginController`'s `GET /login/session` uses it too, so it keeps the same 401 for a missing, malformed or unknown
   token (`BACKWARD_COMPATIBILITY.md` §3).
4. Check that `GamePollEtagConfiguration` (the 304 path) still sees the request the way it did. Its ETag must still
   depend on the viewer.

**Done when:** the existing 401 tests in `GameControllerTest` and `LoginControllerTest` pass unchanged, no
`HttpHeaders.AUTHORIZATION` is left in `GameController`, and a browser smoke test works: log in, create and join a
game, poll it, place an offer.

## Phase 7: per-phase state in `Turn`, stored as before

**Why.** `Turn` has 22 flat fields. Rules like "empty outside Investment" and "while either is set, nobody jumps" are
enforced only by KDoc and method checks.

**Recommendation (from the review).** Do it last, and drop it from this PR if it grows past one focused commit. Its
trigger (a stale-phase bug, or a 5th phase) hasn't fired yet. What keeps it cheap is that the stored shape does not
change.

**Design:**
- `sealed interface PhaseState`, with `Bidding(doneBidding)`, `ShipJumps(turnOrderPicks, jumpedShips, doneJumping,
  landingShip, interception, battle)`, `Trading(tradingQueue, resolvedGoods, resolution)` and `Investment(doneInvesting,
  collected, connectionsRaised, reputationBought)`.
- `Turn` keeps the fields that live for the whole turn: `number`, `offers`, `bids`, `initiative`, `initiativeRolls`,
  `interceptionTries`, `battleReport`, plus `state: PhaseState`. `phase` is derived from `state`.
- **Before you move a field, grep every read of it.** A field read after its phase (e.g. `resolvedGoods` in
  Investment, or `turnOrder()` in the DTO during Trading) stays on `Turn`. Write your final split in this spec before
  you write code.
- **Storage:** `TurnItem` keeps the same flat attributes. It writes the fields of the current state and writes
  empties for the others, which is what's stored today; it reads them back into the right `PhaseState` from the
  `phase` attribute. Old items therefore load unchanged. `TurnItemTest` must pass, with the round-trip cases rebuilt
  through the new constructor, and you must add one case that reads an item **saved by the current code** (copy a
  fixture map from the existing test).
- Phase transitions (`closeBidding`, `endTrading`, `next()`…) build the next phase's state and so drop the old one by
  construction.

**Done when:** no KDoc says "empty outside X" (the type says it), `TurnItemTest` and the old-item case pass, and every
`Game*Test` passes.

---

## Finish

- Run the trader-implement §3 audit over the whole branch diff and put its findings, or "none", in the PR body.
- `.ai/lessons.md`: add an entry saying that a viewer-visibility rule goes in `Game.viewFor`/`PlayerView`, not in a
  DTO.
- Add a row to standards §13 and an example: "Is it about what one player may see? → `PlayerView` via `Game.viewFor`".
- Open the PR against `main` ("refactor: architecture review fixes"), with one commit per phase. Its body lists the
  phases, the `Game.kt` line counts, and links this spec and the review. When it has merged, move this spec to
  `.ai/specs/implemented/`.
