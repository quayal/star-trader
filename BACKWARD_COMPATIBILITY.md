# Backward compatibility

What this project treats as a protected contract surface, and how a change to one must be handled. Review skills check changes against this file; implementation skills warn when a change isn't compliant.

## Context: this is a pre-launch, single-maintainer project

Per `.ai/specs/product-brief.md`, there are no real players or external clients yet, and the current API is explicitly **expected** to be replaced as multi-game hosting (R01), authentication (D03) and persistence (D02) land. Treat the surfaces below as "don't break without noticing", not "frozen forever" — but still require every breaking change to be a deliberate, stated decision (ideally recorded in the product brief's Decisions table), not an accident.

## Protected surfaces today

### 1. Game rules (highest protection)

`StarSystem`, `Goods`, `OfferType`, and the resolution logic in `TradeDesk` / `BestOfferCalculator` / `SDCalculator` / `DiceRoller` encode the original board game's actual rules (business rule R03: six star systems, four tradable goods, dice-resolved trades). These are the closest thing this project has to a stable contract, because they're fixed by the board game itself, not by this codebase's current stage.

- **Breaking change**: altering a star system's name/goods, a good's identity, the dice mechanic, or the offer-matching/pricing logic in a way that changes a game outcome for the same inputs.
- **Required path**: must come with a test proving the new behavior against the original game's rule, and a note in the PR body explaining why the rule changed (new house rule, bug fix against the original, etc.) — not silently "while I was in there."

### 2. HTTP API — `/api/*` (`TradeOfferController`)

Current endpoints, all under `/api`:

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/options` | — | `TradeOfferOptions` (star systems, offer types, price bounds) |
| GET | `/trade-offers` | — | `List<TradeOfferView>` — every offer, unscoped |
| POST | `/trade-offers` | `TradeOfferRequest` | `201` + `TradeOfferView`, or `400` with a plain-text reason |
| GET | `/trade-offers/resolve` | `turn`, `system`, `goods` query params | `TransactionView`, or `404` with a plain-text reason |

- **Breaking change**: removing/renaming a field in `TradeOfferView`, `TradeOfferRequest`, `TradeOfferOptions`, or `TransactionView`; changing a field's type or meaning; changing a success/error status code; removing an endpoint.
- **Required path**: none required yet (no external client exists), but any such change must be called out explicitly in the PR body ("Decisions touched" per `SDLC.md`), since the static frontend in `src/main/resources/static` is the one consumer and must be updated in the same PR.
- **Known planned break**: this entire surface is global/unscoped (no game id), which product brief problem statement and engineering standard #11 both flag as not meeting R01. The multi-game work in Scope/Now is expected to replace these endpoints with game-scoped equivalents (e.g. `/api/games/{gameId}/trade-offers`) — that is anticipated, tracked work, not a violation to block on today, but once a game-scoped surface ships, *that* becomes the protected one and this table must be updated in the same PR.

### 3. Frontend/backend contract

`src/main/resources/static/index.html` is served as-is by Spring Boot's static resource handling and talks to `/api/*`. A change to the API response shape or the static file's expectations of it must keep both sides in sync within one PR — there is no versioning or feature-detection between them today.

### 4. Build and tooling contract

- `./gradlew build` is what CI (`.github/workflows/*.yml`) and this repo's validation gate (`SDLC.md`) run; it must keep working from a clean checkout with JDK 17.
- ktlint style rules (`build.gradle.kts`) are enforced as part of `check`; a change to `.editorconfig`/ktlint config that relaxes a rule project-wide is a deliberate decision, not a drive-by fix for one file.

## Not yet applicable (no contract exists)

These will become protected surfaces once they exist, per the product brief's Scope/Now — call this file out for an update in the PR that first introduces each:

- **Persistence schema** — no document store exists yet (D02); once it does, the per-game document shape becomes a protected surface needing a migration/versioning story.
- **Auth/session** — no login exists yet (D03); once the email-code session flow ships, its cookie/token shape and session lifecycle become protected.
- **Confidential-data authorization** — the session→player→game check from product-brief assumption A05 doesn't exist yet; once it does, loosening it is always a blocker-severity review finding (see `CODE_REVIEW.md`), never a plain compatibility note.

## Required path for any breaking change, in general

1. State the break explicitly in the PR body (what surface, what changes, who/what consumes it).
2. Update every in-repo consumer in the same PR (currently: the static frontend).
3. If the surface is tied to a product-brief business rule or decision, add a superseding entry there per `SDLC.md`'s "Amending this process" / the product brief's own change-control table.
4. Prefer additive changes (new field, new endpoint) over renaming/removing while any real consumer might exist.
