# Backlogs

## .ai/specs/product-brief.md

Backlog source: `.ai/specs/product-brief.md`
Last filed: 2026-10-29

2026-10-29 run: filed E01-S03, E02-S05 and the E03–E05 stories from the playtest specs (`2026-10-29-*.md`, each story's design authority); acceptance criteria are the specs' numbered criteria, verbatim. Epic bodies #8–#11 were refreshed to the current rule ids (R40–R43, R25–R27, R34–R38, D09) with the user's yes.

Earlier (2026-10-25): E03–E05 still had no stories/tasks filed — this run was scoped to `--epic E02` only, by request, to keep the chunk small. Narrowed to the MVP slice per the brief's own stated purpose ("playtest login, turns, trading, and visuals"); multi-game hosting beyond one room (full R01), confidential-info trading (R06, underspecified), and Scope/Later items are deferred to a follow-up backlog run. E02-S01/S02 ship with a placeholder starting-money/ship-capacity value pending Q06 (blocks only the literal numbers, not R22's structural rule).

| Id | Issue | Title | Epic | Depends on | Adopted |
|---|---|---|---|---|---|
| E01 | #7 | Player login and session | — | — | no |
| E01-S01 | #12 | A player can request a one-time sign-in code | #7 | — | no |
| E01-S01-T01 | #13 | Integrate a transactional email provider to deliver the one-time code | — | — | no |
| E01-S02 | #14 | A player can exchange a valid one-time code to start a session | #7 | E01-S01 | no |
| E01-S03 | #34 | A player can sign in on the sign-in screen and returns to the game link they came from | #7 | E01-S02, E01-S01-T01 | no |
| E02 | #8 | Game creation and persistence | — | E01 | no |
| E02-S01 | #19 | A player can create a new game and becomes its first player | #8 | E01-S02 | no |
| E02-S02 | #20 | A player can join an existing game as another player | #8 | E02-S01, E01-S02 | no |
| E02-S03 | #21 | A game's state persists so it survives a server restart | #8 | E02-S01 | no |
| E02-S03-T01 | #22 | Pick a document store and validate it meets the consistency and schema needs | — | E02-S03 | no |
| E02-S04 | #23 | A player can poll their game's current shared state | #8 | E02-S01, E01-S02 | no |
| E02-S05 | #35 | A player can create a game, others join it from its lobby, and the creator starts it | #8 | E01-S03, E02-S01, E02-S02, E02-S04 | no |
| E03 | #9 | Bidding phase | — | E02 | no |
| E03-S01 | #36 | A game runs turns of three phases | #9 | E02-S05 | no |
| E03-S02 | #37 | Players see the shared board on the Deck and System details | #9 | E02-S04 | no |
| E03-S03 | #38 | A player places secret trade offers | #9 | E03-S01, E03-S02 | no |
| E03-S04 | #39 | A player signals done bidding and sees who the game is waiting for | #9 | E03-S01 | no |
| E03-S05 | #40 | Players bid for turn initiative | #9 | E03-S04 | no |
| E03-S06 | #41 | Players read public events in the Game log | #9 | E03-S05 | no |
| E04 | #10 | Ship-jump phase | — | E03 | no |
| E04-S01 | #42 | Players pick their place in the turn order | #10 | E03-S05 | no |
| E04-S02 | #43 | Players jump their ships in turn order | #10 | E04-S01 | no |
| E04-S03 | #44 | After each jump the player chooses where the ship ends up | #10 | E04-S02 | no |
| E04-S04 | #45 | Other players see the Ship-jumps phase progress live | #10 | E04-S03 | no |
| E05 | #11 | Trading phase and resolution | — | E04 | no |
| E05-S01 | #46 | The first initiative player orders and triggers resolutions | #11 | E04-S02, E03-S03 | no |
| E05-S03 | #47 | The counter resolves the offers | #11 | E05-S01 | no |
| E05-S04 | #48 | Each trader chooses an amount and which ships load or unload | #11 | E05-S03 | no |
| E05-S05 | #49 | The price changes, then Wholesalers trade | #11 | E05-S04 | no |
| E05-S06 | #50 | Trading 6 or more units can raise a player's market position | #11 | E05-S04 | no |
| E05-S02 | #51 | Offers are disclosed and the Dictator may act | #11 | E05-S03 | no |
| E05-S07 | #52 | The turn ends and the next turn starts with Bidding | #11 | E05-S05 | no |
| E05-S08 | #53 | The game ends after the last turn and shows the winner | #11 | E05-S07 | no |
| E06 | — | Later (parked: N01, R07, D05/D06/N04, rate-limiting) | — | — | not filed |
