# Star Trader design system — read this before building a screen

Plain static HTML + CSS, no build step (brief R05, N03). Three shared stylesheets in `src/main/resources/static/css/`:

| File | What it is |
|---|---|
| `tokens.css` | Every value. The **only** file allowed to contain color literals. |
| `fonts.css` | Orbitron + Rajdhani, bundled in `static/fonts/` (OFL 1.1). Never load fonts from a CDN. |
| `components.css` | Shared classes. Use these instead of re-styling elements per page. |

Every page links all three, in that order. Shared page scripts are ES modules in `static/js/` (no build step); their pure logic is unit-tested in `src/test/js/` with Node's built-in runner (`./gradlew jsTest`, part of `check`).

**Signed in.** Every game screen calls `requireSession()` from `js/session.js` before loading anything: a signed-out player goes to the sign-in screen (`/sign-in.html?next=…`, s10) and comes back. A new game screen must do the same.

The live reference is `/design-system.html` (`./gradlew bootRun`, then open `http://localhost:3000/design-system.html`).

Sources cited below: `.ai/specs/reference.md` (R…, A…), `.ai/specs/tokens.md`, `.ai/specs/product-brief.md` (brief R…/N…). **proposal** = not yet confirmed by the creator.

## Character

**Is:** control-deck, instrumented, luminous, legible, spatial. **Is not:** generic, dry, off-game, ornamental, spreadsheet-like. (reference.md)

Dark navy surfaces, 1 px borders, luminous accents that mark meaning (R01.1). Data carries color and visual form, never a plain table of numbers (A01, conflict 45).

## Semantic tokens

Use these, never the raw `--navy-*`, `--cyan-*` etc.

| Token | Use for | Never for |
|---|---|---|
| `--surface-page` | App background; inputs; a card nested inside a panel | — |
| `--surface-panel` | Panels, tables | Page background |
| `--surface-raised` | Hover, selected row, empty track of a meter | Default state |
| `--surface-backdrop` | Behind a modal `<dialog>` (`::backdrop`), e.g. the landing dialog s05 | Panels, page background |
| `--border-subtle` | Panel edges, row dividers (decorative: 1.3–1.7:1) | Inputs, buttons, focus |
| `--border-control` | Inputs, buttons, unselected graph nodes (≥ 3:1) | Decoration |
| `--text-primary` | Values, headings | — |
| `--text-muted` | Labels, timestamps, secondary lines | Values that matter |
| `--text-on-accent` | Text on a cyan button | — |
| `--accent-selected` | Selected nav item, primary button, selected node/row, focus ring | Decoration (A02, R01.1) |
| `--status-live / -warn / -danger` | Live/your turn · waiting/pending · outbid/error | The S/D scale |
| `--trade-buy / -sell` (+ `--tint-*`) | The Buy/Sell column only (R04.1) | Anywhere else |
| `--good-poly / -chips / -food / -iso` | The goods column and goods icons only (R04.1) | Anywhere else |
| `--sd-easy / -fair / -hard / -bad` | The S/D value only (R03.5) | Buy/Sell column |
| `--glow-selected / -live / -danger` | Selected, live, danger (R01.1) | Logos, every border, decoration |
| `--font-display` | Titles, badges, numerals (R01.2) | Body text |
| `--font-body` | Body and labels (R01.2) | — |
| `--text-caption…--text-display` | 12 · 14 · 16 · 20 · 24 · 32 px. 12 is the floor (R01.2) | Anything below 12 px |
| `--space-group` 8 | Within a group: label ↔ value | — |
| `--space-related` 12 | Rows in a list | — |
| `--space-panel` 16 | Inside a panel | — |
| `--space-between-groups` 24 | Between groups | — |
| `--space-section` 32 | Between sections | — |
| `--space-screen` 48 | Screen margins | — |
| `--space-hairline` 4 | Icon ↔ text, badge padding (**proposal**) | Layout |
| `--radius-control` 4 / `--radius-panel` 8 | Controls / panels (pick) | Pills (`999px`) |
| `--duration-hover` 150 ms / `--duration-flash` 600 ms | Hover glow / one flash on a polled change. Both 0 under reduced motion | Loops |

