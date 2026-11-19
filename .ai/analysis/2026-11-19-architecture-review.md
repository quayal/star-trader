# Architecture review: Star Trader, 2026-11-19

Mode: review of the existing design (skill `software-architecture`, mode B). Covers the backend at `152bf6d` on `main`.
No ADRs written yet: a finding gets one only once the creator accepts it.

## Verdict

The architecture fits what the project is trying to do. It's one deployable with polling and one consistent aggregate
per game, and it should stay that way. The problems come from how it was built, not from the patterns chosen. Three
cost something now:

1. The secrecy rules that decide what each viewer may see are written in the web DTOs.
2. `Game.kt` is the one place every feature passes through, and much of what it holds is log text.
3. About a dozen dead classes from the first attempt are still in `trading`, including a second dice port that is a
   Spring bean.

All three can be fixed one small PR at a time. None of them needs a change of pattern.

## Drivers (recovered, ranked)

| # | Driver | Evidence |
|---|---|---|
| 1 | **Modifiability**: rules change and get superseded almost daily | 212 commits touching `src/main` since 2026-10-01 `[evidence]`; product-brief rules superseded repeatedly (R08→R24→R34, R18→R25…) |
| 2 | **Correctness and testability of game rules** | Standards §6, §7, §13; 50+ domain test files with no Spring `[evidence]` |
| 3 | **Confidentiality** of offers, bids, money and cargo | R17, R29, A05, standards §10 |
| – | Scale and performance: low | 2–6 players per game, polling at 1–2 s (D01), a solo creator and agents, a local or ngrok playtest (D10) |

Team: one human plus AI agents. Agents copy whatever precedent the code shows, so dead code and misplaced rules
spread faster here than they would in a human team `[inference]`.

## As-is

- Spring Boot modular monolith. The top-level packages are `game` (with `game.battle`), `login`, `trading` and `web`.
  The frontend is static vanilla JS: 26 files, about 3.6 k lines.
- `Game` is the aggregate root, about 40 public commands. Each one goes controller → `*Service` →
  `GameStore.update(id) { it.command(...) }`.
- Storage is DynamoDB: one item per game plus one item per turn of the log. Writes use optimistic versioning with a
  retry, in a transaction that also covers the log items. Reads are consistent (`DynamoDbGameStore`).
- Each poll builds a per-viewer projection in `GameResponseDto(game, viewerEmail)`.
- Sizes: `Game.kt` 1,117 lines, `GameDtos.kt` 1,139, `GameController.kt` 705 (38 endpoints), `Player.kt` 602,
  `Turn.kt` 492.
- Hotspots: `Game.kt` and `GameDtos.kt` are each touched by 48 of the 212 commits (23 %) since 2026-10-01 `[evidence]`.

## Fit verdict per decision

| Decision | Verdict | Why |
|---|---|---|
| Single deployable, static client, polling (D01, R05) | **Fits** | No driver calls for distribution or push. |
| One `Game` aggregate saved as a unit | **Fits** | A turn-based game for at most 6 players has little contention, and the rules span players inside a turn (Vernon: the aggregate is the true consistency boundary). Splitting it would bring cross-aggregate transactions in exchange for nothing. |
| Optimistic version, retry, transactional log items | **Fits, well executed** | Meets §9 and A04. The log is split out because of the 400 KB item limit. |
| Ports with fakes (`GameStore`, `Dice`, `Clock`, sender) | **Fits** | Each port has a real second implementation (a fake in tests, DynamoDB Local) and fast tests. |
| Rich model, with each part owning its rules (§8, §13) and ktlint rules enforcing it | **Fits** | A rules-heavy core calls for a Domain Model (Fowler PoEAA). The custom ktlint rules work as fitness functions. |
| Application services | **Slightly over-engineered, low cost** | The methods only forward calls (e.g. `TradingService`, 9 of 9). They do name the transaction boundary, so it's cheap to keep them. |
| Per-viewer projection in the web adapter | **Misapplied** | Finding 1. |
| `Game` as the entry point for everything, including the log text | **Misapplied (how it was built)** | Finding 2. |
| `Turn` as a flat record of every phase's state | **Under-modelled, not urgent** | Finding 3. |

