# Apply engineering standards to the existing codebase

- Date: 2026-10-15
- Status: done — `./gradlew build` and `./gradlew test` pass (35 tests: 30 pre-existing + 5 new `TradeDeskTest` cases)

## Why

The current source tree (`domain/`, `service/`, `web/`, `web/dto/`, `util/`) is the creator's first,
learning-stage attempt and predates `.ai/engineering-standards.md`. It's a textbook layer-by-technical-
concern split — exactly what rule #1 of the standards forbids. This spec migrates the existing trading
logic (the only feature that exists in code today) into the package-by-concept + hexagonal shape the
standards describe, without inventing new product features (no game/room scoping, no auth — those are
separate, undesigned work per the product brief's Now scope).

## What exists today

One bounded concept, scattered across technical layers: star systems, goods, trade offers, dice-resolved
trading (`BestOfferCalculator`, `TransactionAmountCalculator`, `SDCalculator`), an in-memory trade offer
book, and the web controller/DTOs exposing it. No persistence, no multi-game boundary yet.

## Target shape

Single top-level package `trading` (the one concept that exists), hexagonal inside it per standards rule #2:

```
trading/                         domain: entities, value objects, calculators, ports (interfaces)
  StarSystem, Goods, OfferType, CompetitivePosition, MarketPosition, Player, Loan,
  TradeOffer, TradeResult, SDIndicator, SDCalculator,
  BestOfferCalculator, TransactionAmountCalculator   (stateless; @Component dropped — plain
                                                       instantiation, no DI needed for pure functions)
  DiceRoller                     port: abstracts the dice source
  TradeOfferBook                 port: abstracts storing/listing offers
  TradeDesk                      new: orchestrates submit/resolve against the two ports + calculators
  InvalidTradeOfferException     new: domain-rule violation (price range, goods-not-in-system)

trading/adapter/random/
  RandomDiceRoller                real DiceRoller adapter (today's util/Dice logic)

trading/adapter/memory/
  InMemoryTradeOfferBook          real TradeOfferBook adapter (today's service/TradeOfferBook logic);
                                  simple enough to double as the test double per standards rule #4 — no
                                  separate fake needed for this one
  TradeOfferSequence              implementation detail of the in-memory book (moved, not a port)

trading/adapter/web/
  TradeOfferController, TradeOfferDtos   translates HTTP <-> domain at the border (rule #3)
```

`DiceRoller` needs a `FakeDiceRoller` test double (deterministic rolls) since the real adapter is
non-deterministic — this is the one port where standards rule #4's fake is actually load-bearing.

## Why `TradeDesk` is a new class, not just a file move

Before this change, all orchestration (validate request -> roll dice -> pick best offer -> compute
amount) lived inside the web controller, meaning nothing exercised the ports except a full
`@SpringBootTest`. That's precisely the slow-test problem the creator flagged as the reason for going
hexagonal. Extracting `TradeDesk` gives the ports somewhere to be used from a plain, fast, Spring-free
test (rule #7) — without it, rule #4 (ports get fakes used in domain tests) would have nothing to attach
to. This is the one piece of new logic in this migration; everything else is a move + import fix.

Validation splits by rule #2's dependency boundary:
- malformed/missing HTTP fields -> stays in the web adapter, still throws `ResponseStatusException`
  (that's a translation-layer concern: an incomplete request, not a business rule)
- business rules (price must be 1-20, goods must be tradeable in the chosen system) -> move into
  `TradeDesk`/domain, throwing `InvalidTradeOfferException`; the web adapter catches it and maps to the
  same HTTP 400 responses as before

All existing HTTP behavior and response bodies are preserved exactly; `TradeOfferControllerTest`
(the `@SpringBootTest` integration test) is moved as-is and must still pass unchanged.

## Out of scope

- Game/room scoping of `TradeOfferBook` (still a single global in-memory list) — this needs the
  not-yet-designed "games" concept from the product brief and is tracked as a known gap, not fixed here.
- Any new product behavior. This is a structural migration only.
- Tooling (ktlint/detekt/Konsist) — explicitly deferred in the standards doc itself.

## Verification

`./gradlew build` and `./gradlew test` must pass with the same test count as before plus the new
`TradeDeskTest`.
