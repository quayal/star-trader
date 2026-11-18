# Notifications: pop-ups for interceptions, battles and your moves; toasts for the rest

- Date: 2026-11-18
- Source: the creator after the 2026-11-18 playtest; the player who intercepted didn't notice the interception failed.
- Status: in progress (stacked on PR 161, which adds structured data to log entries)

## What the creator asked

- More in-your-face notifications as pop-up windows, like "where do you want to land".
- Interceptions: failed, succeeded, "Evil Corp tried to intercept you with Meridian", the rolls, "Wayfarer evaded them".
- Blocking actions in general are pop-ups saying what's going on.
- Players not in a battle are told about its rounds as a queue of notifications they click through.
- The UI a bit more active and engaging.
- Infrastructure: drop the factory intro "Each unit adds one unit of output a turn, …".

Answers (creator, 2026-11-18): no sound; battles and interceptions go in the queue, everything else fades; one PR.

## Decisions

| # | Decision |
|---|---|
| 1 | **Event cards** (a modal `<dialog>`, like landing): every interception try, battle start, battle round, destroyed ship and battle end. Worded for the viewer: the interceptor ("Your interception failed: Wayfarer evaded Meridian"), the intercepted ("Evil Corp tried to intercept your Wayfarer with Meridian — you evaded them"), or a bystander ("Evil Corp tried to intercept Vega Lines's Wayfarer with Meridian — it evaded them"). Each try shows both rolls. |
| 2 | Several cards queue in one dialog: "Next" (or "OK" on the last) and "Dismiss all", with "2 of 5". |
| 3 | A battle round card is for bystanders only: players fighting already get the battle report dialog. |
| 4 | Cards are per browser: the dialog remembers, per game, how far into the log it has shown (`localStorage`). The first time a browser opens a game it starts from the end, so an old game doesn't replay its history. |
| 5 | **Your-move cards**: when the game starts waiting on the viewer for a blocking step (choose a place in the turn order, jump, decide an interception, battle orders, place hits, choose a trade amount, the Dictator's offer, the wholesale step), a card says so with a button that takes them to the screen where it's done. Once per step (keyed by game, turn and step), and not for "resolve the next good", which repeats for every good. |
| 6 | Order: the landing dialog and the battle report come first; event cards next; a your-move card last. Only one modal is open at a time. |
| 7 | **Toasts** for every other new public log line (jumps, landings, trades, prices, phase changes): bottom right, fade after 6 s, at most 3 at once plus "+N more in the Game log". `aria-live="polite"`; no animation under reduced motion. Only lines that arrive while the page is open. |
| 8 | No sound. |
| 9 | The factory intro sentence on Infrastructure is removed. |

## Data

Log entries gain an optional structured `notice` next to `trade` (PR 161), so the client never parses log text:
`Notice(kind, system, players, round, interception)`, `kind` one of `INTERCEPTION`, `BATTLE_STARTED`,
`BATTLE_ROUND`, `SHIP_DESTROYED`, `BATTLE_OVER`; `players` are the players involved (the interceptor and the
intercepted, or everyone with a ship in the battle); `round` for battle rounds; `interception` for a try:
`InterceptionRoll(interceptorEmail, interceptorShip, interceptorRoll, arrivingEmail, arrivingShip, arrivingRoll, success)`,
the rolls as the log already words them ("Meridian (2 + d6 3 + 1 = 6)"). A reputation line a try or a round causes
carries the same notice, so it lands on the same card. Public, like the lines. Older entries have none.

Response: `log[].notice: { kind, system, systemName, playerIds, round, interception: { interceptorId, interceptorShip,
interceptorRoll, arrivingId, arrivingShip, arrivingRoll, success } | null } | null`. Additive.

## Placement (§13)

| Rule | Data | Row | Where |
|---|---|---|---|
| Who a battle involves | the battle's ships | 4 | `Battle.landers()` (exists) |
| A try's notice | interceptor, arriving ship, rolls | 7 | `Game.withInterceptionRolled` builds it beside the line it already logs |
| Round, destroyed, over, started notices | battle + log lines | 7 | where `Game` logs them today |
| Card wording, grouping, queue, seen pointer | `log[].notice`, viewer id | 9 | `js/notices.js` (pure, tested) |
| Your-move card | existing decision helpers | 9 | `js/notices.js`, reusing `fightDecisionText`, `deckDecisionText`, `decisionText` keys |
| Toast lines | `log` | 9 | `js/notices.js` |
