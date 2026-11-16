# E06 — Factories, warehouses and the Investment phase

**Brief:** `.ai/specs/product-brief.md` — R39, R40, R42, R23, R10, R16, R29, D07, D09, D10, plus the new R44–R50 and D11 added with this spec, and D12 (2026-11-16) for where Investment lives.
**Siblings:** E03 Bidding, E04 Ship-jumps, E05 Trading (`2026-10-29-e05-trading-phase.md`). Screen IDs are defined in the E03 spec.
**Prototype:** none yet. The **Infrastructure** screen (s10, D12) is new; System details already has a foldable "warehouses and factories" section (D07) that this epic fills in. s09 is Game over (E05-S08).

## 📝 TLDR

After Trading, every turn has a fourth phase, **Investment** (phase 7 of the board game's turn; the phases between Trading and Investment are not built, and nothing here depends on them). Players act at the same time, and the phase closes when every player signals done (D09).

In Investment each player does two things, **in this order**:

1. **Collect.** Every factory the player owns produces its output. The player places it into their warehouse or into their ships docked at that system. Whatever doesn't fit, or isn't placed, is lost.
2. **Invest.** With a ship at a system, the player may buy warehouse blocks there and add units to factories there.

Collect before invest is what makes planning matter: a warehouse bought this turn can't catch this turn's output, and a factory bought this turn first produces next turn. A player who wants to keep what a factory makes has to build the warehouse a turn earlier.

When production later becomes its own phase 6, Collect moves there. Investment keeps only step 2.

**Where it happens (D12).** All of Investment is on one dedicated **Infrastructure** screen (s10): collecting output, buying warehouse blocks and factory units, and the "Done investing" button. Everyone is taken there when Investment starts. The Deck carries no Investment controls, so it stays as uncluttered as it is in the other phases. E06-S03 first built Collect on the Deck; E06-S08 moves it.

Money still decides the winner (R39). Factories and warehouses are assets and don't count towards the score; selling them is a later feature.

## 📝 Rules

| Id | Rule |
|---|---|
| R44 | A turn is Bidding, Ship-jumps, Trading, then Investment. Investment is simultaneous and closes when every player has signalled done; nothing has a deadline (D09). The phase list is ordered and open, so a production phase can later be inserted before Investment. |
| R45 | **Warehouse.** A player has at most one warehouse per system; its capacity is a number of blocks. One block costs 20 HT and holds 10 cargo spaces, like one ship (R42). Buying needs one of your ships at that system. Buying adds blocks to the existing warehouse, or creates it. |
| R46 | **Factory.** A player has at most one factory per good per system; its size is N units produced per turn. Where a good can be produced: Gamma POLY; Sigma FOOD and ISO; Mu CHIPS; Tau ISO. Beta and Epsilon have none. Buying needs one of your ships at that system. Buying N units costs N × (5 × the good's current price at that system + 10) HT, paid once, and adds N to the factory's size, or creates it. There is no size cap, only the player's money. |
| R47 | **Collect.** At the start of Investment, a player with factories sees each one's output (its size) and places it. Destinations are their warehouse at that system and their ships docked there, and the player may split an amount across them. POLY and ISO take cargo space; units beyond the free space are lost (R16). CHIPS and FOOD take no space, but need a warehouse or a ship of theirs at that system or they are lost. Anything the player doesn't place when they confirm is lost. A player with no factories skips Collect. |
| R48 | **First use.** A warehouse or factory bought in Investment is bought after the buyer's Collect, so it first matters next turn: a factory first produces next turn, a warehouse first catches output next turn. |
| R49 | **Trading with a warehouse.** A warehouse at a system counts as presence there for Trading, including wholesale (R37). The trader chooses which of their ships and which warehouse at that system load and unload, and may split the amount. A sell is capped by what the chosen ships and warehouse hold. This extends R23 and R42. |
| R50 | Everyone sees every warehouse and factory (owner, system, good, size or blocks). Builds and collected output are public Game log events; the log doesn't show what they cost, because money is private (R29). Nobody starts with a factory or warehouse (R40 is unchanged). |

## 📋 Stories and acceptance criteria

### E06-S01 — A turn also has an Investment phase

1. [R44] When Trading finishes for the last good, every player's client moves to Investment and the top bar reads "Investment".
2. [R44, D09] Each player can signal "Done investing". When every player has, the next turn starts with Bidding. After the last turn, the game ends as before (E05-S08).
3. [D09] Every screen names who the game is waiting for: "Waiting for Vega Lines".
4. [R44] Existing games keep working: the phase order after Trading is the only change, and a game saved mid-turn before this change resumes in its saved phase.
5. [R44] The phase list is data in one ordered place, so adding a phase between Trading and Investment later changes only that list and the new phase's own rules.

### E06-S02 — A player can own warehouses and factories, and everyone sees them

1. [R45, R46] A player's warehouses (system, blocks) and factories (system, good, size) are part of the game state and persist with it (E02-S03).
2. [R50] System details shows, per system, every player's warehouse and factories there, to every player. The existing foldable section is used.
3. [R46] System details also says which goods the system can produce, or "No factories here" for Beta and Epsilon.
4. [R50, R40] A new game has none.
5. [R29] Money stays private; nothing shown here reveals anyone's balance.

### E06-S03 — Collecting output at the start of Investment

*Shipped on the Deck. From E06-S08 the same panel is on the Infrastructure screen (s10); the criteria below describe its behaviour, not its place.*

1. [R47] When Investment opens, a player with factories sees "Collect your output" listing each factory: system, good, units.
2. [R47] For each factory they choose how many units go into which of their warehouse and docked ships at that system. The screen shows each destination's free space. CHIPS and FOOD show no space, only whether there is somewhere to keep them.
3. [R47, R16] POLY and ISO beyond the chosen destination's free space are lost, and the screen says so before the player confirms.
4. [R47] CHIPS and FOOD with no warehouse and no ship of theirs at that system are lost, and the screen says so before confirming.
5. [R47] On **Confirm**, the chosen amounts are stored, everything else is lost, and the invest step opens. A player can't build before they have confirmed.
6. [R47] A player with no factories goes straight to the invest step.
7. [R48] The warehouse blocks and factory size used are those owned at the start of this Investment, so nothing bought this turn counts.
8. [D09] If a player hasn't confirmed, the game waits and names them.

### E06-S04 — Buying warehouse blocks

1. [R45, s10] After collecting, a player with at least one ship at a system sees "Add warehouse blocks" for that system on the Infrastructure screen, with a number of blocks and the total cost at 20 HT each.
2. [R45] Buying needs enough money. A player with no ship at a system can't buy there, and the option isn't shown.
3. [R45] On confirm, the money is taken and the blocks are added to the existing warehouse or create it. The player's capacity there shows 10 cargo spaces per block.
4. [R48] The new capacity isn't used until next turn's Collect (this turn's Collect has finished) but counts for Trading next turn.
5. [R50] A public Game log line: "{corp} added {n} warehouse block(s) at {system}".
6. [R44, D09, s10] The Infrastructure screen says "Everyone invests at the same time. The turn moves on once everyone is done." so a player knows they don't wait for anyone else to build. It shows the whole time Investment is open, including after the player has signalled done. Whichever of E06-S04 and E06-S05 lands first adds it; the other keeps it and doesn't repeat it.

### E06-S05 — Building or extending a factory

1. [R46, s10] After collecting, a player with a ship at a system that can produce at least one good sees "Add factory units" on the Infrastructure screen for each producible good, with a number of units and the total cost.
2. [R46] The cost shown is N × (5 × the good's current price at that system + 10) HT. The price is the one on the Deck when the player confirms, and the screen shows it next to the cost; it can't change during Investment.
3. [R46] On confirm, the money is taken and the factory's size grows by N, or the factory is created at size N. There is one factory per player per good per system.
4. [R46] Buying needs enough money. There is no maximum size.
5. [R46] Beta and Epsilon, and goods a system can't produce, offer nothing.
6. [R48] The factory first produces at the start of next turn's Investment.
7. [R50] A public Game log line: "{corp} added {n} unit(s) of {good} production at {system}".
8. [R44, D09, s10] The Infrastructure screen says "Everyone invests at the same time. The turn moves on once everyone is done." so a player knows they don't wait for anyone else to build. It shows the whole time Investment is open, including after the player has signalled done. Whichever of E06-S04 and E06-S05 lands first adds it; the other keeps it and doesn't repeat it.

### E06-S06 — A warehouse lets you trade without a ship

1. [R49] In Trading, a player with a warehouse at a system but no ship there can still place offers for goods there and trade them when they win.
2. [R49] When trading (including wholesale), the player chooses which of their ships and which warehouse at that system load or unload, and may split the amount, as E05-S04 does for ships.
3. [R49, R10] A sell is capped by what the chosen ships and warehouse hold.
4. [R49, R16] A buy is capped only by money (R12). POLY and ISO beyond the chosen destinations' free space are lost; CHIPS and FOOD fit whenever any ship or warehouse is chosen.
5. [R23] With neither a ship nor a warehouse at the system, bought goods are still lost as today.

### E06-S07 — The Game log shows builds and collected output

1. [R50] After a player confirms Collect, the log shows "{corp} collected {n} u of {good} at {system}" per factory, with "(m lost)" if anything was lost.
2. [R50] Builds are logged as in E06-S04 and E06-S05. No line shows a price or the player's money.

### E06-S08 — An Infrastructure screen for the whole Investment phase

1. [D12, s10] A destination "Infrastructure" sits beside Deck, Trading and Game log, from the moment a game has started, and is marked as the current one when open.
2. [D12, D07] When Investment starts, every player is taken to the Infrastructure screen, as for Trading. A player who goes back to another screen isn't pulled back.
3. [D12, R47] The Collect panel (E06-S03-1 to S03-8) is on this screen and no longer on the Deck, with the same behaviour: a group per factory, an amount per destination, the loss warnings, Confirm.
4. [D12, D09] The "Done investing" button is on this screen. It stays hidden, with the line saying why, until the player has confirmed Collect (S03-5). After it is pressed the screen shows that the player is done and who the game still waits for (S01-3). The top bar keeps naming who the game waits for, and links to this screen while the player still has to collect.
5. [D12, R45, R46] After Collect, the screen holds the invest panels of E06-S04 and E06-S05 for the systems where the player has a ship. In this story they are not there yet, so the screen shows only what this player owns (6) until those stories land.
6. [D12, R50] The screen always shows the viewer's own warehouses (system, blocks, capacity, what each holds) and factories (system, good, size), in every phase. Outside Investment it shows no controls and says "Investment opens after Trading". What a warehouse holds is private to its owner (R29); everyone else sees it on System details as before (S02-2).
7. [D12] The Deck shows nothing of Investment: no Collect panel, no done button, and no line about waiting to collect beyond the top bar's.
8. [D10] The screen uses the design system and has no horizontal overflow at 390 px. Actions that aren't available now are hidden, not explained (D07).

## 📝 States checklist (Investment)

| State | Screen | Criterion |
|---|---|---|
| Before Investment ("starts after Trading") | Infrastructure (s10), read-only | S01-1, S08-6 |
| Collect, factories to place | Infrastructure (s10) | S03-1, S03-2, S08-3 |
| Collect with loss warning (overflow or nowhere to keep CHIPS/FOOD) | Infrastructure (s10) | S03-3, S03-4 |
| No factories, straight to invest | Infrastructure (s10) | S03-6 |
| Invest: ship at a producing system | Infrastructure (s10) | S04-1, S05-1 |
| Invest: no ship at a system | Infrastructure (s10) | S04-2 |
| Can't afford | Infrastructure (s10) | S04-2, S05-4 |
| Done investing, waiting for others | Infrastructure (s10) | S01-2, S01-3, S08-4 |
| Investment open, viewer on the Deck | Deck | S08-2, S08-7 |
| Warehouses and factories on System details | System details | S02-2 |
| Trading from a warehouse with no ship | Trading | S06-1 |
| Game ends after the last Investment | Game over | S01-2 |

## 📝 Decisions in play

R39 (money decides), R40 (start), R42 (cargo), R23, R10, R16, R12, R29 (money private), R37, D07, D09, D10, new R44–R50, new D11, new D12.

## ❓ Open questions

None blocking. These are small defaults, not decisions, and can change after the playtest:

- Production size has no cap (R46). If one player floods a good, the only brake is money. Revisit after the playtest.
- A player can only invest where they have a ship, which also decides where they can build.

## 🚫 Out of scope

- Selling or scrapping factories, warehouses or ships; ship purchase, fuel and hull damage (N07 stays active).
- Production as its own phase 6, and the board game's other phases.
- Production affecting prices or S/D: a factory only adds to its owner's stock.
- Loans and reputation (N05).
- Counting assets in the final score (R39).

## 📋 Phasing and dependencies

- Depends on E05-S07 (turn end) for S01 and on E05-S04 (ships choosing what to load) for S06. S08 depends on S03 (it moves S03's panel) and is the shell S04 and S05 build into.
- S02 adds state and the System details section only, so it doesn't need S01's phase. S06 needs a warehouse to exist in a player's state (S02), not the buying step (S04), so tests can set warehouses up directly.
- Waves, for parallel work:
  1. S01 (engine) ∥ S02 (state and System details)
  2. S03 (Collect; needs S01 and S02) ∥ S06 (Trading from a warehouse; needs S02)
  3. S08 (Infrastructure screen; moves Collect off the Deck; needs S03)
  4. S04 (warehouse blocks) ∥ S05 (factory units); both need S03 and S08
  5. S07 (Game log; needs S04 and S05)
- Merge conflicts: nearly every story touches the game's turn and player model, so merge the first one to finish at once and have the others rebase early. S01 ∥ S02 is the riskiest pair.
- S01 and S06 carry the most risk and should start first, in that order, to leave time before the playtest.
- Existing code: to be confirmed when S01 starts. It touches the game's phase order, a ship's cargo rules, and the trade flow's choice of which ships load, and the persistence mapping for the new state.
