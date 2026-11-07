# CI pipeline: build, lint, tests

- Date: 2026-10-15
- Status: implemented on branch `ci-pipeline`, not yet merged; branch protection not yet applied (pending explicit confirmation — see below)

## Why

The creator is directing an AI agent as the primary implementer and wants a safety net that doesn't rely on
trusting the agent's own claims about what it ran. The real fix for that is making "tests pass" a fact
checked by GitHub, not a claim made in chat: a required CI status check on `main` means nothing merges
unless the real build, lint and test run green, independent of what any agent says.

## Scope

A single GitHub Actions workflow (`.github/workflows/ci.yml`) running on every push and on pull requests
targeting `main`:

- checkout, JDK 17 (Temurin), Gradle setup
- `./gradlew build` — this alone covers compilation, ktlint, and the full test suite (unit + the one
  `@SpringBootTest` integration test), because the ktlint plugin wires `ktlintCheck` into Gradle's `check`
  lifecycle task, which `build` already depends on.

Browser/e2e testing is explicitly out of scope (discussed and agreed separately): the frontend is a thin,
largely static mockup and the product brief's Now-scope features (auth, multi-game, phases) aren't built
yet, so a Playwright/Selenium suite would mostly protect UI that's about to be rebuilt.

## ktlint

Added `org.jlleitschuh.gradle.ktlint` 14.2.0. Two decisions worth recording:

- **Code style: `intellij_idea`, not the plugin's default `ktlint_official`.** The stricter official style
  (one parameter per line, forced trailing commas everywhere, every chained call on its own line) would
  have required reformatting nearly every file in the project just to pass CI for the first time — that's
  disproportionate "basic setup" cost and fights the project's actual existing conventions rather than
  codifying them.
- **`.editorconfig` added at the repo root** declaring `indent_style = tab` for `.kt`/`.kts` files. Without
  it, ktlint's formatter defaults to 4-space indentation and a first `ktlintFormat` run rewrote all 27
  source files' indentation (675+/607- diff) — a mass rewrite nobody asked for, and one that would have
  collided badly with the then-still-unmerged `apply-engineering-standards` branch touching the same files.
  With tabs declared explicitly, the remaining violations were ~38 small, genuinely worthwhile fixes
  (missing trailing commas, inconsistent constructor-parameter whitespace, import ordering, one-line bodies
  that fit on the signature line) — fixed via `ktlintFormat` and verified with a full rebuild + test run.

## detekt — attempted, deferred

detekt was in the original "basic" scope but had to be dropped: the latest stable release
(`io.gitlab.arturbosch.detekt` 1.23.8) embeds a Kotlin 2.0.21 compiler internally and hard-fails
(`check(KotlinCompilerVersion.VERSION == whichKotlin())`, no override flag) against this project's Kotlin
2.3.21. The only thing published that targets newer Kotlin is `dev.detekt:detekt-gradle-plugin:2.0.0-alpha.6`
(a new group ID, alpha quality, published August 2026) — not something to bolt into CI unverified. Tracked
as a follow-up in `2026-10-15-ci-hardening-followups.md`.

## Branch protection — not yet applied

Requiring the CI workflow as a required status check on `main` is a GitHub repository setting, not a code
change — applying it was intentionally held back pending the creator's explicit go-ahead rather than done
as a side effect of this spec, since it changes how every future merge to `main` behaves.

## Verification

`./gradlew build` passes locally (compile + ktlint + all 35 tests) on the `ci-pipeline` branch, which is
rebased on `main` after `apply-engineering-standards` merged.
