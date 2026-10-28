# Audit: `design/mockups/` (Star Trader: 5 pages + `shared.css` + `shared.js`)

Scope: `index.html`, `ships.html`, `warehouses.html`, `factories.html`, `trade-offers.html`, `shared.css`, `shared.js`. The recruitment page (`02-rekrutacja.html`) is not covered here. The mockups are suggestions, so many findings will not apply as-is.

Method: all five pages rendered in headless Chromium at 1440, 768 and 320 px. Computed styles, sizes and overflow read from the DOM. Screenshots of `index` and `trade-offers` at 1440 checked by eye.

What could not be verified:
- **Fonts:** Google Fonts were blocked (no network), so the page rendered in the `Segoe UI`/`sans-serif` fallback. Orbitron and Rajdhani are narrower, so widths and overflow could differ slightly.
- **Contrast:** calculated from the declared colors, with translucent colors composited over the panel color. No pixel sampling. The decorative space backdrop was not checked.
- **Interaction:** states were not clicked through. Tabs and the segmented control are static in the mockups.

## Step 1. Signs of a generic appearance

| Sign | Verdict | Where / what |
|---|---|---|
| Indigo/purple as action color | absent | Action color is cyan `#4fd8ff`. Purple is a category color (factories, "Wholesaler", CHIPS). |
| Gradient text in a heading | absent | |
| Gradient background or button | present | 28 gradients: `.btn-primary`, every panel and stat tile, the sidebar, progress fills, a 2 px accent line on each stat tile (`.stat-tile::before`). |
| Dark navy with a glow | present | The whole theme (`--void #05070c`, `--panel #0e1525`) with cyan glow shadows on the logo, active nav item, buttons, fills and star nodes. |
| Large radii and pills | partial | Radii mostly 5–12 px, chamfered buttons. Pills (`999px`) on chips, tracks and fills. |
| Card shadow as only separator | absent | Panels use 1 px borders. Shadows are only glows. |
| Card in a card | present | A ship card is a `.panel` inside the "Fleet Roster" `.panel`. The star chart is a bordered box inside a panel. The ISO→POLY recipe strip sits inside the factory card. |
| Translucent, blurred panels | present | `.topbar`: `rgba(10,14,24,.55)` + `backdrop-filter: blur(6px)`. |
| Pill above a heading | absent | The "Systems online: 6/6" chip is a status, not a heading decoration. |
| Highlighted word in heading | absent | |
| "Start / See demo" button pair | absent | |
| Row of huge numbers | present | `.stat-row` of four tiles on every page except trade-offers. |
| Three identical cards with icons | partial | Grids of identical cards (6 ships, 6 depots, 5 factories) holding different data. A legitimate pattern for items. |
| Cards floating over first screen | absent | |
| Logo as colored square with letter | present, nearly | "ST" monogram in a glowing circle (`.brand`). |
| Initial avatars | present | "AG" avatar; "CH/FD/IS/PL" letter tiles for goods. |
| Emoji instead of icons | present | `🚀` in `factories.html`; `⚠` in "Low fuel ⚠" and the shortage line; `▲▼` glyphs in deltas. |
| Sparkles as AI symbol | absent | |
| Icons on colored backgrounds | partial | Goods tiles (`.goods-icon`) are tinted letter tiles, but they carry information. |
| Empty promises | absent | Copy is functional. Exception: "Here's the state of your trading empire…". |
| Invented data | present, expected | Normal for a mockup. Several numbers contradict each other (finding 14). |

## Main findings

