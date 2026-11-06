# Game delegates to its parts

## 📝 TLDR

`Game` stays the aggregate root: the one entry point for every player action and the unit `GameStore.update` saves
atomically (engineering standards §8, §9). But the rules themselves move to the class that owns the data they check or
change. `Game` coordinates its parts and doesn't reach inside them. This is a refactor with no behaviour change: the
same messages, log lines and exceptions.

## Why

The creator, 2026-11-06: "all of this should belong to individual classes, and Game calls their methods… Game should
know not what sits inside ship". `Game.kt` had grown to 336 lines. It held ship movement (`it.copy(system = …,
location = SPACE)`), the player's ship-replacing `copy` (copy-pasted twice), offer price and goods checks, bid limits,
turn-order and jump-slot bookkeeping, and the initiative roll. The Trading phase (E05) would have added more.

## Decisions

- **Each rule goes to the type that owns its data:**
  - `Ship` owns jumping (`jumpTo`: not to the system it's already at; arrives in Space) and landing (`landAt`), plus
    the text of its own log lines.
  - `ShipLocation` owns the text for "lands at the port", "lands on the planet" and "stays in space".
  - `Player` owns its ships (`ship(name)`, `jumpShip`, `landShip`, `startWith`), its bid limit (`bid(amount)`) and its
    market positions (`isDictatorOf`).
  - `Offer` owns its own validity: the good is traded at the system and the price is 1–20 (`Offer.place`).
  - `Turn` owns its phase checks, done-bidding signals, bids and offers (one per good per system), the initiative
    roll and closing Bidding, turn-order picks including the auto-filled last place, and the jump-slot bookkeeping
    (jumped ships, the ship waiting to land, ended slots, moving to Trading).
- **`Game` keeps what spans its parts:** membership, game status, which player acts, the log, and the messages that
  name another player's corporation (e.g. "It's X's turn to jump"). Only `Game` knows every player.
- **The same in `login/`:** `CodeExchangeService` checked a `StoredCode`'s expiry, attempts and match itself; these
  become `StoredCode.canBeTried(now)`, `attemptsUsedUp()`, `withAttempt()` and `matches(guess)`.
- **Keeping it that way** (the creator, 2026-11-06: "add whatever you need to keep the code clean and organised"):
  - engineering standards §8 states the rule, and AGENTS.md points to it;
  - a new ktlint rule, `trader:copy-only-in-own-class`, reports `x.copy(…)` with any receiver but `this` in production
    code. That's how `Game` reached inside `Ship` and `Player`, and it's the one form a syntactic check can see. Test
    sources are skipped by path (an `.editorconfig` section for `src/test/**` broke ktlint's other settings for those
    files, see `.ai/lessons.md`);
  - `CODE_REVIEW.md` gets a check for what ktlint can't see (a decision made by reading another object's fields, a
    private helper over one of our types, a class growing story by story), rated Major.
- **Not in scope:** the older `trading/` prototype (`TradeDesk`, `TradeOfferBook`, `trading.Player`,
  `trading.MarketPosition`) runs alongside `Game` with its own in-memory offers and repeats the offer checks. Whether
  E05 folds it into `Game`'s parts or retires it is a separate decision, made with E05.

## After the DDD review (2026-11-06)

An architect review agreed with one `Game` aggregate, the parts owning their rules, and thin application services. Fixed
on this branch, as the creator chose:

- **`Turn` no longer depends on `Player`.** The first version had `Turn.placeOffer(player, …)` call
  `player.isDictatorOf`, which is a rule spanning two parts and so the root's. Now `Player.offer(…)` makes the offer
  (with the Dictator check) and `Player.bid(…)` the bid; `Turn.placeOffer(offer)` / `placeBid(bid)` only record them.
  `Game` checks the phase first (`Turn.requireBidding`, so it stays public), so a late bid or offer is still refused
  as "Bidding has closed" whatever its amount (A04). Dictator and duplicate checks swapped order; both can't hold at
  once, since market positions change in Trading and each turn starts with no offers.
- **The 1–50 turns rule** moved from `GameCreationService` into `Game.lobby`.
- **One read per action.** `GameStore.update` throws `GameNotFoundException` itself, so services no longer `find` first.
- **The ktlint rule** covers domain code only (not `/adapter/` or tests), and also reports a bare `copy` inside
  `x.run { }`, `x.apply { }` or `with(x) { }`. Building an object with its constructor from another's fields is the
  bypass it can't see; review checks that.

Deferred: `PlayerId`/`ShipName` value classes and typed log entries returned from part methods (next PR, before E05);
the `trading/` decision and splitting `Turn` into `Bidding`, `TurnOrder` and `ShipJumps` (start of E05); nullable
request fields reaching the domain (changes which error a player sees first, so a product call).

## Done when

- `Game` no longer copies or reads the inside of a `Ship`, and its methods read as coordination.
- The existing `Game*Test` suites pass unchanged. The new methods on `Ship`, `Player`, `Offer` and `Turn` get their own
  unit tests.
- `./gradlew build` passes.
