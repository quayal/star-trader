# Reputation

**Brief:** `.ai/specs/product-brief.md` — N05 (reputation and loans out of scope; superseded here for reputation only), D11, R39 (money alone wins), R43 (market positions), R70–R80 (interception and battle).
**Source:** the creator's vault note `10-projects/Trader/Reputation.md` and their answers of 2026-11-18.
**Depends on:** `2026-11-18-connections.md` (connection rises move reputation). **Shown on:** `implemented/2026-11-18-corporations-tab.md`.

## 📝 TLDR

Proposed: every player has a **reputation** from 0 to 40, starting at 20 and visible to everyone. Today it doesn't exist; only an unused `trading.Player.reputation` field does. Rising in the market and building political or business connections raise it. Criminal connections, falling in the market and hostile acts in space lower it: every interception try, and firing at a ship that didn't try to intercept you. A low reputation recovers by 3 each turn, though a player at 0 has to buy their way back, and a player can buy +1d6 for 20 HT once per Investment. A reputation of 25 or more pays a money bonus at the end of each turn. Reputation itself doesn't count toward winning (R39); its other effects come with later features.

## 📝 Problem Statement

The original game uses reputation to make hostile play cost something. Battle (R69–R80) now exists, so attacking another ship should leave a public mark. Loans, police control and smuggling also move reputation in the creator's rules, but none of them exist yet, so this spec builds the changes that touch existing features and lists the rest.

## 📋 Acceptance criteria

### The value
1. A whole number from 0 to 40, 20 for every player at game start. Every change is clamped to that range. It's public: `reputation` in `players[]`, shown first on each Corporations card.
2. **Every change is a public log line** giving the reason, any roll and the result. For example: `Vega Lines's reputation: −7 for intercepting (rolled 2d6: 7) → 13`. A change clamped to nothing (already at 0 or 40) is still logged, so the reason is visible.

### What changes it (implemented now)
| # | Event | Change | Where it happens |
|---|---|---|---|
| 3 | End of turn, reputation 1–17 | +3 | `endTurn`, before the next Bidding; not after the last turn |
| 4 | Market position rises (R43, including none → Retailer) | +2 | position roll |
| 5 | Market position falls (a Dictator demoted to Wholesaler) | −2 | position roll |
| 6 | Business connections rise, except a rise caused by a market position rise | +2 | Investment raise |
| 7 | Political connections rise | +1 | Investment raise |
| 8 | Criminal connections rise | −1 | Investment raise |
| 9 | Buys reputation in Investment: 20 HT, once per Investment | +1d6 | Infrastructure |
| 10 | Each interception try, successful or not, unless the trying ship has a working Battle comm module | −2d6 | when the tries are revealed (R70) |
| 11 | Fires at a ship that didn't try to intercept any of the firer's ships | −2d6, once per battle per player | first such order resolved |

- **3:** a player at 0 doesn't recover: they have to buy an increase (row 9) to get back to 1 or more (creator, 2026-11-18).
- **9:** refused at 40 (`Your reputation is already at 40.`), when they can't pay (`Buying reputation costs 20 HT; you have 15 HT.`), or if they've already bought this Investment (`You've already bought reputation this Investment.`). The cost isn't logged (R29).
- **10:** the Battle comm module prevents the interception penalty but not the firing penalty (row 11). Battle comm is a military module, and nothing gives one yet (R56), so today every try costs reputation.
- **11:** "once per battle per player" follows the note's own arithmetic: an attacker who intercepts and fires loses 2d6 + 2d6, and the ship they attacked loses nothing. In a battle with no interceptions (R80), the first player to fire loses 2d6. Returning fire at a ship that tried to intercept you costs nothing.

### Money bonus
12. **At the end of each turn**, after the recovery (row 3), every player with reputation 25 or more is paid a bonus from the creator's table:

    | Reputation | 25 | 26 | 27 | 28 | 29 | 30 | 31 | 32 | 33 | 34 | 35 | 36 | 37 | 38 | 39 | 40 |
    |---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
    | Bonus (HT) | 5 | 5 | 10 | 10 | 15 | 20 | 20 | 20 | 20 | 25 | 25 | 30 | 30 | 30 | 35 | 40 |

    It's paid on the last turn too, before the winner is decided, because the note says "each turn". So reputation earns money that counts toward winning, although reputation itself doesn't.
