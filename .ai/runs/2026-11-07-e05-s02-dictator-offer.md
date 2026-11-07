# Execution plan: E05-S02 — Offers are disclosed and the Dictator may act

Source doc: .ai/specs/2026-10-29-e05-trading-phase.md
Issue: #51

## Goal

When a good starts resolving, every player sees every offer for it: corporation, Buy/Sell, price (S02-1). If a player
is Dictator of that good at that system, the game asks them before the roll whether to place one offer or pass (R38,
S02-2). Their offer is shown like the others (S02-3). With no Dictator the resolution goes straight on (S02-4).

## What already works (checked before planning)

- E05-S01 (#46, PR #88) queues every good, and the first player in initiative orders the queue and presses **Resolve next**.
  Today a resolution has no steps: `Turn.resolveNext` moves the good from `tradingQueue` to `resolvedGoods` right away.
- Market positions exist on `Player.marketPositions` (R28), are public in the game shape, and `Player.isDictatorOf` already
  keeps a Dictator from placing that offer in Bidding (E03-S03-10, R38). Nobody gains a position until S06, so in real
  play there is no Dictator yet. The browser walk seeds one in DynamoDB Local.
- The s03 status line, the decision alert (S01-9) and the top bar are already drawn from pure helpers in `js/trading.js`.

## Scope

- **Domain** (`game`): a `Resolution` of the good being resolved: the disclosed offers (copied from the turn's offers for
  that good, in placement order), the Dictator's email if there is one, and whether they've decided. `Turn.resolution`
  holds the current or last resolution. A good counts as resolved (`resolvedGoods`) once its resolution finishes.
  **Resolve next** is refused while a resolution is unfinished (S01-4 becomes a real check), and reordering still works.
  `Game.placeDictatorOffer` and `Game.passAsDictator`: only the Dictator, only while they're asked. The offer is checked
  like any other (R03). Disclosure and the Dictator's decision are logged.
- **Adapters**: `ResolutionItem` in `TurnItem`. An older item has no resolution, which reads as null and is correct,
  because every S01 resolution finished at once. The game shape gains `turn.resolution`. New endpoints:
  `POST /api/games/{id}/trading/dictator-offer` and `POST /api/games/{id}/trading/dictator-pass`.
- **Frontend**: s03's main column shows the resolution: "Now resolving: Mu Herculis · ISO" (or "Last resolved: …" once
  finished), the offers table (Player, Offer, with "(Dictator)" marked) or "No offers for this good.", and the Dictator
  line ("deciding before the roll" / "offered Sell at 10 HT" / "passed"). The Dictator gets the form: Buy/Sell, price,
  **Place offer** and **Pass**, with an inline error. Everyone else reads "Waiting for {corp} (Dictator) to decide." (S01-8).
  Off s03, the decision alert names the Dictator decision (S01-9).

## Non-goals

- The counter, trades, amounts, price change, Wholesalers and positions (S03–S06). After disclosure and the Dictator's
  decision the resolution finishes, so the good counts as resolved. S03 puts the roll where the resolution now finishes.
- Gaining a Dictator position (S06). Nothing in the game creates one yet.
- The old `trading` package (`BestOfferCalculator`, `TradeDesk`) stays untouched until S03.

## Decisions (autonomous, reversible)

- **Disclosure and the Dictator's decision are logged.** The spec's TLDR says "Everything is public and logged", and
  prototype revision 015 logs both. Log lines: "Offers for Mu Herculis ISO: Vega Lines buys at 11 HT, Orion Freight
  sells at 10 HT" (or "No offers for Mu Herculis ISO"), then "Altair Trade Co. (Dictator) offers to sell at 10 HT" or
  "Altair Trade Co. (Dictator) passed".
- **The resolution stays on the turn after it finishes.** s03 keeps showing the last good's offers until the next
  **Resolve next**. S03–S05 add their results to the same object.
- **The Dictator is found when the good starts resolving** and kept on the resolution. Positions don't change during
  a turn (S06-5).
- **Two endpoints, offer and pass**, not one with a "pass" flag. Each status code then means one thing. Out of turn is
  `409` "Waiting for {corp} (Dictator) to decide.", and nothing to decide is `409` "No Dictator decision is pending.".
  An invalid type or price is `400` with the offer form's messages.
- **Waiting for the Dictator blocks Resolve next** with `409` "Waiting for {corp} (Dictator) to decide.", the same text
  the screen shows.

## Implementation Plan

### Phase 1: Domain

1. `Resolution` and `Turn.resolution`: `resolveNext(dictator)` discloses the good's offers and asks the Dictator, or
   finishes at once. `placeDictatorOffer` and `dictatorPassed` finish it, and Resolve next is refused while it's open.
   Tests first (`ResolutionTest`, `TurnTest`).
2. `Game.resolveNextGood` finds the Dictator and logs the disclosure. `Game.placeDictatorOffer` and
   `Game.passAsDictator` check membership, that the caller is the one asked, and the offer, then log the decision.
   `TradingService` runs both inside `GameStore.update`. Tests (`GameTradingTest`, `TradingServiceTest`).

### Phase 2: Adapters

1. Persistence: `ResolutionItem`, `TurnItem` round trip, and an older item reads with no resolution.
   `BACKWARD_COMPATIBILITY.md` §7.
2. Web: `turn.resolution` in the game shape, the two endpoints and their status codes (`GameControllerTest`).
   `BACKWARD_COMPATIBILITY.md` §6.

### Phase 3: Frontend

1. `js/trading.js`: resolution heading, offer rows, Dictator line, waiting text and decision text for the Dictator step,
   and Resolve next waits for the resolution to finish. Tests in `src/test/js/trading.test.mjs`.
2. s03: the resolution panel, the Dictator form (Buy/Sell, price, Place offer, Pass, inline error, focus), and the
   status and alert for the Dictator.

### Phase 4: Verify

1. Full gate (`./gradlew build`), then a browser walk with two players into Trading and one seeded Dictator position.
   Check disclosure, the Dictator form (error, offer, pass), the waiting text and Resolve next blocked for the other
   player, the alert off s03, the log, and phone width.

## Risks

- `index.html` is one page for every game, so the Dictator form's typed price and its message are keyed by game, turn
  and good (lessons 2026-11-04).
- A good resolving with an open Dictator step spans polls. The form must not be redrawn under the Dictator's hands
  (keep it unless the resolution changes).
- `hidden` vs `display` on the new panel and form (lessons 2026-10-30, 2026-11-04).

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [ ] 1.1 Resolution and Turn.resolution
- [ ] 1.2 Game and TradingService: disclosure and the Dictator's decision

### Phase 2: Adapters

- [ ] 2.1 Persistence of the resolution
- [ ] 2.2 Web: game shape and Dictator endpoints

### Phase 3: Frontend

- [ ] 3.1 trading.js helpers for the resolution
- [ ] 3.2 s03 resolution panel and Dictator form

### Phase 4: Verify

- [ ] 4.1 Full gate and browser walk
