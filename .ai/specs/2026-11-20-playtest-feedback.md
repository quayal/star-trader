# Playtest feedback, 2026-11-20

**Brief:** `.ai/specs/product-brief.md`: R25 (Trading resolution), R27 (initiative), R28 and R43 (market positions), R29
(private money), R58 (moving goods), R67–R68 and R71 (crew and jump modifiers), R74–R78 (battle), R85–R88 (reputation),
R96 (selling factories), R104–R105 and R108 (agents), D15 (the screen follows the phase).
**Source:** the creator's notes from the playtest of 2026-11-20 (vault note `10-projects/Trader/Intel.md`, below the tables)
and their list of the same day.
**Status:** proposed, not started.

## 📝 TLDR

Thirteen fixes from today's playtest. Two change rules:
- **Agents move at any time**, like cargo, not only on their owner's Opportunity turn (R104).
- **Wolf's initiative bonus is shown** in the initiative line, which reveals him (R105).

The rest make information easier to find:
- a battle summary;
- crew bonuses next to crew classes;
- jump modifiers on the map;
- a new **Guide** tab with every agent, each phase and the key modifiers;
- market positions and a money history on the Deck;
- reputation growth and bonus next to reputation;
- a card for an offer that couldn't trade;
- the sell-factory button next to buy;
- a proper agent Move button;
- the same red Pass button everywhere.

## 📋 Acceptance criteria

### Agents
1. **Moving agents at any time** (creator: "like cargo"). This replaces R104's "on the owner's Opportunity turn". At any
   moment of a running game, in any phase and whoever's turn it is, a player may:
   - move an agent **from a ship onto the Port or Planet** where the ship is;
   - move an agent **from its own place in a Port or on a Planet onto a ship** at that same location;
   - move an agent **between two of their ships** at the same system and location.

   Two places still block it: a ship in **Space** (an agent can't be on its own there, and transfers in Space aren't
   allowed, matching cargo), and a ship **in a battle** that's under way. Public counts (R105) change as before.
2. **The Move control** for agents uses the standard button style from `docs/design-system.md` (`.btn`, as Move goods
   does), next to a labelled destination select. The bordered `quiet-button` goes. Agents are moved from the Fleet ship
   cards and the Agents card, and from now on in every phase.
3. **Where your agents are:** the Agents card lists each of the viewer's agents with its name, effect and **current
   place**, for example `Novak, aboard Starfarer, Tau Ceti Port` or `Silent, on his own in Mu Herculis Port`. The same list
   shows on the **Deck** in a "Your agents" panel. On the map, the viewer's own agents also appear in the system panel,
   named, which only the viewer sees.
4. **Wolf in the initiative line:** his +4 is shown wherever the roll is shown: the log, the initiative result and the
   turn-order panel. For example: `Vega Lines: bid 5 + roll 3 + Wolf 4 = 12`. Showing him **reveals** that Vega Lines has
   Wolf, as with every acting agent (R105, criterion 11 of the agents spec). Today the bonus is added to the total but not
   shown (`5 + 3 = 12`), which was a bug against R105.

### Battle
5. **Battle summary:** when a battle ends, every player who took part, and every player watching, gets a **summary dialog**
   (and a log entry they can reopen) with:
   - **each round:** every ship's order (fire at whom, rockets or not, break away), the salvo rolls, hits, and where the
     hits were placed, with modules damaged or destroyed;
   - **the aftermath:** ships destroyed (and agents killed aboard, by count, R107), ships that broke away, where the
     survivors were placed, and **reputation changes** caused by the battle (interception tries, firing, R86), with each
     roll.

   It replaces the need to piece the battle together from the per-round results dialogs, which stay as they are.

### Ships
6. **Crew bonuses next to the crew class**, wherever a class is chosen or shown: buying a ship, replacing a crew, and the
   Fleet ship card. For example: `A · 20 HT per crew · jump +5 · intercept +1`, `B · 10 HT · jump +3`,
   `C · 6 HT · jump +1`, `D · 2 HT · jump +0 · intercept −1`. The bonuses come from the server's crew data (R68, R71), not
   from copies in the page.
7. **Jump modifiers on the map:** the middle of the Deck's map, inside the hexagon ring, shows a short list:
   `Crew A +5 · B +3 · C +1 · D +0`, `Safe jump +2` and `Novak +4`, plus any modifier active this turn. When one of the
   viewer's ships is selected for a jump, the list also shows that ship's own total (`Starfarer: +5`).

