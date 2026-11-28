# Market screen: a grid that's quicker to read

**Brief:** R140 (the Market screen), R16, R23, D08. No rule changes: R140 still describes what the screen shows.
**Builds on:** `.ai/specs/implemented/2026-11-26-bidding-market-screen.md` (criteria 3–7, 11, 12 change here).
**Source:** the creator, 2026-11-28: "where I can be filter is pretty useless, ships can move everywhere. Also, offers
are shown quite clearly on the tiles, the filter doesn't really do much. Where I have goods is good though." A UX review
of the screen followed, and the creator asked for its items 1–6 on a branch to see before deciding.

## 📝 TLDR

The grid is easier to scan, and the screen points at what to do:

1. **One filter.** `Where I can be` and `My offers` go. `Where I have goods` stays, as a single toggle.
2. **Prices tinted by rank in their row.** Cheap is cool, dear is warm, in five steps, so the buy-low and sell-high
   ends of each good show without reading the numbers.
3. **The spread sits on its row.** A last `Spread` column (`7 → 14`, `spread 7`) replaces the list under the grid.
4. **Leaner cells.** Price and move, the lean bar, your stock and tier, your offer. The buy-chance percentage and the
   `cheapest`/`dearest` words leave the cell. The percentage stays in the inspector and in the bar's tooltip, and the
   extremes are named in the Spread column.
5. **Warnings where the offer is.** An offer the check warns about gets a `Check` badge on its chip, with the reason in
   the tooltip and in the inspector. The grid heading counts the warnings, and the count moves focus to the check panel.
6. **Holdings show where they sell.** Hovering or focusing a ship or warehouse chip outlines the cells where its goods
   could be sold: its own system, plus, for a ship that can still jump, every system it can reach. The best price for
   each good is labelled. A sentence under the strip says it in words. Clicking the chip pins it.

## 📋 Acceptance criteria

1. **Filter.** The filter group is one toggle button, `Where I have goods` (`aria-pressed`). Pressed, it dims cells
   where the viewer holds none of that good, as before. `Where I can be` and `My offers` are gone. The column headers
   still show presence and reach (old criterion 5).
2. **Price tint.** Each traded cell gets a step 0–4 from its price's place between the row's lowest and highest price
   (`round(4 × (price − low) / (high − low))`). 0–1 get a cool tint, 3–4 a warm tint, stronger at the ends, and 2 none.
   A row where every price is the same is not tinted. Cells narrow from 120 to 96 px so the grid and the Spread
   column fit beside the inspector at 1440 px. The tints are new semantic tokens in `tokens.css`
   (`--price-cheap`, `--price-cheap-strong`, `--price-dear`, `--price-dear-strong`), stepped and not blended (the
   design system's rule for scales). The colour is never the only signal: the prices are on the cells, and the legend names the scale.
3. **Spread column.** The grid gains a last column, `Spread`. Per good it shows `<low> → <high>` and `spread <high −
   low>`, or `—` when the good trades in fewer than two systems, or at one price everywhere. The cheapest and dearest
   systems are named in its tooltip and for screen readers. A legend under the grid shows the five tints from cheapest
   to dearest. Arrow keys skip it (it isn't a market). On a phone it is one more item in the good's card. The list
   under the grid is removed.
4. **Leaner cell.** A cell shows the price with its move, the lean bar, `you N` with the tier badge, and the offer chip
   (or the Dictator line). The `buyers N%` text is visually hidden: screen readers still hear it, and it's the bar's
   tooltip. The `cheapest`/`dearest` words and the price underline go.
5. **Warnings on cells.** Each per-offer warning from the offers check carries its offer's system and goods. A cell
   whose offer has warnings shows a `badge badge--warn` reading `Check`, after the offer chip. The badge's tooltip and a
   visually hidden text give the warnings. The inspector of that market lists them under `Check`. Next to the grid
   heading, a quiet button reads `1 warning` / `N warnings` while there are any; pressing it moves focus to the Offers
   check heading. The Offers check panel stays as the full list. Set-level warnings ("Offers in 4 systems…") count too
   but have no cell.
6. **Where a holding sells.** Each holdings chip has an id (`ship:<name>`, `warehouse:<system>`). For a chip, the
   *outlets* are every (system, good) where the chip holds that good and could be there in Trading: the chip's own
   system, and for a ship that can still jump, every system in its jump chances. Hovering or focusing a chip previews
   them, and clicking pins them, until clicked again (`aria-pressed`). Outlet cells get a dashed outline, and the
   highest price per good among them is labelled `best` at the foot of the cell, so the row's prices stay aligned. A line under the strip (a polite live region) says it in words,
   e.g. `Wayfarer: FOOD 6 sells best at Beta Hydri 14 (58%), then Mu Herculis 9 (92%), Tau Ceti 7 (here).` An empty chip
   says it holds nothing to sell. **This replaces old criterion 11's "clicking a chip filters the matrix to that place's
   column"**, which dimmed the cells that showed where to sell.
7. **Tests.** JS unit tests for the price step, the spread, the single filter, warnings keyed to a market, and the
   outlets and their sentence.

## Placement

Everything here is presentation of data the viewer already gets (`board`, `you`), so it stays in `js/market.js`
(standards §13 row 9: the JS formats). No server change, no new rule.

| Behaviour | Data it reads | §13 row | Where it goes |
|---|---|---|---|
| Price step and the row's spread | `board[].market[].price` | 9 | `marketMatrix` in `js/market.js` |
| Warnings keyed to a market | `you.offers`, ships, warehouses | 9 | `offersCheck` in `js/market.js` (each warning gains `system`, `goods`) |
| A holding's outlets and their sentence | `you.ships` (cargo, `jumpChances`), `you.warehouses`, `board` | 9 | `outlets`, `outletsText` in `js/market.js` |
| Highlighting cells on hover / pin | DOM only | — | `index.html` |

## Defaults I chose

- **Cool cheap, warm dear**, not green and red: green and red already mean buy/sell, rise/fall and the D08 levels on
  this very cell. Blue-ish and amber tints don't collide with them.
- **Stepped tint by rank in the row, not by absolute price.** The question a row answers is "where is this good cheap
  compared with elsewhere", and FOOD at 14 and ISO at 14 mean different things.
- **Outlets include the chip's own system** even for a ship that can jump: staying is a choice too.
- **Outlets outline rather than dim**, so the rest of the grid stays readable while a chip is pinned.
- **The `Check` badge is a word, not a ⚠ symbol**, as the design system avoids emoji icons and status by symbol alone.

## Out of scope

- A jump plan (giving each ship a tentative destination during Bidding). Raised in the same review; it is a product
  decision and gets its own spec if wanted.
- Placing an offer from the cell by keyboard.
- Buy suggestions for empty ships.
