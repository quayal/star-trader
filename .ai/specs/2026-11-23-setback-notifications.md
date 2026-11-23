# Setback notifications, and opportunities in action

Date: 2026-11-23. Status: in progress. Source: the creator, 2026-11-23:

> seizing a ship while landing on the planet also needs a notification

> generally, if the game roll results in anything else than the player wanted, it needs a separate notification. So,
> being forced into the port or losing illegal cargo also counts.

> opportunity in action (like furs bought and on ship) should be shown in opportunity deck

## R124: a card for every roll that went against the player

Each setback's log lines (with the reputation lines that follow it) carry a `Notice` whose `players` are the ones it
happened to. They get one card, worded for them and marked as an alert; everyone else gets the same public lines as
toasts, as for any other public line.

| Roll | Setback | Notice kind | Card goes to |
|---|---|---|---|
| Jump (R68) | failed | `JUMP_FAILED` | the jumper |
| Landing (R116, R117) | forced into the Port or held in Space, cargo confiscated or not | `LANDING_FORCED` | the owner |
| Landing | seized, or forced while military (ship confiscated) | `SHIP_SEIZED` | the owner |
| Expedition (R114) | lost on a 1 | `EXPEDITION_LOST` | the owner |
| Pirates (E12) | ship lost | `LOST_TO_PIRATES` | the owner |
| Position roll (R43) | rose nothing | `NO_PROMOTION` | the trader |
| Position roll (R43) | another's roll demoted a Dictator | `DEMOTED` | the demoted |

Not setbacks (a size, not a failure): initiative, the market counter, buying reputation, black-market price, expedition
pay, sabotage and reputation-loss amounts. Interceptions, battles and sabotage on one's assets already had cards.

**Not covered yet:** a failed recruitment (R103) and a saboteur's failed sabotage are private, so a public notice can't
carry them (R101 hides the saboteur). They need a private card from the player's view, like "Your offer didn't trade".

## Opportunities in action

The Opportunity screen gets a panel, "Your opportunities in action", shown in every phase while anything is under way:
goods bought through an opportunity and still aboard (R112), with where they sell; delivery jobs (R114); ships away on
the expedition, with their return turn. Each line names the ship, where it is now and what comes next. Built in
`js/opportunities.js` (`opportunitiesInAction`) from `you.ships` and `you.expeditions`, which the server already sends.