### Guide
8. **A new Guide tab**, a destination beside the others and reachable in every phase. It's static reference, served from
   the server's own data so it can't drift from the rules:
   - **Each phase** in order, with what a player can do in it (one or two lines each).
   - **Agents:** all of them with their effects, including those not in the pool yet, marked `Not in the game yet`.
   - **Key modifiers:** jump (crew, Safe jump, Novak), interception (crew, Battle comm, Trigger), initiative (Wolf),
     sabotage (criminal connections, Shorty, the table), and reputation gains and losses, including the end-of-turn +3 and
     the bonus table (R86, R88).

### The Deck
9. **All your market positions on the Deck:** a "Your market positions" panel listing each position the viewer holds,
   with system, good and tier (`Tau Ceti · ISO · Wholesaler`). It's empty with `No market positions yet.`
10. **Recent money history on the Deck:** a "Your account" panel with the viewer's last money movements. Each one has the
    turn, the reason and the amount, **credits in green and debits in red**, using the design system's status tokens.
    Examples: `T4 · Sold 6 ISO at Tau Ceti · +72`, `T4 · Loan interest · −3`. The 10 newest show, and `Show all` expands
    the turn's and earlier ones. It's private (R29). This needs a **server-side ledger**:
    - every change to a player's money goes through one place on `Player` that records the turn, a reason and the amount;
    - this covers trades, wholesale, bids, purchases, sales, loans and interest, sabotage, recruiting, reputation buys,
      bonuses and events.

### Reputation
11. **Next to reputation** on the Corporations card: the viewer's next end-of-turn change (`+3 at the end of the turn`, or
    `No +3 above 17`), and the bonus the current value earns (`Bonus at 27: 10 HT per turn`, or the next threshold:
    `Bonus from 25`). The full table is in the Guide.

### Trading
12. **An offer that couldn't trade** gets a **notification card to acknowledge**, using the existing notification cards
    and toasts. That covers a winning offer whose tradeable amount was 0, because of the S/D roll or nothing left, and an
    offer that lost. Example: `Your ISO buy at Tau Ceti didn't trade: the market moved past your price.` It's one card per
    offer, and it stays until dismissed.

### Infrastructure
13. **Sell factory** sits next to **Buy factory** in the factories table row and card, the same size and style. It's still
    available only in Investment (R96).

### Buttons
14. **Pass is always the red button** (`.btn .btn--danger`), never small link-like text: the Dictator's Pass (today a
    `text-action`), and every other decline of the same kind (Pass, Don't intercept, Decline an offer). `text-action`
    remains only for links that leave nothing undone, such as `Copy link` and `Retry now`.

## 📝 Proposed Solution

Mostly frontend.
- **Agent moves:** the server drops the Opportunity-turn gate in `Player`'s agent moves and checks location instead, as
  cargo moves do (R58).
- **Wolf:** the initiative result carries each player's bonuses with the agent's name, and the log line prints them.
- **Ledger:** `Player.pay` and `Player.receive` take a reason. The direct `copy(money = …)` writes in `Player` go through
  them, and a `MoneyLedger` value keeps the movements, with the newest kept and older ones trimmed per game.
- **Battle summary:** `Battle` already logs rounds and results. A `BattleSummary` is built from them when the battle ends,
  and shown from the log.
- **Guide:** a `GET /api/rules` endpoint returns the phases, agents, crew classes and modifier tables from the domain enums.
  The Guide, crew labels and map legend render from it.

## 📝 Edge Cases

- **Moving an agent during a battle** at another system is allowed. Only ships in the battle are locked.
- **Wolf's owner has no other tell:** the reveal is the point, since the creator wants it open.
- **The ledger in a game saved before this ships:** it starts empty, and the panel says `History starts from turn N`.
- **An offer withdrawn in Bidding** isn't "couldn't trade" and gets no card.

## Defaults I chose (override any)

- Agent moves follow cargo's rules: **same location, not in Space, not in a battle**, in any phase.
- The Deck shows the **10 newest** money movements, with the rest behind `Show all`.
- An offer that **lost** also gets a card, not only one that won and couldn't fill.
- The Guide is **reference only**, with no per-player state.
- The jump-modifier list sits **in the map's centre**, with the selected ship's total when one is chosen.

## 📋 Brief changes (with this spec)

- **R104:** agents board, step off and change ships at any time in a running game, at the same location, never in Space
  or in a battle under way (creator, 2026-11-20).
- **R105:** Wolf's bonus is shown in the initiative line, which reveals him.
- **R29:** the player's own money history is shown to them, never to others.
