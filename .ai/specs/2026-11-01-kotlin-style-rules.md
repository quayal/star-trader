# Kotlin style rules: no trailing commas, functions belong to the type they work on

## 📝 TLDR

Two style rules the creator set, written into the engineering standards (§5) and enforced by ktlint, so `./gradlew check` fails on a violation rather than relying on agents remembering:

1. **No trailing commas**, at declaration and call sites.
2. **A function that works on one of our types belongs in that type's class.** A function over a `Game` is a method of `Game`. No extensions on our own types (extensions are for library types we can't edit), no Kotlin `object` as a namespace for functions. A function no class owns may be top-level.

## Why

- Trailing commas were "forbidden" in `953cc98` (2026-10-15) by setting ktlint's two trailing-comma rules to `disabled`. Disabling a rule only stops it checking, so commas came back unnoticed: 81 by 2026-11-01. The rule was never written in the standards, so later sessions didn't know about it.
- The creator's preference (2026-11-01): you call a function on the object you have, and the function is declared in that object's class. Extension functions are for classes you can't edit; "static" functions on objects aren't used either.

## Decisions

- **Trailing commas:** turn both ktlint rules back on with `ij_kotlin_allow_trailing_comma = false` and `ij_kotlin_allow_trailing_comma_on_call_site = false`, so ktlint reports a comma and `ktlintFormat` removes it. Strip the existing ones in this PR.
- **Mapping between the domain and an adapter lives on the adapter side** (creator's choice, 2026-11-01), as a constructor or method of the adapter's class: `GameResponseDto(game, viewerEmail)`, `TradeOfferView(offer)`, and for DynamoDB something like `ShipItem(ship)` / `ShipItem(map).toShip()`. Putting `toAttributes()` in `Ship` would make the domain import the AWS SDK, which §2/§3 forbid.
- **Logic over a bare value gets a value class** (creator's choice, 2026-11-01): `SDCalculator` becomes `@JvmInline value class SupplyDemand(val indicator: Int)` with `priceModifier()`; the reverse lookups are companion factories `SupplyDemand.lowestFor(priceModifier)` / `highestFor(priceModifier)`.
- **Enforcement:** ktlint has no such rule, so add a custom ruleset as the Gradle subproject `ktlint-rules`, wired in with the ktlint plugin's `ktlintRuleset` configuration. Rule `trader:functions-in-classes` reports:
  - in an `object` declaration (not a companion, not an object expression), a function with parameters or a receiver, unless it `override`s an interface member;
  - in a `companion object`, a function taking exactly one parameter of a non-built-in type: a conversion (`TradeOfferView.from(offer)`), which becomes a constructor. Factories of several values (`Game.lobby(id, turns, creator)`) or of built-in values (`SupplyDemand.lowestFor(priceModifier)`) stay allowed;
  - a top-level function whose first parameter is a project type;
  - an extension function or property, wherever declared, on a project type.
- **Top-level functions are allowed when no class owns them** (creator, 2026-11-01). An earlier draft of this PR banned every top-level function except `main`. The creator called that too strong: some logic assembles something from several inputs and belongs to no class. The test is what a function works on, not where it's declared. ktlint checks only the first parameter; a project type in a later parameter or inside a collection is left to review (`CODE_REVIEW.md`), by the creator's choice ("LLM reviewing may notice it").
- **"Project type" without type resolution:** ktlint is syntactic, so a receiver counts as a project type when it's imported from `dev.adriangrzebyk.trader`, written fully qualified under that package, or used without an import and not one of Kotlin's default-imported types (then it's from the same package). Type parameters (`fun <T> T.x()`) and function types are not. Known gap: a library type reached through a wildcard import would be misread as a project type; ktlint's `no-wildcard-imports` already forbids those.
- **Existing code on `main`** is rewritten to pass: `SDCalculator` → `SupplyDemand`; `TradeOfferView.from` → a `TradeOfferView(offer)` constructor; `Game/Player/Ship.toResponseDto()` extensions → `GameResponseDto(game, viewerEmail)`, `PlayerResponseDto(player)`, `ShipResponseDto(ship)` constructors.
- **PR #65** (still open) has `GameItemMapper`, `PlayerAttributes`, `ShipAttributes`, `TurnAttributes` as objects. It isn't changed here; whichever of the two merges second rewrites them as adapter-side classes.

## Done when

- `./gradlew check` fails on a trailing comma and on each shape the rule reports; the rule has its own unit tests.
- Engineering standards state both rules; a lesson records that ktlint's `disabled` doesn't mean "forbidden".
- `./gradlew build` passes on the branch and in CI.
