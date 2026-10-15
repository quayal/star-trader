# CI hardening follow-ups

- Date: 2026-10-15
- Status: planned, not started — deferred out of `2026-10-15-ci-pipeline.md` to keep that one shippable quickly

## Why

Discussed alongside the basic CI pipeline: given an AI agent is the primary implementer here, the real risk
to guard against is confidently-wrong code (hallucinated correctness, tests that don't actually test
anything, quiet drift away from `.ai/engineering-standards.md`) rather than just "does it compile." These
items each close a different gap than build+lint+test does, but none were cheap or safe enough to bundle
into the first CI pass.

## Items

- **detekt**, once there's a stable release compatible with this project's Kotlin version (currently 2.3.21).
  See `2026-10-15-ci-pipeline.md` for why it was dropped from the basic pipeline (`io.gitlab.arturbosch.detekt`
  1.23.8 hard-fails on Kotlin 2.3.21; `dev.detekt:detekt-gradle-plugin:2.0.0-alpha.6` exists but is alpha).
  When adding it: generate a `detekt-baseline.xml` against the codebase as it stands then, so CI only fails
  on *new* violations rather than retroactively blocking on everything existing code already does.

- **An architecture/boundary test** (e.g. Konsist) asserting the hexagonal dependency rule from
  `.ai/engineering-standards.md` rule #2: domain code (top of each concept package) must never import
  Spring or Jackson; only `adapter/*` sub-packages may. Without this, the boundary only survives because an
  agent remembers to re-read the standards doc — a future session can easily reintroduce framework coupling
  into domain code without anyone noticing until it's spread through several files.

- **Mutation testing** (e.g. Pitest), lower priority. Catches the specific hallucination failure mode of a
  test that passes today but wouldn't actually catch a real bug (asserting whatever the code happens to do
  rather than what it should do). More setup cost than the other items here, worth adding once the basics
  are solid rather than in the same pass.

- **Secret scanning** (gitleaks, or GitHub's built-in secret scanning on this public repo) — cheap to turn
  on, and worth having before real credentials exist in the codebase (the product brief's passwordless
  email-code login, a future persistence connection string).

## Explicitly not planned here

Browser/e2e testing — see `2026-10-15-ci-pipeline.md`'s scope note; revisit once a real game flow
(polling, phases, confidential state) exists to make a scripted walkthrough worth its flakiness.
