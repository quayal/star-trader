# Run: implement the Opportunity phase, loans, sabotage and agents

**Asked:** `/om-auto-implement-spec the four new specs`: `.ai/specs/2026-11-20-opportunity-phase.md`, `2026-11-20-loans.md`,
`2026-11-20-sabotage.md`, `2026-11-20-agents.md`.

**Done:** one implementation PR for all four, on `feat/opportunity-loans-sabotage-agents`. The creator prefers whole features
per PR, and three of the specs depend on the Opportunity phase.
- Domain: `TurnPhase.OPPORTUNITY` between Trading and Investment, played in turn order, Collect moved into it, ships leave
  the yard when Investment opens, and the phase waits for owed sabotage hits. New packages `game.loan`, `game.sabotage`,
  `game.agent`. Loans are settled at Investment close (unpaid-interest penalty, then collection), and standings use final
  money. Factories can be sold. Sabotage uses the creator's table, with Silent, Shorty and JB. Agents cover recruit, move,
  sell, death in Space and every effect hook: Novak (jump), Wolf (initiative), Trigger (intercept), X (weapons), JB
  (every reputation loss), Limpy (a Dictator question in Trading).
- Store: new item classes (`LoanItem`, `RecruitedAgentItem`, `SabotageItem`, `AgentOfferItem`) and new attributes with
  defaults, so no migration.
- API: the collect endpoint moved to `/opportunity/collect`; the new endpoints are listed in BACKWARD_COMPATIBILITY.md.
- UI: Opportunity, Sabotage, Agents and Loan cards on Infrastructure, the top-bar done button, the Limpy question on
  Trading, agent counts on Corporations, a disabled-factory note, the loan line on Assets, and a sabotage notice card.

**Decisions:**
- Limpy is asked as a separate step before the good resolves (`Turn.limpyAsk`), so `Resolution` stays untouched.
- A ship's sabotage hits reuse battle hit placement. What's thrown off goes to the warehouse there as far as it fits.
- Hits beyond what a ship can survive destroy it at once, as in battle.

**Verified:** `./gradlew check` green with Docker up. A browser walk on a local app took a two-player game to Opportunity:
recruit, sabotage, out-of-turn hit placement, the out-of-turn done refused, Investment opening, and a loan.
Agent identities stayed private in the other player's JSON.

**Outcome:** done; PR open. Known follow-up: move the sabotage effect on the target (`Game.withSabotageEffect`) into
`Player`.
