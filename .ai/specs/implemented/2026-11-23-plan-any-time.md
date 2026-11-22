# Plan Opportunity and Investment at any time

**Brief:** `.ai/specs/product-brief.md`: R44 (Investment), R61 (buying a ship), R45–R46 (warehouses, factories), R97
(sabotage), R102 (Opportunity, one player at a time), R103 (recruiting).
**Source:** the creator, 2026-11-23: "Make it possible for players to plan their actions in opportunity and investment
phase, e.g. ship hull and modules should be visible in fleet deck, factories and warehouses in infrastructure, agents and
sabotage in opportunity. Only acting should be guarded by being on the turn. Other players' actions can change the plan,
so the plan is not binding, but players should be able to browse options." Then: "I mean plan actions to be taken in opp
and inv. Planning can be done all the time."
**Status:** shipped in the PR that adds this file.

## 📝 TLDR

What a player can do in Opportunity and Investment can be browsed and filled in at **any time while the game runs**.
Only the action itself (Buy ship, Confirm a warehouse or factory purchase, Sabotage, Recruit) waits: for Investment, or
for the player's own Opportunity turn. No rule changes; the server refuses out-of-phase actions exactly as before.

## 📋 Acceptance criteria

1. **Fleet:** the "Buy a ship" form (shipyard, hull table, modules, crew, name, cost line) shows in every phase of a
   running game. Outside Investment, Buy is disabled and the line above says "Plan a ship here at any time; you can buy it
   during Investment." Refit, repair and sell of owned ships stay Investment-only.
2. **Infrastructure and System details:** the warehouse and factory rows with their Add/Build buttons show in every phase.
   The dialog opens with the cost, the most the money allows and the factory cap; outside Investment, Confirm is disabled
   and the dialog says "You can buy this in Investment. Prices and your money may have changed by then."
3. **Opportunity tab:** the Sabotage panel (targets, blame, cost, modifiers) and the recruit form show in every phase.
   Their buttons are enabled only on the player's own Opportunity turn; outside Opportunity the line under each says "You
   act on your Opportunity turn, after Trading. Plan it here now; ships may move and targets change by then."; in
   Opportunity it is the existing whose-turn line.
4. **Not in the lobby or after the game:** nothing to plan; `you.invest` is null then.
5. **API:** `you.invest` is sent whenever the game is running (was: Investment only). Additive in shape; recorded in
   `BACKWARD_COMPATIBILITY.md`.

## 🧭 Placement (`.ai/engineering-standards.md` §13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| What Investment sells is visible to a member whenever the game runs | game status, turn | 0 (what a player may see) | `Game.viewFor` via `Game.isRunning` |
| Buying waits for Investment | game status, phase | unchanged | `Game.canInvest`, the services' `withInvestment` |
| Sabotage and recruit wait for the player's Opportunity turn | turn's opportunist | unchanged | `Turn` (server), `waitTurnText` (wording only) |
| Planning vs acting in the UI | `you.invest`, `turn.phase` | frontend | `investment.js` `canPlan` / `canBuy`, `shipyard.js` `buyClosedText` |
