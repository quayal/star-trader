# Execution plan: E03-S03 — A player places secret trade offers

Source doc: .ai/specs/2026-10-29-e03-bidding-phase.md
Issue: #38

## Goal

During Bidding a player places secret trade offers from the Deck (s01) and from System details (s02): a
system, a good, a whole price 1–20 and Buy/Sell, at most one per good per system per turn, never for a good
they're Dictator of there. Only that player ever sees their offers; the old unscoped `/api/trade-offers`
listing is gone.

## Scope

- `game` domain: `Offer` (player, system, goods, Buy/Sell, price); `Turn.offers`, kept from Bidding through
  Trading and empty at the next turn's Bidding; `Game.placeOffer(...)` with the S03 rules (R03 price, R33 one
  per good per system, R38 Dictator, R17/A04 only while Bidding, goods traded at that system, members only).
- `BiddingService.placeOffer` runs it inside `GameStore.update` (atomic, like "done", A04).
- DynamoDB: `TurnItem` stores `offers` via a new `OfferItem`; older items read as no offers.
- Web: `POST /api/games/{id}/offers` → `201` + the game shape; `you.offers` carries the caller's own offers of
  this turn. `turn` and `players[]` never carry offers (R17, A05).
- Legacy `/api/trade-offers` (list, submit, resolve) and `trade-offers.html` removed (S03-8); `/api/options`
  stays (home Market Overview, design-system page).
- `static/js/offers.js` (new, pure, unit-tested): price validation, which goods can still be offered and why
  not, the no-ship warning, offer lines, the "Bidding has closed" wording.
- `static/index.html`: s01 right column with "Place an offer" and "Your offers this turn"; s02 "Place an
  offer" for that system.
- `BACKWARD_COMPATIBILITY.md` §2, §6, §7; `docs/design-system.md` known gaps.

## Non-goals

- "I'm done bidding" button and waiting list (S04), initiative bid (S05), Game log screen (S06).
- Editing or withdrawing an offer (S03-9, OQ-3: no in v1).
- Resolving offers in Trading (E05). `TradeDesk`/`BestOfferCalculator` stay as they are for E05 to reuse.

## Decisions

- Base branch `main` (config `auto`).
- **A new `game.Offer`, not `trading.TradeOffer`.** The spec's architecture note says "reuse" but is marked
  "to refine": `trading.TradeOffer` has nullable fields with defaults and an initiative counter from the
  global book, which §12 (no defaults, every field named) and §11 (no global state) rule out for stored game
  state. E05 can map `Offer` into trading's calculators.
- **Offers live on `Turn`**, since they're per turn: the next turn's `Turn.bidding(n)` starts empty, and the
  Bidding → Ship-jumps step now keeps them (it rebuilt the turn before).
- **Offers are private**: only in `you.offers`; `TurnResponseDto` stays without them. Denied-path controller
  test: another player's poll and their own offer response never contain the first player's offer.
- **Price comes in as a number that may be fractional** (`BigDecimal`, as `turns` does), so 2.5 is refused by
  the domain's message rather than rounded by Jackson. Unknown system/goods names → `400`.
- **Messages**: "Price must be a whole number from 1 to 20." · "You already have an offer on {goods} at
  {system} this turn." · "You're Dictator of {goods} here — you place that offer when it resolves in
  Trading." · "{goods} isn't traded at {system}." · "Bidding has closed" (`409`, the existing A04 text); the
  screen words it "Bidding has closed — this offer wasn't placed." (S03-7).
- **Legacy API removed rather than scoped**: it has no game id and no session (§11, A05); the game-scoped
  endpoint replaces it, as `BACKWARD_COMPATIBILITY.md` §2 anticipated. The `InMemoryTradeOfferBook` stops
  being a Spring bean (it stays as the `TradeDesk` test fake).
- **Forms are static markup, not rebuilt on poll**, so typed values and focus survive the 2 s poll. After a
  `409` close the form stays on screen, disabled, with the message and the typed values until the next turn.

## Risks

- `risk-high`: confidential read (A05). Covered by the denied-path controller test and the DTO test.
- A new stored attribute: older turns must read as no offers (round-trip + missing-attribute tests).

## Implementation Plan

### Phase 1: Domain

- 1.1 `Offer`, `Turn.offers`, `Game.placeOffer` with the S03 rules, TDD.
- 1.2 `BiddingService.placeOffer` inside `GameStore.update`.

### Phase 2: Persistence and API

- 2.1 `OfferItem` and `TurnItem.offers` with older-item default and round-trip tests.
- 2.2 `POST /api/games/{id}/offers`, `you.offers`; denied-path tests.
- 2.3 Remove `/api/trade-offers` and `trade-offers.html`; `BACKWARD_COMPATIBILITY.md` §2/§6/§7.

### Phase 3: Client logic

- 3.1 `offers.js`: price validation, offerable goods (R33, R38), no-ship check, offer lines, closed wording, with tests.

### Phase 4: Screens

- 4.1 Deck (s01): right column with "Place an offer" and "Your offers this turn".
- 4.2 System details (s02): "Place an offer" for that system with the no-ship warning.
- 4.3 `docs/design-system.md` known gaps.

### Phase 5: Verification

- 5.1 Full gate (`./gradlew build`) and a browser walk with screenshots.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Domain

- [ ] 1.1 `Offer`, `Turn.offers`, `Game.placeOffer` with the S03 rules, TDD
- [ ] 1.2 `BiddingService.placeOffer` inside `GameStore.update`

### Phase 2: Persistence and API

- [ ] 2.1 `OfferItem` and `TurnItem.offers` with older-item default
- [ ] 2.2 `POST /api/games/{id}/offers`, `you.offers`; denied-path tests
- [ ] 2.3 Remove `/api/trade-offers` and `trade-offers.html`; compatibility doc

### Phase 3: Client logic

- [ ] 3.1 `offers.js` with tests

### Phase 4: Screens

- [ ] 4.1 Deck (s01) offer form and Your offers
- [ ] 4.2 System details (s02) offer form
- [ ] 4.3 `docs/design-system.md` known gaps

### Phase 5: Verification

- [ ] 5.1 Full gate and browser walk with screenshots