| # | Sign or problem | Where | What is visible | Why it's a problem | Replacement | Confidence |
|---|---|---|---|---|---|---|
| 1 | Mockups show features the brief excludes | `ships`, `factories`, `warehouses`; brief N07, R19 | Fuel, hull integrity, repair, ETA in cycles, "Commission new ship", "Rush repair/build", "Assign crew", "Upgrade" | Content the creator ruled out for v1. A mockup that implies it risks being built | Mark these pages "post-v1 sketch", or redraw them for v1 (ship in a system, cargo, jump) | verified |
| 2 | The v1 core loop is not mocked | all pages; brief R17–R21 | No phase indicator (Bidding / Ship-jumps / Trading), no D04 timer, no initiative bid, no "I'm done", no other players' status. Topbar shows only "Cycle 128". The offer form has Quantity, but R09 says quantity is not chosen by the player | The first screens a player needs are missing, and the form contradicts the rules | Topbar phase + countdown, initiative-bid control, form without quantity | verified (against brief) |
| 3 | Dashboard repeats the generic KPI-tile template | `.stat-row` on 4 pages | 4 tiles per page, colored top rule, up/down arrow delta, "AG" avatar | Generic appearance. Counts duplicate what the page below shows | One "what do I do now" panel; counts only where they drive an action | verified |
| 4 | Deltas use arrows and colors without meaning | stat tiles on 4 pages | "▲ 2 in transit" (green), "▼ Risk of overflow" (red), "▼ Hull at 42%", "▼ 2 sites idle‑rated". Nothing went up or down | Color is the only carrier of good/bad (WCAG 1.4.1); arrows are decoration | Plain text with a status word; arrows only for real change | verified |
| 5 | Low-contrast secondary text | `--text-faint #5b6888` | On `--panel` 3.28; on `--void` 3.63; on `--panel-alt` (table headers, chips) 3.08; muted badge 3.06; 8 px nav labels 3.37; sidebar footer 3.44. Need 4.5:1 | Accessibility. Colors stat labels, table headers, nav labels, breadcrumbs, timestamps | Raise `--text-faint` to about `#7d8bad` or stop using it for text | verified (calculated) |
| 6 | Text sizes too small and too many | CSS, 1440 px | Nav labels 8 px, goods tiles 9.6 px, timestamps 10.4 px, star-chart labels 10.4 px. 16 distinct sizes on `index`, 17 on `warehouses` | Accessibility; a type scale nobody chose | A 5-step scale with a 12 px floor | verified |
| 7 | Edges and fills nearly invisible | borders, `.track`, inputs | Panel border `#223253` on panel 1.43:1; input border 1.50; track border 1.50. Fills start from dark `*-dim` colors: cyan-dim 2.12, amber-dim 2.39, red-dim 1.59; the 0% bar uses `#35507f` at 2.37. Need 3:1 | Accessibility (WCAG 1.4.11) | Border and fill tokens at ≥ 3:1; solid fill instead of dim-to-bright gradient | verified (calculated) |
| 8 | Focus not designed; chamfer can clip it | `shared.css` buttons, inputs | No `:focus-visible` rule. Inputs/selects set `outline:none`, relying on a border change plus a 3 px halo at `rgba(79,216,255,.12)` (1.25:1). `.btn` has `clip-path: polygon(...)`, which cuts anything outside the box | Accessibility (WCAG 2.4.7 / 2.4.11) | Solid 2 px focus ring token; drop `clip-path` or use an inner ring | verified (CSS); inferred (clip effect, not rendered) |
| 9 | Labels not tied to controls | `trade-offers.html` form | All 5 `<label>`s lack `for`; selects and numbers lack `id`. Buy/Sell are plain `<button>`s without `aria-pressed`; tabs have no `role=tab`. Zero ARIA attributes in total | Accessibility: label click doesn't focus the field; screen reader hears nothing | `for`/`id` pairs; `aria-pressed`, `role=tab`/`aria-selected` | verified |
| 10 | Navigation vanishes below 860 px | last rule of `shared.css` | At 768 and 320 the sidebar is `display:none` with no replacement. Zero links to other pages visible (measured) | UX: you can't leave the current page | Bottom bar or menu button | verified |
| 11 | Horizontal scroll at 320 px; clipped tab bar at 768 | topbar; `trade-offers` | At 320 `scrollWidth` is 530–565 px on every page (topbar chips overflow). At 768 the trade-offers "Active Offers" panel reaches 1037 px and pushes the tabs out of view (`grid-template-columns: 360px 1fr`, no breakpoint) | UX (WCAG 1.4.10 reflow) | Wrap or collapse the chips; single column below about 900 px | verified |
| 12 | Small touch targets | buttons, tabs | `.btn-sm` 25 px tall, tabs 30 px. All meet the 24 px minimum (WCAG 2.5.8); none reaches 44 px | UX on touch | 40 px+ height for primary actions | verified |
| 13 | Link styled as button looks like a link | `index.html` "New trade offer" | `<a class="btn">` shows the default underline (screenshot). Other pages use `<button>` | Inconsistent | `text-decoration:none` on `.btn` | verified |
| 14 | Numbers contradict each other | all pages | **Fleet:** "7 vessels", "Docked 4", but 6 cards, 3 docked. **Factories:** "5 active" but 4 Active + 1 Idle, no card "under construction"; "Output 1,860 u" vs 960 u shown; "Avg efficiency 81%" vs 64.5% / 86%; "2 sites idle‑rated" vs 1 idle. **Warehouses:** Epsilon "71% full" vs 75% by stock, Sigma "63%" vs 65%; empire totals (POLY 11,400 / ISO 4,150 / FOOD 9,800) differ from depot sums (11,160 / 3,370 / 5,800); empire bars use an unlabeled scale (~16,000). **Market:** "Open offers 11" vs 4 in "My offers" | Trust problem for anyone using the mockups as a spec (brief Q03 asks for the real numbers) | Derive tiles from one data source | verified (arithmetic) |
| 15 | Inline styles replace the system | all pages | 293 inline `style=""` attributes, 30 `rgba()` and 8 hex colors outside `:root`. Ship/depot cards repeat the same inline flex pattern 6–11 times | Tokens exist but are bypassed, so the system is not enforceable | Components: `Meter`, `StatusRow`, `FactoryCard`; no inline styles | verified |

