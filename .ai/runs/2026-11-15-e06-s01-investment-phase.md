# Run: E06-S01 — A turn also has an Investment phase

Issue: #99 (epic #98). Design authority: `.ai/specs/2026-11-15-e06-factories-warehouses.md`, which lives on branch
`spec/e06-factories-warehouses` (PR #106) and wasn't on `main` when this ran. Run by `om-auto-fix-issue` (feature route),
in parallel with E06-S02 (#100), so the scope was the engine only: no warehouse or factory state.

## Asked

Add an Investment phase after Trading: simultaneous, closes when every player signals done, every screen names who the
game is waiting for, saved mid-turn games resume, and the phase list is data in one ordered place (S01-1 to S01-5).

## Done

- `TurnPhase` gains `INVESTMENT` and `next()`; the enum's order is the one place the phase list lives (S01-5).
- `Turn.doneInvesting` (public, like `doneBidding`), `endTrading()`, `signalDoneInvesting`, `everyoneDoneInvesting`.
- `Game`: resolving the last good now starts Investment (log "All goods resolved"). `signalDoneInvesting` ends the turn
  when the last player is done: the next turn's Bidding, or `FINISHED` after the last turn (`endTurn` moved there).
  `advancePhase` covers the new phase.
- `InvestmentService` runs the signal inside `GameStore.update` so the turn ends once (A04). `POST /api/games/{id}/investment/done`.
- Persistence: `turn.doneInvesting`, read as empty from older items (S01-4). `TurnResponseDto.doneInvesting`.
- Frontend: `js/investment.js`; the top-bar button reads "I'm done investing" and the bar names who is awaited
  ("Investment — waiting for you, Vega Lines"); s03 says every good resolved while players invest.
- `BACKWARD_COMPATIBILITY.md` §6 and §7 updated for the endpoint, the field and the phase.

## Decisions

- Where the game ends moved from the last good's resolution to the last player's done investing (the spec's states
  checklist says "Game ends after the last Investment"). Tests that resolved the last good and expected the next turn or
  game over now also signal done investing.
- Log: "All goods resolved" is logged when Investment starts; the next turn's "Turn n starts with Bidding…" line when it
  ends. No extra "Investment starts" line (not in the criteria).
- A signal outside Investment, including after it closed, is `409` "Investment isn't open." (a closed Investment can't be
  told apart from one that hasn't started, because the turn has moved on).

## Outcome

Done, with one gap: the Investment top bar was not walked in a real browser (no browser provider was installed here).
`./gradlew build` (547 Kotlin tests, 0 skipped, Docker up; 209 JS tests; ktlint) passed, and a live walk of the API
(two players, a one-turn game, DynamoDB Local) went Trading → Investment → done ×2 → `FINISHED`, with the repeat signal
ignored and a late one refused.
