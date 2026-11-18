# Engineering standards

Living document. Updated in place as we learn what works, not dated or versioned like a spec.

This project is a solo learning exercise in two things at once: building Star Trader, and learning to
direct AI coding agents as the primary implementers (the "future of SDLC" for this creator). These
standards exist so agents stay consistent with each other across sessions without the creator having to
re-explain the same decisions every time.

## 0. Practicality beats dogma

Hexagonal architecture and DDD patterns (ports/adapters, aggregates, value objects, bounded contexts) are
tools, not goals. Use the amount of structure that earns its keep for the problem in front of you. If a
port/adapter split is solving a problem that doesn't exist yet, skip it; if a plain function and a data
class do the job, that's the right call, not a shortcut. When a rule below conflicts with the simplest
working solution, say so and ask rather than silently defaulting to "the proper pattern."

## 1. Package by game concept, discovered as needed

- Top-level packages are named after game concepts (e.g. `game`, `login`, `board`), never technical layers
  (`controller`, `api`, `service`, `persistence`, `dto`).
- The package list isn't decided upfront — when starting new work, ask "what game concept does this
  belong to?" and put it there, creating a new package if none fits.
- Each package is self-sufficient: its logic and tests live together. Nothing outside a package reaches
  into its internals.
- A large concept inside a package gets a subpackage once it has several classes of its own: `game.battle` holds
  `Battle`, `Salvo`, `AttackTotal`, `Interception` (creator, 2026-11-17). It may import from its parent and the
  parent from it when both are parts of one aggregate saved as a unit (`Game`); a separate top-level package is for a
  separate concept, not for one that only exists inside a `Game`.

## 2. Hexagonal within each feature, not across the whole app

- *Inside* a concept package, layering by technical role is expected and good: the concept's domain code
  (entities, value objects, and the port interfaces it needs from the outside world) sits at the top of
  the package; adapters implementing those ports live in sub-packages, e.g. `game/adapter/web`,
  `game/adapter/dynamodb`. This is layering-as-detail-inside-a-slice — different from the top-level
  layering rule #1 forbids.
- Hard dependency rule: domain code imports nothing from Spring, Jackson, or a persistence client — ever.
  Adapters depend inward on domain interfaces; domain never imports an adapter.

## 3. Translate at package borders

- When a type crosses from one package into another, or into HTTP/JSON/storage, convert it explicitly
  with a small mapping function. Don't let one package's internal type leak out as another's public shape.
- Keep this lightweight — a mapping function is enough. Don't add interfaces/adapters for a translation
  that's only ever done one way.

## 4. Every port gets a fake, not just a real adapter

- A port (repository interface, clock, random/dice source, email sender, etc.) gets an in-memory/fake
  implementation used in domain tests, alongside whichever real adapter backs it in production.
- Build the port and its fake first, write domain tests against the fake, then build the real adapter.
  This is what makes "swappable infrastructure" and "fast tests" concrete rather than aspirational.

## 5. Clean, understandable code

- Name things in the game's own vocabulary (`TradeOffer`, `Phase`, `AutoSkip`), not generic terms
  (`Item`, `Manager`, `Handler`).
- Small, single-purpose functions. Immutable data (`val`, `data class`) by default.
- No speculative abstractions, no defensive handling for states that can't occur.
- **No trailing commas**, in declarations or calls. ktlint enforces this through `.editorconfig`
  (`ij_kotlin_allow_trailing_comma = false`, with the trailing-comma rules *enabled*), and `ktlintFormat` removes them.
- **A function that works on one of our types belongs in that type's class.** A function over a `Game` is a
  method of `Game`, called on the game you have, not `describe(game)` or `fun Game.describe()`. A function no
  class owns (it assembles something from several inputs, or works only on built-in or library types) can be a
  top-level function: `parseTurns(text)`, `main`. The test is what the function works on, not where it's declared.
  - **No extension functions or properties on our own types.** Extensions are for classes we can't edit, i.e.
    library types (`Map<String, AttributeValue>.attribute(name)`).
  - **A Kotlin `object` isn't a namespace for functions.** `object ShipAttributes { fun toAttributes(ship) }`,
    called as `ShipAttributes.toAttributes(ship)`, is a "static" function; the function belongs in a class, or at
    the top level when no class owns it. An `object` holding constants, or implementing an interface
    (`object SilentSender : CodeSender`), is fine.
  - **Mapping between the domain and an adapter is a constructor or method of the adapter-side class**, so the
    domain never imports an adapter's types (§2, §3): `GameResponseDto(game, viewerEmail)`,
    `TradeOfferView(offer)`, and for a stored item a class like `ShipItem(ship)` / `ShipItem(map).toShip()`.
    Not `game.toResponseDto()`, `TradeOfferView.from(offer)` or `ShipAttributes.toAttributes(ship)`.
  - **Logic over a bare value gets a class for that value**, often a `@JvmInline value class`:
    `SupplyDemand(indicator).priceModifier()`, not `SDCalculator.getPriceModifier(indicator)`.
  - `object`s keep constants and zero-argument functions. Companions keep constants and factories that build
    their own type from several values or from built-in values (`Game.lobby(id, turns, creator)`,
    `SupplyDemand.lowestFor(priceModifier)`); a companion function converting one value of another type is a
    constructor instead.
  - **Enforcement is partly ktlint, partly review.** The custom ktlint rule `trader:functions-in-classes` (in
    `ktlint-rules/`) reports an extension on our types, a function with parameters on an `object`, a companion
    conversion, and a top-level function whose *first* parameter is one of our types. It has no type resolution,
    so it judges "our type" from the imports (imported from `dev.adriangrzebyk.trader`, or used without an import
    and not a Kotlin default type). It misses a function that works on our type through a later parameter
    (`price(roll, offer)`) or a collection (`List<Player>`); reviewers check those (`CODE_REVIEW.md`).

