# Playtest touches: done investing on the Deck, red Pass, short jump card, Dictator price, goods colours

Date: 2026-11-20 · Source: creator request, 2026-11-20 · Status: in progress (browser-walked 2026-11-20) · Ships with
`2026-11-19-invest-on-tables.md` on the same branch.

## Ask (creator)

> I'm done investing should also be visible on the deck.
> Passing on wholesale should be bigger button (red)
> Notification to jump ship is enough to say it's your time to jump ships
> If asked for dictator's decision, show price of goods
> Every time goods are mentioned, also on deck or any other view, they should be colour coded

## Decisions

- **Done investing in the top bar** of the Deck and System details, as "I'm done bidding" is in Bidding; once done the
  bar says who the game still waits for. Infrastructure keeps its own button. No server change.
- **Wholesale Pass** is a full `.btn` in a new `.btn--danger` variant (red, `--status-danger`), beside Trade.
- **The jump card and alert** say only "It's your turn to jump ships." (the "or to finish jumping" is on the panel).
- **The Dictator sees the market price** of the good resolving, from the board, wherever they're asked: the card, the
  decision alert, the Trading status line and the form question ("… ISO here (market price 12 HT)").
- **Goods colours everywhere** (widens R04.1, which kept them to goods columns): one module, `js/goods-colour.js`,
  wraps every POLY/CHIPS/FOOD/ISO word in page text in `.good--<name>` as renders happen (a MutationObserver), rather
  than every render doing it. Not inside filled buttons (the colour would vanish on cyan), form controls (they can't hold
  a span) or the map's SVG (it colours its own). The text is unchanged, so screen readers and `textContent` checks are too.

## Placement

UI-only. `trading.js` `resolvingPrice` / `priceNote`; `notices.js` and `ship-jumps.js` texts; `goods-colour.js`
`goodsParts` (pure, tested) and `colourGoods` / `keepGoodsColoured` (DOM).