## Components (`components.css`)

| Class | Use when | Don't use when |
|---|---|---|
| `.panel` | Grouping a block of content on the page | Inside another `.panel` (card-in-card, A02) |
| `.btn` | The one primary action of a panel | Navigation (use a link or `.nav-item`) |
| `.control` | `<input>` and `<select>` | — |
| `.field-label` | The always-visible label of a control (`<label for>`) | As a placeholder substitute — placeholders are examples, not names |
| `.choice` | Radio/checkbox as a tile (e.g. BUY / SELL) | More than ~4 options (use a `<select>`) |
| `.nav-item` + `aria-current="page"` | Rail/sidebar destinations (R03.1) | In-page actions |
| `.link` | Inline navigation in text | Actions that change state (use a button) |
| `.badge .badge--live/--warn/--danger` | A status with a word: "Your turn", "Waiting", "Outbid" | Decoration, counts |
| `.message .message--error/--success/--warn` + `role="status"` | Feedback under a form; `--warn` for a consequence to check before confirming ("the rest will be lost") | Errors the player must act on elsewhere |
| `.data-table` (+ `.num` on numeric cells) | Tabular data with aligned numbers | One-dimensional lists of 1–3 facts |
| `.trade .trade--buy/--sell` | The Buy/Sell cell of an offer row (R04.1) | Any other column |
| `.good .good--poly/…` | The goods name in a goods column or on the map (R04.1); `color` in HTML, `fill` in SVG | Elsewhere |
| `.sd-value[data-step]` | The S/D indicator. Number always shown (R03.5) | Without the number |
| `.cargo-meter` | Used / capacity of a ship or depot (R01.3) | Percentages without the absolute numbers |
| `.market-level[data-level]` | A market level (D08): its triangle and label ("▲ high demand"; just "in balance"), or, on the map, a price with its triangle after it. Works in HTML (`color`) and SVG (`fill`). ▲/▼ sit in a `.market-level__triangle`, small and centred on the text (in SVG a `dy` lift, as SVG text ignores `vertical-align`); the map's size is the reference. `data-level` is the level key from `js/board.js` (`high-demand` … `high-supply`); colours are the `--market-*` tokens | Without the triangle or the word next to it |
| `.flash` | Add once when a poll changes a value (Gap 1) | Anything that repeats |
| `.visually-hidden` | Text only a screen reader needs, e.g. the polite live region announcing other players' progress (E04-S04) | Hiding anything a sighted player needs too |

A heading that takes focus when a screen opens (`tabindex="-1"`) shows no focus ring; it isn't a control. Hover and focus states can be frozen for documentation with `data-preview-state="hover|focus"` (used on `/design-system.html` only).

## If… then use…

- **Something is selected / current** → `--accent-selected` border + `--glow-selected`. Not a fill color, not a different hue.
- **A value changed after a poll** → add `.flash` once. Never a ticker, pulse or loop (A04).
- **A color means something** → put the word or number next to it (WCAG 1.4.1). S/D shows `S/D −7`, a badge says "Outbid".
- **Red text** → check which red: Sell column, danger status or S/D bad. They share a hue and are told apart by placement (tokens.md flag 4).
- **S/D color** → compute buy chance = P(2d6 + indicator ≥ 0) and step it: ≥ 95% easy, ≥ 70% fair, ≥ 40% hard, else bad (boundaries sit between the discrete 2d6 outcomes). Never hard-code indicator endpoints (conflict 48). Always stepped, never a smooth blend (creator decision 2026-10-28, Gap 1).
- **Market level** (D08: five levels; ▲/▼ for direction, none in balance; green up, red down, bright/muted for how likely, plus the word) → `marketLevel(indicator)` in `js/board.js` gives the label, symbol, hint and the `data-level` key for `.market-level`; the moderate/high demand split is `MODERATE_DEMAND_MIN_CHANCE` there (20%). Don't recompute the bands on a page.
- **A button is disabled** → say why next to it.
- **A page toggles `hidden` on something whose class sets `display`** (`.btn`, a `form`, a flex/grid container) → add `[hidden] { display: none; }` for it, or the element stays visible (lessons 2026-10-30, 2026-11-04).
- **Listing offers** → only the signed-in player's own offers while Bidding is open (A03, brief R17). They come from `you.offers`; the offer form's helpers are in `js/offers.js`. Buy/Sell in an offer row uses `.trade`, the goods `.good`.
- **A feature isn't implemented** (fuel, hull, repair, ETA, upgrades) → show nothing for it (R01.4, R02.1).
- **Need a value that has no token** → add a semantic token to `tokens.css` with a source comment, don't inline it.

