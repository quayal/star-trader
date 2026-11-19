# Playtest feedback: battle dialog, rolls in results, evasion first, Move goods on the system page

Date: 2026-11-19 · Status: in progress · Source: creator's playtest notes (2026-11-19)

## Asked for

1. Don't announce "Turn 2 of N starts with Bidding; turn 1's offers have expired". The log already groups by turn and the
   top bar shows the turn, so the line (and its toast) is noise.
2. Move goods from the system page, not the Fleet tab, and pick the units with a slider instead of typing a number.
3. A ship that evades an interception: its owner is told it evaded *before* the landing dialog asks where it ends up.
4. The whole battle in its own pop-up (modal dialog), not a panel on the Deck: complete focus on the battle.
5. The battle results show the rolls (firepower + d6 vs shields → hits, and the rocket d6), not only the log.

## Decisions

- **1.** `Game.withNextTurn` no longer logs a line; `Turn.startedText` goes. The Game log's turn groups already say
  which turn it is. Nothing else (offers being per-turn) changes.
- **2.** The Move goods panel moves to the system page (`#system-panel`), limited to the viewer's holders at that
  system (`cargoHolders(game, playerId, systemId)`). It shows only when the viewer has two holders there (two ships in
  port, or a ship in port and the warehouse). The Infrastructure pointer and the Fleet tab point to a system's page
  instead. Units is an `<input type="range">` from 1 to the most that can move (R58: what From holds, capped by To's
  free space), starting at that most, with the chosen number shown next to it. The server still checks (R58).
  Other number fields (bids, offers, hits) stay as they are: "try sliders" was asked about moving goods.
- **3.** While the viewer has an unseen interception card about their own arriving ship, the landing dialog waits; the
  card ("You evaded an interception", with both rolls) shows first, and closing it opens the landing dialog. The card
  already exists; only the order changes.
- **4.** The fight panel becomes `#fight-dialog`, a modal `<dialog>` holding the same content (ships, what's asked,
  waiting for whom, the one form). It opens for a player who has a decision in the interception or whose ships are in
  the battle, and stays open for the whole battle. "Hide" closes it; a "Return to the battle" button on the Deck
  reopens it, and it reopens on its own whenever the viewer has a new step (orders, hits, landing). Escape counts as
  Hide. Bystanders get a short panel on the Deck (title, who's waiting) with no form. The battle report and the
  notification cards open on top of / after it as today.
- **5.** `SalvoResult` keeps the salvo's rolls (`SalvoRolls`: firepower, d6, shields, rocket d6 or null), null for a
  salvo at a ship that broke away and for reports saved before this change. The report DTO sends them; each report
  line gets a second line "6 + d6 4 = 10 vs shields 2 → 2 hits · rocket d6 3: holds".

## Placement (standards §13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| A salvo result carries the rolls that made it | the salvo's firepower, rolls, shields | 8 | `SalvoRolls` next to `SalvoResult`, built by `SalvoResult.of(salvo…)` from `Salvo.rolls()` |
| Rolls cross to JSON / DynamoDB | `SalvoResult.rolls` | 1 | `SalvoResultResponseDto`, `BattleReportItem` (map only) |
| No log line when a turn starts | the log | 7 | `Game.withNextTurn` (removal) |
| Which holders can move goods at a system, slider bounds, dialog ordering | browser state | 9 | `cargo.js`, `battle.js`, `notices.js`, `index.html` (formatting and UI state only; the server decides) |

## Backward compatibility

Additive: `results[].rolls` is new and nullable; a saved report without rolls loads with `rolls = null`. The removed
log line was public text only.
