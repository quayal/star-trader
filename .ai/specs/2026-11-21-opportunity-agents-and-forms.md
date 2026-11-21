# Opportunity tab: agents and opportunity forms, 2026-11-21

**Brief:** `.ai/specs/product-brief.md`: R103 (recruiting), R104 (moving agents), R105 (an agent's identity is its
owner's), R106 (selling agents), R111–R112 (opportunities).
**Source:** the creator's notes after playing PR 173's Opportunity tab.
**Status:** implemented on `feat/opportunity-tab-agents`.

## 📝 TLDR

The Agents panel took more than half the Opportunity tab. It shrinks:
- **Selling** is a button beside each agent, opening a dialog (to whom, price).
- **The buyer gets a pop-up** to buy or decline.
- **Where a recruit starts is chosen after he joins**, since the right place depends on who he is.

A **status strip** in the top bar keeps the viewer's money, reputation (with its effect) and connections in view.

Learned opportunities come **first**. Their **Take** button isn't active when no ship can take them, and the title and
the note read as separate lines.

## 📋 Acceptance criteria

1. **Compact Agents panel.** It holds the viewer's agents, the offers made or received, and one row to recruit: pay,
   Recruit, and the chance. The old "Where the agent starts" select and the separate "Sell an agent" form go.
2. **Sell beside the agent.** On the viewer's Opportunity turn, each of their agents has a **Sell** button next to Move.
   It opens a dialog titled "Sell Wolf" with **To** (another player) and **Price** (whole HT, 0 or more; a bad price keeps
   the dialog open with the error), then Offer or Cancel. The rules are R106's, unchanged.
3. **The buyer's pop-up.** An agent offered to the viewer pops up once, when no other dialog is open: "Orion Freight
   offers you an agent for 25 HT (Planet at Mu Herculis)", with **Buy**, **Decline** (red, as every pass) and **Later**.
   Later leaves it in the Agents list with Buy and Decline, as before. The agent stays unnamed until bought (R105). The
   pop-up closes if the offer is answered elsewhere or Opportunity closes.
4. **Placed after recruiting.** Recruiting no longer asks where the agent starts. He joins at the first valid start, and
   a dialog opens at once: "Wolf joins you", his effect, and **Where he starts** with every start R103 allows (aboard a
   ship out of the yard, or on his own in the Port or on the Planet of a system with a ship or warehouse), then Place or
   Later. Until placed, the agent shows "Just joined: choose where he starts." with a Place button instead of Move.
   - Server: `RecruitedAgent.placeableIn` is the turn he was recruited in, until placed. `Player.placeNewAgent` places
     him anywhere `checkAgentStart` allows, once, and only in that turn. `POST /api/games/{id}/agents/place` takes
     `{ agent, ship }` or `{ agent, system, location }`. The agent DTO says `placeable`. The field is stored with the
     agent; older items read as placed.
   - The recruit line in the log no longer names a system ("Orion Freight recruited an agent"), since the place comes
     after.
5. **Opportunities first.** On the Opportunity tab, the learned opportunities come right after the output to collect,
   before whose turn it is, agents and sabotage.
6. **Opportunity forms.** The title, the facts and the "Needs a ship …" note are separate lines (the note no longer runs
   into the title's full stop). With no ship where it's taken, **Take** is disabled and the note shows as a warning. A
   military ship's opportunity stays takeable without one, since its hull appears on the Planet.
7. **Status strip** (creator: "this info should be readily available to the player at all times"). On every screen of a
   running game, under the phase heading and at the normal value size (not the small grey subtitle):
   `Money 240 HT · Reputation 27 (+3 next turn · +15 HT bonus) · Connections POL 2 · BUSS 4 · CRIM 1`.
   - The reputation note comes from the server's `reputationRecovery` and `reputationBonus`. With no bonus yet it says
     where one starts ("bonus from 25"). A recovery that won't happen is left out.
   - Money moves out of the subtitle, which keeps the initiative order.
8. **Your agents named on your Corporations card** (creator: "the player should know what agent this is"). The viewer's
   own card lists each of their agents by name, effect and place: `Wolf (+4 on your initiative roll, wherever he is): on
   his own in Port at Sigma Draconis`. Other players' cards keep the public counts (`1 agent in Port at Sigma Draconis`),
   since only an agent's owner knows who he is (R105).
9. **Hulls and modules in the Guide** (creator: "so players can plan ahead when not in Investment"). Two full-width
   tables after the modifiers:
   - **Hulls:** every hull with price, module capacity, crew, cargo, weapons, shields, intercept and repair cost, with
     notes for military ones ("Military (opportunity only)") and aerodynamic ones.
   - **Modules:** every module with price, crew, weapons (rockets in brackets), cargo and passengers, with notes for
     military ones and Safe jump ("Takes no module space · jump +2"). Under it, what fitting costs in crew per class.
   - `/api/rules` now sends `hulls` and `modules` from the `Hull` and `Module` tables, so the Guide can't drift from them.

## Verification

Kotlin tests: placing a new agent once, where a start is allowed; the recruit's placeable turn; the agent item's round
trip. JS tests: `takeable`. Browser walk on a local instance:
- Ann recruited at 60 HT; Wolf joined and the place dialog opened with six starts.
- Placed on Mu Herculis's Planet, he showed there with Sell.
- The sell dialog refused "abc", then offered him to Vega Lines for 25 HT.
- In Vic's view the pop-up opened with the unnamed offer, and Buy gave Vic Wolf.
- The Agents panel was 230 px with an agent listed.

The status strip on the Command deck and the Opportunity tab read `Money 300 HT · Reputation 20 (bonus from 25) ·
Connections POL 2 · BUSS 1 · CRIM 1`; `statusItems` and `reputationEffect` have JS tests, as does `corporationAgentLines` for both cards. The Guide showed 13 hulls and 8 modules; `RulesControllerTest` and the
Guide's JS test check the rows. The furs form's disabled Take is covered by the unit test, not the walk, since the walk drew no furs tip-off.
