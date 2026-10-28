# Backlogs

## .ai/specs/product-brief.md

Backlog source: `.ai/specs/product-brief.md`
Last filed: 2026-10-25

E03–E05 still have no stories/tasks filed — this run was scoped to `--epic E02` only, by request, to keep the chunk small. Narrowed to the MVP slice per the brief's own stated purpose ("playtest login, turns, trading, and visuals"); multi-game hosting beyond one room (full R01), confidential-info trading (R06, underspecified), and Scope/Later items are deferred to a follow-up backlog run. E02-S01/S02 ship with a placeholder starting-money/ship-capacity value pending Q06 (blocks only the literal numbers, not R22's structural rule).

| Id | Issue | Title | Epic | Depends on | Adopted |
|---|---|---|---|---|---|
| E01 | #7 | Player login and session | — | — | no |
| E01-S01 | #12 | A player can request a one-time sign-in code | #7 | — | no |
| E01-S01-T01 | #13 | Integrate a transactional email provider to deliver the one-time code | — | — | no |
| E01-S02 | #14 | A player can exchange a valid one-time code to start a session | #7 | E01-S01 | no |
| E02 | #8 | Game creation and persistence | — | E01 | no |
| E02-S01 | #19 | A player can create a new game and becomes its first player | #8 | E01-S02 | no |
| E02-S02 | #20 | A player can join an existing game as another player | #8 | E02-S01, E01-S02 | no |
| E02-S03 | #21 | A game's state persists so it survives a server restart | #8 | E02-S01 | no |
| E02-S03-T01 | #22 | Pick a document store and validate it meets the consistency and schema needs | — | E02-S03 | no |
| E02-S04 | #23 | A player can poll their game's current shared state | #8 | E02-S01, E01-S02 | no |
| E03 | #9 | Bidding phase | — | E02 | no |
| E04 | #10 | Ship-jump phase | — | E03 | no |
| E05 | #11 | Trading phase and resolution | — | E04 | no |
| E06 | — | Later (parked: N01, R07, D05/D06/N04, rate-limiting) | — | — | not filed |
