# Kotlin style rules: no trailing commas, extension functions over object functions

## 📝 TLDR

Two style rules the creator set, written into the engineering standards and enforced by ktlint, so `./gradlew check` fails on a violation rather than relying on agents remembering:

1. **No trailing commas**, at declaration and call sites.
2. **Functions over a value are extension functions, not functions on an `object`.** `game.toItem(version)`, not `GameItemMapper.toItem(game, version)`.

## Why

- Trailing commas were "forbidden" in `953cc98` (2026-10-15) by setting ktlint's two trailing-comma rules to `disabled`. Disabling a rule only stops it checking, so commas came back unnoticed: 83 across 30 files on 2026-11-01. The rule was never written in the standards, so later sessions didn't know about it.
- The creator prefers extension functions on domain types to "static" functions on objects (2026-11-01). Both compile to the same bytecode; this is a readability preference: the value the function works on reads as the receiver.

## Decisions

- **Trailing commas:** turn both ktlint rules back on with `ij_kotlin_allow_trailing_comma = false` and `ij_kotlin_allow_trailing_comma_on_call_site = false`, so ktlint reports and `ktlintFormat` removes them. Strip the existing ones in this PR.
- **Extension functions:** ktlint has no such rule, so add a custom ruleset as the Gradle subproject `ktlint-rules`, wired in with the ktlint plugin's `ktlintRuleset` configuration. Rule `trader:no-functions-on-objects` reports:
  - any function that takes a parameter, declared directly in an `object` declaration (not a companion), unless it `override`s an interface member. Write it as an extension on the type it works on, or as a top-level function when that type is a primitive or `String` (an extension on `Int` would leak into every `Int`).
  - a function in a `companion object` that takes exactly one parameter of a non-built-in type. That's a conversion (`TradeOfferView.from(offer)`) and becomes an extension (`offer.toView()`). Factories that build the companion's own type from several values (`Game.lobby(id, turns, creator)`) and functions taking only built-in types (`String`, `Int`, …) stay allowed: they have no natural receiver.
  - Objects holding only constants or zero-argument functions (`OneTimeCode`) stay allowed.
  - The rule is syntactic (ktlint has no type resolution), which is why it's phrased by declaration shape, not by "is the parameter a domain type".
- **Existing code on `main`** is rewritten to pass: `SDCalculator` becomes top-level functions; `TradeOfferView.from` becomes `TradeOffer.toView()`.
- **PR #65** (still open) has `GameItemMapper`, `PlayerAttributes`, `ShipAttributes`, `TurnAttributes` as objects. It isn't changed here; whichever of the two merges second rewrites them to extensions (`game.toItem(version)`, `item.toGame()`, …).

## Done when

- `./gradlew check` fails on a trailing comma and on a function that breaks the rule above; the rule has its own unit tests.
- Engineering standards state both rules; a lesson records that ktlint's `disabled` doesn't mean "forbidden".
- `./gradlew build` passes on the branch and in CI.