13. **The log.** `Reputation bonus: Vega Lines, Kessler Cargo`, one line per turn and only when someone is paid, without amounts (money is private, R29). The paid player sees the amount privately, as part of their money.

### Deferred (listed so their reputation rule isn't lost)
- Doesn't pay interest on a loan: −4 (loans are out, N05/D11).
- Has to leave Space during police control: −2d6.
- Has to jettison cargo while smuggling: −2d6.
- Ship confiscated while smuggling: −3d6.

## 📝 Proposed Solution

A `Reputation` value class owns the range, the clamping and the end-of-turn recovery. `Player` exposes intent-named methods (`gainReputation(reason, amount)`, `loseReputation(...)`), and the existing events (position roll, connection raise, interception, salvo) call them through `Game`. Each event writes its own log line.

**Alternative considered:** computing reputation from the log (rejected: the log is text, and a stored value is simpler to read and test).

## 📝 Architecture and data model

- `game/Reputation.kt` (new): value 0..40, `START = 20`, `plus(n)`, `minus(n)`, `recovered()` (+3 if 1–17), `isMax()`, `bonus()` (the table, 0 below 25).
- `Player.reputation: Reputation`. The legacy `trading/Player.kt` (used only by the old `TradeDesk`) is left alone. Its unused `reputation` field isn't this value.
- `Turn.reputationBought: List<String>`, emptied with Investment.
- Battle: `Battle` records, per player, whether they've already paid the firing penalty this battle, so `Battle` owns that rule.
- Web: `POST /api/games/{id}/reputation/buy`. `PlayerResponseDto.reputation`.
- DynamoDB: a new attribute, no migration (the game isn't published).
- Frontend: buy action on Infrastructure (beside the connection raise), a value line on Corporations.

## 📝 Edge Cases

- A rise and a fall in the same position roll (one player promoted to Dictator, another demoted): each gets their own line.
- Reputation can't go below 0: a −2d6 roll at 3 logs `→ 0`.
- An interception try by a player whose ship has a damaged Battle comm module still costs reputation, because a damaged module doesn't count (R76).

## Defaults I chose

- Recovery isn't applied after the final turn, since the game is over. The money bonus is (criterion 12).
- The bonus is logged without amounts.
- The firing penalty is once per battle per player, not per order or per round.
- Changes clamped to nothing are still logged.
- Buying reputation is refused at 40 rather than taking the money for nothing.

## Brief changes

- N05 is superseded for reputation (loans stay out). New decision D21 records that.
- New rules: R85 (reputation 0–40, starts 20, public, no effect on winning), R86 (the change table above), R87 (buy +1d6 for 20 HT once per Investment), R88 (money bonus at 25+, paid at the end of each turn, the last included).

## 📋 Phasing

1. **The value:** start value, recovery and money bonus at end of turn, log, shown on Corporations.
2. **Market and connections:** rows 4–8.
3. **Buying:** row 9, on Infrastructure.
4. **Hostile acts:** rows 10–11 in the battle flow.

Phases 2–4 are independent once phase 1 lands. Row 6–8 needs the connections spec's raise.

## 📋 Implementation Plan

1. `Reputation` with unit tests (range, clamping, recovery boundaries 0, 1, 17, 18).
2. `Player.reputation`, start at 20, persistence round-trip. Recovery in `endTurn` with the log line.
2a. Money bonus in `endTurn` (every turn, the last included, before the winner), its log line. Tests at 24, 25, 33 and 40.
3. DTO field and the Corporations section. JS test.
4. Position roll: +2 and −2 with log lines.
5. Connection raise hooks: +2, +1, −1. No change for a rise caused by a market position.
6. Buy endpoint, refusals, `Turn.reputationBought`, Infrastructure action.
7. Interception tries: −2d6 unless a working Battle comm module.
8. Firing penalty, once per battle per player, owned by `Battle`.
9. Record D21 and R85–R88 in the brief. Check it in the browser through a turn with an interception.