## What to avoid

| Avoid | Recognize it in code | Instead |
|---|---|---|
| A01: dry info on navy | System font stack; one text color for every value; rows of `NAME ... 17 CR` with no color or form | Display/body fonts, data color (S/D, goods), spatial layout |
| A02: generic AI look | `linear-gradient` backgrounds or text, `backdrop-filter`, sparkle icons, emoji as icons, rows of identical KPI tiles with ▲▼, pills (`border-radius: 999px`) everywhere, card inside card | Flat navy surfaces, 1 px borders, glow only for meaning |
| A03: others' offers during Bidding | A table/tab of all players' offers | Own offers only |
| A04: real-time / animation-heavy | `animation: … infinite`, pulsing glow, `requestAnimationFrame` loops, live counters | Static glow, a 150 ms hover transition, one 600 ms flash |
| Raw values | `#hex`, `rgb(`, `hsl(` outside `tokens.css`; ad-hoc `px` for spacing/color | `var(--semantic-token)` |
| Placeholder as label | `<input placeholder="Price">` with no `<label>` | `.field-label` + placeholder as an example |
| Status by color alone | A colored dot with no text | `.badge` with a word |
| `div` as a button | `<div onclick>` | `<button>` / `<a>` |

## Commands

- View: `./gradlew bootRun` → `http://localhost:3000/design-system.html`
- Token check: `scripts/check-tokens.sh` (also runs in `./gradlew check`, so CI enforces it)
- After changing a token: reload `/design-system.html` and read the contrast table — text needs 4.5:1, control borders 3:1.

## Known gaps

- The home "Market Overview" still has A01's structure (`trade-offers.html` was removed in E03-S03). The game screens (s01, s02) follow the accepted prototype; below 700 px the rail stacks above the screen instead of overflowing (E03-S02).
- ~~`trade-offers.html` lists every player's offers~~ — removed in E03-S03 with the unscoped `/api/trade-offers`; offers are placed on s01 and s02 and only the player's own are listed (A03).
- No icons yet; no icon set chosen.
- The prototype accepted 2026-10-28 (`.ai/prototypes/discovery/dashboard-map-system/revision-013/`) needs these design-system changes before the redesign:
  - ~~Five market colours~~ — done in E03-S02: `--market-demand-high/-moderate`, `--market-balance`, `--market-supply-moderate/-high` (green ▲, red ▼, bright at the extremes, muted in between, grey in balance).
  - ~~A price-direction mark on every price~~ — done in E03-S02 as ▲ / ▼, nothing in balance (creator, 2026-11-02; the earlier arrows ↑ ↗ → ↘ ↓ and the — are gone).
  - ~~A map node holds its goods and prices inside a larger circle, as a flat-topped hexagon ring~~ — done in E03-S02 (the Deck, `index.html`).
- **The Deck's map on a phone**: below 700 px the rail stacks above the screen and the map takes the full width, but its text still scales to about 8 px at 390 px, under the 12 px floor. Everything on it is also on System details and in Your fleet; a readable mobile map needs a design decision.
  - **The money unit is HT.**
