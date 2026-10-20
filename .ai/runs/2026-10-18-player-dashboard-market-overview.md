# Execution plan: player dashboard — live market overview

Source doc: .ai/specs/2026-10-18-player-dashboard-market-overview.md

## Goal

Give a player a dashboard page showing live prices and S/D modifiers for all
six star systems, built from `design/mockups/index.html`'s visual shell, and
make it the app's root page (`static/index.html`) without losing the existing
trade-offer form.

## Scope

- `trading/adapter/web` (`TradeOfferDtos.kt`, `TradeOfferController.kt`,
  `TradeOfferControllerTest.kt`): extend `StarSystemOption` with per-good
  price + S/D modifier.
- `src/main/resources/static`: move today's trade-offer form to
  `trade-offers.html`; add a new `index.html` dashboard.

## Non-goals

Fleet/warehouse/factory stat tiles, the orbit-ring star chart, "Fleet Alerts",
login/sessions, multi-game/room selection, any confidential per-player state,
and any new endpoint (reuses `/api/options`).

## Implementation Plan

### Phase 1: Market data over HTTP

1. Add `GoodsMarket(goods, price, indicator)` and a `market: List<GoodsMarket>`
   field on `StarSystemOption`; populate it in `TradeOfferController.options()`
   from `StarSystem.getPrice`/`getIndicator`. Extend
   `TradeOfferControllerTest.shouldExposeOptionsForBuildingTheForm` with
   `EPSILON_ERIDANI` market assertions.

### Phase 2: Dashboard as the root page

2. Rename `static/index.html` to `static/trade-offers.html` (content
   unchanged except title + back-link). Add a new `static/index.html`
   dashboard: sidebar (Deck/Market nav only) + topbar (title only) + a
   "Market Overview" panel rendering all 6 systems' goods/price/indicator from
   `/api/options`.

## Risks

- `GET /` now serves different content than before — intentional, confirmed by
  the issue author; no real users exist yet (pre-launch project).
- Response payload growth on `/api/options` is negligible (≤4 entries × 6
  systems).

## Progress

PR: #5

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Market data over HTTP

- [x] 1.1 Extend `StarSystemOption` with per-good price/indicator + test — 5446e8d

### Phase 2: Dashboard as the root page

- [x] 2.1 Move trade-offer form to `trade-offers.html`; add dashboard `index.html` — f52bf44
