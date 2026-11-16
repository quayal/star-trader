# Backlogs

## .ai/specs/product-brief.md

Backlog source: `.ai/specs/product-brief.md`
Last filed: 2026-10-29 (E06 filed 2026-11-15; E06-S08 filed 2026-11-16 as #113)

2026-11-16: the creator moved the Investment phase to its own Infrastructure screen (D12). Added E06-S08 (the screen; moves Collect off the Deck) and made E06-S04 and E06-S05 depend on it; S04 and S05's wording changes in the spec. Filed as #113; #102 and #103 refreshed from the spec (new dependency, "Infrastructure screen" wording).

2026-11-16 run: planned E07 (ships from hulls and modules, Fleet tab) from the creator's vault note and answers; spec `2026-11-16-e07-hulls-modules-fleet.md`. Filed as #116–#119. Shipyards, crews, repair and battle are not filed (listed as Later in the spec).

2026-11-15 run: planned E06 (factories, warehouses, Investment phase) from the creator's answers; spec `2026-11-15-e06-factories-warehouses.md`. Filed as #98–#105. The parked "Later" row was renamed E99 so E06 could be used for this epic.

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
| E06 | #98 | Factories, warehouses and the Investment phase | — | E05 | no |
| E06-S01 | #99 | A turn also has an Investment phase | #98 | E05-S07 | no |
| E06-S02 | #100 | A player can own warehouses and factories, and everyone sees them | #98 | E05 | no |
| E06-S03 | #101 | Collecting output at the start of Investment | #98 | E06-S01, E06-S02 | no |
| E06-S04 | #102 | Buying warehouse blocks | #98 | E06-S03, E06-S08 | no |
| E06-S05 | #103 | Building or extending a factory | #98 | E06-S03, E06-S08 | no |
| E06-S06 | #104 | A warehouse lets you trade without a ship | #98 | E06-S02, E05-S04 | no |
| E06-S07 | #105 | The Game log shows builds and collected output | #98 | E06-S04, E06-S05 | no |
| E06-S08 | #113 | An Infrastructure screen for the whole Investment phase | #98 | E06-S03 | no |
| E07 | #116 | Ships from hulls and modules, and the Fleet tab | — | E06 | no |
| E07-S01 | #117 | Hulls and modules exist as game data, and a ship is made of them | #116 | E06 | no |
| E07-S02 | #118 | Cargo space comes from the ship's modules, and new games start with the new ships | #116 | E07-S01 | no |
| E07-S03 | #119 | A Fleet tab shows my ships with all their stats | #116 | E07-S02 | no |
| E99 | — | Later (parked: N01, R07, D05/D06/N04, rate-limiting); renamed from E06 on 2026-11-15 so E06 could be factories | — | — | not filed |
