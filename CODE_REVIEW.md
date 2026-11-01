# Code review

This document is the repo's own review rules, applied automatically by the `om-code-review` skill (and therefore `om-auto-review-pr`) in addition to its built-in checklist. It complements, not replaces, any `reviewChecklist` path set in `.ai/agentic.config.json` — here that's `.ai/engineering-standards.md` itself, which is the canonical source for the rules below; read it for the full rationale.

Stack: Spring Boot 4 + Kotlin 2.3 (JVM 17), ktlint-enforced style, JUnit 5 + AssertJ for tests, a static HTML/JS frontend served from `src/main/resources/static`. No database yet — persistence is in-memory, pending the document-store decision (D02) in `.ai/specs/product-brief.md`.

## Review priorities, in order

1. **Correctness of game rules.** Trade resolution (`TradeDesk`, `BestOfferCalculator`, `SupplyDemand`, `DiceRoller`) encodes the board game's actual rules (R03 in the product brief). A change here that doesn't match the original game's rule, or that isn't covered by a test proving the rule, blocks.
2. **Confidential-data scoping.** Per the product brief (R02, A05), any endpoint or query that returns one player's private state (trade offers, agents, secret opportunities) must check the caller is that player in that game — not just that they're authenticated. This repo has no auth/session layer yet; once one lands, its absence here is a blocker, not a nit.
3. **No unscoped/global state.** Engineering standard #11: all game state must be keyed by game id, even before persistence exists. `TradeOfferBook`/`InMemoryTradeOfferBook` are pre-multi-game and already flagged in the product brief as not meeting this — don't let *new* state follow the same pattern; existing instances are tracked work, not something every unrelated PR must fix.
4. **Atomicity of state transitions.** Standard #9: a state change reachable from more than one path (a player action racing a phase timer, for example) must be a single atomic/idempotent operation, never read-decide-write split across steps.
5. **Contract surfaces.** Check changes against `BACKWARD_COMPATIBILITY.md`.

## Repo-specific checks

- **Package shape** (standard #1/#2): top-level packages are named after game concepts (`trading`, future `phases`, etc.), never technical layers. Inside a concept package, adapters live under `<concept>/adapter/<kind>` (e.g. `trading/adapter/web`, `trading/adapter/memory`, `trading/adapter/random`) and domain code at the top of the package imports nothing from Spring, Jackson, or a persistence client. Flag an adapter-layer import creeping into domain code, or a new top-level package named after a technical layer instead of a game concept.
- **Ports have fakes** (standard #4): a new port (repository, clock, dice/random source, email sender, etc.) needs an in-memory/fake implementation usable in domain tests, not just the real adapter. `FakeDiceRoller` is the existing example.
- **Domain tests stay Spring-free** (standard #7): tests under a concept's domain package (not its `adapter/*` sub-packages) must not use `@SpringBootTest` or boot the Spring context. Only adapter tests (e.g. `TradeOfferControllerTest`) may use Spring test slices, and only to verify wiring, not game rules.
- **Translation at borders** (standard #3): a type crossing into HTTP/JSON (the `*Dtos.kt` files) or out of a package is converted explicitly by a constructor or method of the adapter-side class (see `TradeOfferView(offer)`, `GameResponseDto(game, viewerEmail)`) rather than leaking an internal type across the boundary.
- **Functions belong to the type they work on** (standard #5): a function that works on one of our types is a method of that type's class (or, for domain↔adapter mapping, of the adapter-side class). ktlint's `trader:functions-in-classes` already fails the build on extensions on our types, functions with parameters on a Kotlin `object`, companion conversions, and top-level functions whose *first* parameter is our type. Check what it can't see: a top-level function that works on our type through a later parameter (`price(roll, offer)`) or a collection (`fun summarize(players: List<Player>)`, which may belong on a class that owns that list, like `Game`). A top-level function no class owns, assembling something from several inputs or working on built-in or library types, is fine.
- **Naming** (standard #5): game vocabulary (`TradeOffer`, `StarSystem`, `Goods`) over generic terms (`Item`, `Manager`, `Handler`); immutable data (`val`, `data class`) by default.
- **ktlint clean**: `./gradlew build` runs `ktlintCheck` as part of `check`; a PR with style violations fails the gate before a human needs to comment on formatting.
- **No speculative abstraction** (standard #0/#5): flag a port/adapter split, interface, or configuration knob introduced for a need that doesn't exist yet. Prefer the simplest thing that passes its tests.

## Severity guidance

- **Blocker**: wrong game-rule behavior without test coverage; a confidential-data leak across players; new unscoped/global state; a racy (non-atomic) state transition; a `risk-high` change (per `SDLC.md`'s label taxonomy) missing the evidence that label's gate requires.
- **Major**: a port added without a fake; a domain test that boots Spring; a package organized by technical layer instead of game concept; a leaked internal type across a package/HTTP boundary.
- **Minor**: naming that doesn't match the game's vocabulary; a function over one of our types that ktlint missed, declared outside that type's class; a missed opportunity for a value object (standard #8) where one would clarify; test gaps in adapter/translation code.
- **Nit**: style not already caught by ktlint, comment clarity, minor duplication.

When a rule above conflicts with the simplest working solution for the problem at hand, say so in the review rather than blocking on dogma — see engineering standard #0.