## 6. Start with TDD where practical

- Game-rule logic (trades, dice, phase resolution) starts from a failing test.
- Thin adapter/translation code can follow rather than lead, but still needs coverage before merge.

## 7. Domain tests never load the Spring context

- Domain-layer tests must not use `@SpringBootTest` or anything that boots Spring. Plain JUnit against
  real domain objects and fakes (see #4) only — this is what keeps them fast.
- Spring test slices are reserved for adapter code (web, persistence) and kept to the minimum needed to
  verify the adapter wiring itself, not domain rules.

## 8. DDD vocabulary where it clarifies, not as a checklist

- Value objects for meaningful primitives (`PlayerId`, `Credits`) instead of raw `String`/`Int`, and
  aggregates where there's a real consistency boundary (e.g. `Game` enforcing its own invariants), are
  encouraged.
- **An aggregate root coordinates; its parts own their rules** (creator, 2026-11-06). `Game` is the one entry point and
  the unit saved atomically, but a rule about one part's data is a method of that part: `Ship.jumpTo`, `Player.bid`,
  `Offer.place`, `Turn.placeOffer`. `Game` calls them and doesn't `copy` or reach inside a `Ship`, `Player` or `Turn`.
  It keeps only what spans its parts: membership, which player acts, the log, and messages that name other players.
  The same goes for services (`CodeExchangeService` asks `StoredCode.canBeTried(now)`). ktlint's
  `trader:copy-only-in-own-class` enforces part of this: a data class's `copy` is called only on `this`, so changing
  another object goes through its method. Production code only; tests build fixtures with `copy`.
- No mandate for repositories-per-aggregate, domain events, or CQRS unless a real need shows up — rule #0
  governs here specifically.

## 9. State transitions must be atomic, not racy

- If a state change can be triggered from more than one path (e.g. a player action and a timer expiring),
  it must be a single atomic/idempotent operation — never a read-decide-write split across steps.

## 10. Confidential data access is checked and tested, not assumed

- Code reading one player's private state must verify the caller is that player in that game, not just
  that they're logged in.
- Add a test proving another player in the same game can't read it.

## 11. No global or unscoped state

- All game state is keyed by game id from the start, even before persistence exists. (The first attempt's
  `TradeOfferBook` — a single global in-memory list with no game boundary, deleted on 2026-11-19 — is the
  concrete example of what this rule rules out.)

## 12. Stored data is mapped field by field, and the mapping grows with the model

- Game state is stored as native DynamoDB attributes (one item per game; nested objects as maps and
  lists), translated by `game/adapter/dynamodb/GameItem` — never as an opaque JSON string.
- Nothing that grows with the length of a game lives inside the game item: DynamoDB refuses an item over 400 KB.
  The Game log is one `LogTurnItem` per turn beside it, written with the game item in one transaction (§9).
- One item class per persisted type (`GameItem`, `PlayerItem`, `ShipItem`, `TurnItem`), composed by its
  parent. Each maps as §5 says: a constructor from the domain object (`ShipItem(ship)`) and a method back
  (`ShipItem(attributes).toShip()`). A new type adds an item class and its round-trip test; a new field
  touches only its type's item class and test.
- A change that adds data to `Game` or anything inside it (a player's money, ships, offers…) extends its
  type's item class **and** its round-trip test in the same PR. A field that isn't mapped is silently
  lost on the next save.
- Keep that failure loud: the item class builds each domain object with every argument named, and domain data
  classes declare no default values, so a new field doesn't compile until the item class reads it; the round
  trip then fails until it writes it.
- Once real games exist, a newly added attribute is missing from older items: default it in the item class,
  not in the domain class (see `BACKWARD_COMPATIBILITY.md`, persistence schema).

## 13. Where a piece of logic goes: ask in order, first match wins

Placement is the decision agents get wrong most often, and in a way review catches late. Don't decide it by feel.
Write the rule down as one sentence ("a rocket roll at or below the hits destroys the target"), list the data it
**reads** and **changes**, then take the first row that matches (creator, 2026-11-17):

| # | Question | If yes, it goes… | Example here |
|---|---|---|---|
| 0 | Is it about what one player may see of a game (confidential data, R17, R29, §10)? | `PlayerView`, built by `Game.viewFor(email)`: the root looks the parts up for the viewer and each part decides whether its data is theirs. The DTO only maps the view. | `Resolution.awaitingTradeOf(email)`, `Battle.involves(email)`, `Interception.asks(email)` (creator, 2026-11-19) |
| 1 | Does it turn our data into HTTP/JSON/storage shapes or back? | A constructor or method of the adapter-side class. It only maps: an `if`, `filter` or `firstOrNull` over domain fields means a rule got in, so go on to row 4. | `InvestFactoryResponseDto(player, …)`, `BattleItem(battle)` |
| 2 | Does it need the outside world: randomness, time, I/O, another service? | Behind a port with a fake (§4). The domain gets the port or the value it produced passed in. | `Dice`, `FakeDice` |
| 3 | Does it load, save or run something as one transaction for a request? | An application service: load, call one method on the aggregate, save. No rule in it. | `ShipJumpsService.giveOrders` |
| 4 | Do all the fields it reads and changes belong to one object? | A method on that object. | `Factory.isFor(system, goods)`, `Battle.isJustOver()` |
| 5 | Is that so once other objects' contributions are passed in as plain values? | A method on that object taking those values, never the other objects themselves. | `Battle.fired(salvos, hitsSurvivable)`, not `fired(ships)` |
| 6 | Is it a calculation on a single meaningful number or string? | A value class around that value. | `ProductionCost(price).perUnit()`, `AttackTotal(n).hitsAgainst(shields)` |
| 7 | Does it truly need two or more parts' behaviour at once? | The nearest owner of both (usually `Game`) coordinates: it looks the parts up, calls their methods and passes values between them. The decisions stay in the parts. | `Game.withRoundFired`: fire salvos from two players' ships, hand them to `Battle`, apply `FiredRound` |
| 8 | Is it the result of one step that another step consumes? | A small immutable result type, next to the class that produces it. | `Salvo`, `FiredRound` |
| 9 | Is it shown in the browser? | The server computes it and sends it. The JS only formats it, and repeats a check only for a confirmed instant-feedback story, with the server still deciding. | `unitCost` in the DTO; S05-4's client-side money check |

Then pick the package: the game concept the owning class belongs to (§1), as a subpackage once that concept has
several classes of its own (`game.battle`).

**Tie-breakers.** When two rows seem to fit, take the one that keeps the data and the rule in the same class. When a
rule seems to need a part to take another part (row 5 vs 7), pass values. When the owning class doesn't exist yet,
create it: a new value class or result type is cheaper than a rule in the wrong place.

**Signals that a placement is wrong**, which `.claude/skills/trader-implement` greps for in a diff: a condition over
two or more fields of another object (`it.system == x && it.goods == y`, `battle.stage == … && battle.done…`); logic in
a DTO or item constructor; a method of one part taking another part as a parameter; `Game.kt` getting longer from rules
whose data lives in a part.

## 14. Boundaries are tested

§1 and §2 are checked by `src/test/kotlin/dev/adriangrzebyk/trader/ArchitectureTest.kt` (Konsist, plain JUnit), so a
slip fails `./gradlew check` instead of waiting for review (creator, 2026-11-19):

1. No file in a concept's domain (`game`, `game.battle`, `login`, `board`, anything not under `..adapter..`) imports
   `org.springframework`, `software.amazon`, `com.fasterxml` or `jakarta`. `web`, the top-level Spring config, is exempt.
2. No file outside an `..adapter..` package imports from one.
3. `board`, the board's fixed catalogue, imports nothing from `game` or `login`.
4. `game`'s domain imports nothing from `login`; its web adapter may (for `SessionStore`).

A new concept package or a new adapter kind extends the test's lists in the same change.

## Not decided yet

- The actual target package list — left to emerge from real features, not fixed in advance.
- Whether to adopt detekt. ktlint is in use, and `ktlint-rules/` is where a syntactic project rule goes (as §5's
  functions-in-classes rule does); package boundaries are tested by Konsist (§14).
- A worked example (one concept's port + fake + real adapter) as a template for agents to copy — deferred
  until the first real feature is built, which becomes the example.