## Remaining findings

- **Fill colors carry mixed meanings.** Goods color (POLY amber, FOOD green, CHIPS purple on `warehouses.html`), fullness (red on Tau Ceti), and fuel level (red at 18%, amber at 64%, cyan at 88–100%). Cargo at 96% is green, so "full" reads as good, while a 94%-full depot is red.
- **Badges.** They carry a word plus a dot, so they pass 1.4.1. Their contrast is fine (5.5–9.1:1) except the muted badge (3.06).
- **Star chart.** Nodes are `div`s with only a `title`; keyboard and touch users can't read names beyond the label text. Ship route is not shown (just a white diamond). The Filter button does nothing.
- **Missing states.** The only one is the dashed "Build new factory" card. None for: no offers, no ships docked, rejected offer (price outside 1–20 CR), lost connection while polling, timer expired.
- **Form validation.** The price field says "1–20 CR" but has no error text or hint for what to do when it's wrong.
- **Copy.** "Welcome back, Captain Adrian" hard-codes the creator's name. "Systems online: 6/6" has no meaning in the brief. "Credits 48,210 CR" on every page is justified (money matters, R10/R12).
- **Fonts.** Orbitron and Rajdhani load from Google Fonts at runtime. A deliberate sci-fi choice, so counted as justified, but an external dependency.
- **Motion.** Not an issue: no running animations, only 0.15 s hover transitions (`transition: all` in 2 rules).
- **Passed.** Body text (15.5:1), dim text (7.0:1), accent colors on panels (6.2–11.1:1), badges, the primary button (8.2:1), the input focus border (11.5:1).

## Step 3. Counts

- **Text colors visible at 1440 px:** 8 on every page.
- **Font sizes visible at 1440 px:** 16 on `index`, `ships`, `factories`; 17 on `warehouses`; 13 on `trade-offers`. Smallest 8 px, largest 25.6 px.
- **Font sizes in code:** 25 declarations, 18 unique px values (8–25.6).
- **Border radii in code:** 9 declarations: 2, 5, 6, 7, 8, 12 px, `50%`, `999px`, `var(--radius)` (10 px, in `:root`).
- **Spacing values (padding, margin, gap):** 24 unique values (rem converted to px); **15 off the 4 px grid**: 2.4, 3.2, 4.8, 5.6, 6.4, 7.2, 8.8, 9.6, 11.2, 12.8, 14.4, 17.6, 19.2, 22.4, 25.6.
- **Hardcoded colors outside `:root`:** 8 unique hex (`#05070c`, `#0a0f1c`, `#0a0f1d`, `#0b0f1c`, `#0c1120`, `#103044`, `#1a3b57`, `#fff`) + 30 unique `rgba()` = about 38. Several are near-duplicates of existing tokens. `:root` holds 20 colors.
- **Inline `style=""` attributes:** 293.

## Five decisions

1. **Draw the v1 turn first.** Decide what the player sees in each phase (Bidding, Ship-jumps, Trading): phase and countdown in the topbar, who has acted, the initiative bid, the "done" action. *Principle*, then a "phase bar" *component*.
2. **Cut or label everything outside v1.** Fuel, hull, repair, commissioning, crew and upgrades are N07; "Quantity" contradicts R09. Remove them or mark the page a later-scope sketch. *Principle*.
3. **A contrast and scale floor.** Fix `--text-faint` and borders so text is ≥ 4.5:1 and meaningful edges ≥ 3:1; 12 px minimum and a 5-step type scale; spacing on the 4 px grid. *Token*.
4. **One meaning per color, status in words.** Reserve green/amber/red for status, use goods colors only on the goods tile, drop decorative ▲▼, make fills one solid color. *Principle*, backed by color *tokens*.
5. **Build the repeated pieces once.** `Meter`, `StatusBadge`, `GoodsTile`, `StatTile` and the panel header, with a visible focus ring, real labels and ARIA. This removes the 293 inline styles and gives mobile navigation (finding 10) one place to live. *Component*, with the focus ring as a *token*.
