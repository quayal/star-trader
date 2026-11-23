# Challenge a market position

Date: 2026-11-23. Status: in progress. Source: the creator, 2026-11-23:

> new feature: challenge market position (opportunity phase). Pay 20HT, roll d6. If the result is less than the
> difference between your POL or BUSS connections and the target player (respective connections), the player loses one
> market position (of your choice) and -2 rep. If the roll is the same or higher, the "attacker" loses rep equal to the
> number it missed the roll. Example: Evil Corp POL: 8, Star Dust POL: 4. Evil Corp attempts to challenge Star Dust
> Dictator of CHIPS at Mu Herculis (position, goods, and system are irrelevant for the mechanics). The difference is 4,
> so the successful roll is 1-3. The roll was 5, the reputation loss of Evil Corp is 2. This gets a notification for
> both players, and toast/log for bystanders

## R126: challenging a market position

On their Opportunity turn, after Collect, a player may challenge another player's market position (any tier, any good,
any system):

1. They choose the target player, one of the target's positions, and which connection to fight with: political or
   business (never criminal).
2. They pay 20 HT up front (private, R29); refused without the money.
3. The difference is their level of that connection minus the target's level of the same one. A d6 is rolled.
4. Roll **below** the difference: the target's position drops one tier (Dictator to Wholesaler, Wholesaler to Retailer, a Retailer loses it) and they lose 2
   reputation (creator, 2026-11-23: "loses one market position means demoting one tier").
5. Roll **equal or above**: the challenger loses reputation equal to how far they missed: `roll − difference + 1`
   (difference 4, roll 5: missed 1–3 by 2, so −2).

Reputation losses go through `Player.loseReputation`, so JB (R105, R108) softens them like any other loss (R86).

### Defaults I chose where the creator's note was silent (cheap to change)

- **Once per Opportunity** per player, like sabotage, recruiting and buying reputation.
- **Refused when the difference is below 2**: no d6 can come out below 1, so the challenge could not succeed. The
  refusal says so and costs nothing.
- **The challenger gains nothing** from the position; the target just drops a tier.
- Both the challenge and its result are public (log); the fee isn't (R29).

### Notification (R124's mechanism)

One `Notice` per log line, `players = [challenger, target]`, kind `CHALLENGE_SUCCEEDED` or `CHALLENGE_FAILED` (both are
setbacks for someone). Both players get a card worded for them; everyone else gets the log lines as toasts.

| Outcome | Challenger's card | Target's card |
|---|---|---|
| Succeeded | "Your challenge succeeded" (rise) | "You lost a market position" (alert) |
| Failed | "Your challenge failed" (alert) | "You held your market position" (rise) |

## Placement (standards §13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| The difference, whether a roll wins, what a miss costs, the log wording | two connection levels, a roll | 6 | new `MarketChallenge` |
| The fee and the money check | the challenger's money | 4 | `Player.payChallengeFee` |
| Which position the target holds, lowering it a tier | the target's market positions | 4 | `Player.positionAt`, `Player.withPositionLowered`, `MarketPosition.lowered`, `MarketTier.previous` |
| Once per Opportunity | the turn's list of challengers | 4 | `Turn.challengeRecordedFor` |
| Wording of a position | tier, goods, system | 4 | `MarketPosition.text` |
| Validating the target, rolling, applying both sides, logging | two players, the turn, the log | 7 | `Game.challengeMarketPosition` |
| The request | HTTP | 1, 3 | `POST /games/{id}/challenge`, `OpportunityService.challenge` |
| Whether the viewer already challenged | the turn | 0 | `OpportunityView.challenged` |
| The cards and the form | browser | 9 | `notices.js`, `opportunity.js`, `index.html` |
