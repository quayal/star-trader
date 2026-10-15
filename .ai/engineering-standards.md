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

- Top-level packages are named after game concepts (e.g. `trading`, `phases`), never technical layers
  (`controller`, `api`, `service`, `persistence`, `dto`).
- The package list isn't decided upfront — when starting new work, ask "what game concept does this
  belong to?" and put it there, creating a new package if none fits.
- Each package is self-sufficient: its logic and tests live together. Nothing outside a package reaches
  into its internals.

## 2. Hexagonal within each feature, not across the whole app

- *Inside* a concept package, layering by technical role is expected and good: the concept's domain code
  (entities, value objects, and the port interfaces it needs from the outside world) sits at the top of
  the package; adapters implementing those ports live in sub-packages, e.g. `trading/adapter/web`,
  `trading/adapter/persistence`. This is layering-as-detail-inside-a-slice — different from the top-level
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
  `TradeOfferBook` — a single global in-memory list with no game boundary — is the concrete example of
  what this rule rules out.)

## Not decided yet

- The actual target package list — left to emerge from real features, not fixed in advance.
- Whether to adopt ktlint/detekt or an architecture-boundary test (e.g. Konsist) to make rules #1/#2
  self-enforcing — worth revisiting once a couple of real features exist to encode rules against.
- A worked example (one concept's port + fake + real adapter) as a template for agents to copy — deferred
  until the first real feature is built, which becomes the example.