## Findings (ranked)

### 1. High: what each viewer may see is decided in the web DTOs
- **Evidence** `[evidence]`. `YouResponseDto`'s constructor (`game/adapter/web/GameDtos.kt` ~519–551) decides
  visibility with code like `takeIf { it.offer.playerEmail == player.email }`, `player.email in it.asked` and
  `battle?.takeIf { player.email in it.landers() || … }`. The rule that offers and bids must be the viewer's own is
  written only as a KDoc contract on the caller ("the caller passes `turn.offersOf(player.email)` … never the whole
  turn's").
- **Driver:** confidentiality (#3) and testability (#2). Standards §13 row 1 says that a `takeIf`/`filter` over domain
  fields inside a DTO means a business rule has got into the adapter.
- **Reference:** Evans, *DDD*: domain rules don't live in the presentation layer. Also OWASP API1 (object-level
  authorization), which is decided where the data is assembled.
- **Fix:** add a domain projection, `Game.viewFor(email): PlayerView`, holding the viewer's own offers, bid, what they
  are asked (interception, battle orders), their trade max and so on. `YouResponseDto(PlayerView)` then only maps it.
  The "another player can't see it" tests (§10) become plain JUnit tests against `viewFor`.
- **Cost / benefit:** one medium PR, with no change to the API shape. Each new confidential feature then adds its
  rule in one tested place instead of one more `takeIf` in a DTO. Living with it: each epic adds another visibility
  rule in the adapter, with coverage only from the 2,689-line Testcontainers `GameControllerTest`. I didn't find a
  leak. This finding is about where the rule lives and the risk that creates.

### 2. High: `Game.kt` is the hotspot, and much of its bulk is log text and per-step guards
- **Evidence** `[evidence]`. The file is 1,117 lines and is touched by 23 % of commits. It has 33 `LogEntry.event`
  calls and about 107 string literals of English log text: `gameOverText`, `disclosure`, `waitingForTrader`,
  `resolutionLog`, `positionRollLines`. Around 1,000 lines are spent on step guards: `traderTurnOf`, `dictatorTurnOf`,
  `wholesalerTurnOf`, `resolverTurnOf`, `jumpSlotOf`.
- **Pattern or execution:** the problem is in the execution. The aggregate boundary is right (see above). The §5 rule
  "a function over a `Game` is a method of `Game`" sends anything that coordinates parts into this one file, so the
  file grows with every feature `[inference]`.
- **Reference:** Tornhill, *Your Code as a Crime Scene*: churn × size hotspots come first.
- **Fix, in order of value ÷ risk:**
  1. Move the log text out of the decisions and into the parts and result types that already produce it. The
     project already does this in `InitiativeRoll.logText`, `Ship.landingLog` and `Player.reputationLog`. Doing the
     same for `Resolution` (the disclosure, trade and wholesale lines) and `Battle` takes out the largest block.
  2. Move "is it this player's step?" guards that read only `Turn` data into `Turn`, e.g. `Turn.awaitingTrader(email)`
     returning the awaited trade or throwing. Some of this exists already (`Turn.tradeAwaited()`).
- **Not recommended:** splitting `Game` into several aggregates (there's no contention driver for it), or
  "phase coordinator" classes that take a `Game`, which §5 forbids and would only move the code elsewhere.
- **Trigger to revisit:** `Game.kt` growing past about 1,300 lines, or its share of commits passing 30 %.

### 3. Medium (not yet): `Turn` keeps every phase's state as 22 flat fields
- **Evidence** `[evidence]`. `game/Turn.kt` lines 40–62 hold 22 fields, 4 of them nullable. The KDoc lists invariants
  such as "empty outside Investment", "null when no ship waits to land" and "while either is set, nobody jumps or
  lands". Only comments and method checks enforce them.
- **Reference:** "make illegal states unrepresentable" (Minsky; Wlaschin, *Domain Modeling Made Functional*).
- **Fix:** give `Turn` a sealed per-phase state (`Bidding`, `ShipJumps`, `Trading`, `Investment`), keeping the fields
  that span the turn (`interceptionTries`, `initiative`) on `Turn`.
- **Why not now:** it changes the stored shape (`TurnItem`, with a 486-line round-trip test). That needs an
  Expand–Migrate–Contract change for the games already stored, and an ADR. No bug so far traces back to state left
  over from another phase.
- **Trigger:** the first such bug, or a fifth phase being added.

### 4. Medium: dead first-attempt code in `trading`, including a second dice port
- **Evidence** `[evidence]`. Nothing outside the cluster itself references `TradeOfferBook`, `InMemoryTradeOfferBook`,
  `TradeOfferSequence`, `TradeOffer`, `BestOfferCalculator`, `TransactionAmountCalculator`, `TradeResult`,
  `DiceRoller`, `RandomDiceRoller`, `trading.Player`, `Loan`, `trading.MarketPosition` or `CompetitivePosition`.
  `TradeDesk` is used only for `MIN_PRICE`/`MAX_PRICE`.
  - `RandomDiceRoller` is a `@Component`, so it is a live Spring bean running alongside `game.Dice`.
  - `trading.MarketPosition` has the same name as `game.MarketPosition` but a different model.
- **What is still live:** `StarSystem`, `Goods`, `OfferType`, `SupplyDemand` and `LocationStats`. `game` imports
  them about 75 times, so they are a shared kernel (the board), not "trading".
- **Driver:** modifiability (#1). For agents this is the strongest reason: dead code with live-looking names is
  precedent they will copy.
- **Reference:** review rubric #13 (parallel old and new implementations of the same concept).
- **Fix:** delete the dead classes and their tests, and move the price bounds to where `Offer` checks them.
  Optionally rename `trading` → `board` in a separate, mechanical PR. The risk is low because the compiler finds
  every use.

### 5. Medium: package boundaries have no fitness function
- **Evidence** `[evidence]`. Today the domain imports nothing from Spring, AWS or Jackson (0 matches). No test keeps it
  that way. Standards "Not decided yet" postpones Konsist/ArchUnit "until a couple of real features exist". About 13
  epics have now shipped, so that condition is met.
- **Fix:** add one Konsist test of about 30 lines with three rules: `game`/`login`/`trading` (excluding `adapter..`)
  import no `org.springframework`/`software.amazon`/`com.fasterxml`; nothing outside an `adapter` imports from one;
  `game` doesn't import `login`.
- **Cost:** about an hour. It makes standards §1–§2 hold by tooling rather than by memory, as §5's ktlint rules
  already do.
- **This changes a recorded "not decided" item, so it needs the creator's go-ahead.**

### 6. Low: repeated work in the controller
`GameController` repeats `sessionFor(authorization)` and an enum lookup with a hand-written 400 in each of its 38
endpoints. A `HandlerMethodArgumentResolver` for `Session` would remove about 76 lines. Do it only if the controller
is being changed anyway.

## Working well: keep

- One deployable, polling, static JS, and no distributed parts.
- `DynamoDbGameStore`: a `mutate` without side effects, retried on a version conflict; game and log written in one
  transaction; consistent reads, which settles A01.
- Field-by-field item mapping with no defaults in domain classes, so a missed field fails loudly (§12).
- `PlayerIds`: emails never reach the client.
- The custom ktlint rules (`functions-in-classes`, `copy-only-in-own-class`). Turning the standards into tooling is
  what keeps an agent-built codebase consistent.
- Domain tests are plain JUnit with fakes, and only the adapters use Testcontainers.

## Simplest option

Change nothing. That is enough as long as `Game.kt`'s share of commits stays below about 30 % and no confidential
field is added. Even so, findings 4 and 5 are cheap enough to do now.

## Suggested order

| Step | Finding | Size | Ships alone | Rollback |
|---|---|---|---|---|
| 1 | 4: delete dead `trading` code | S | yes | revert |
| 2 | 5: Konsist boundary test | S | yes | revert |
| 3 | 1: `Game.viewFor(email)` projection | M | yes (same JSON) | revert |
| 4 | 2: log text into parts, guards into `Turn` | M, can be split by phase | yes | revert |
| – | 3: per-phase `Turn` state | L + ADR | when triggered | Expand–Migrate–Contract |
