# Star Trader UX conventions

Written by hand following the `om-ux-setup` fallback format on 2026-10-28, because the `npx uxproof` extractor wasn't run. Generated sections describe what the repo contains. The manual section holds the team's rules and wins on conflict.

## Stack

- Static HTML pages served by Spring Boot from `src/main/resources/static/`. There's no JS build and no component framework (brief R05: static lightweight client; N03: no heavy UI).
- Every page links `css/fonts.css`, `css/tokens.css` and `css/components.css`, in that order.
- Fonts are Orbitron (display) and Rajdhani (body), stored in `static/fonts/`. There are no CDN fonts.
- Dark theme only.

## Tokens

There are 69 tokens in `src/main/resources/static/css/tokens.css`: 18 colors (16 raw, 2 tints), 27 semantic aliases, 17 sizes (type, spacing, radius, widths), 3 glow shadows, 2 fonts and 2 durations. Components use **semantic** tokens only. Raw `--navy-*`, `--cyan-*` and similar tokens are referenced only inside `tokens.css`. Color literals anywhere else fail `./gradlew check` (`scripts/check-tokens.sh`). Purposes and where-not-to-use are listed in `docs/design-system.md` § Semantic tokens.

## Components

There are 16 CSS classes in `src/main/resources/static/css/components.css`: `.panel`, `.btn`, `.control`, `.field-label`, `.choice`, `.nav-item`, `.link`, `.badge`, `.message`, `.data-table`, `.num`, `.trade`, `.good`, `.sd-value`, `.cargo-meter`, `.flash`. Native elements map to classes: `button` → `.btn`, `input`/`select` → `.control`, `label` → `.field-label`, radio → `.choice`, `table` → `.data-table`, navigation links → `.nav-item`. Every class is shown in its states on `/design-system.html`.

## Screen shapes

| Shape | Example |
|---|---|
| Dashboard | `src/main/resources/static/index.html` (current layout is the rejected A01 look, pending redesign) |
| Form with list | `src/main/resources/static/trade-offers.html` |
| Design reference | `src/main/resources/static/design-system.html` |

Excluded: `design/mockups/`. These are early exploratory mockups that use raw hex and CDN fonts. They're superseded by `.ai/specs/reference.md` and `tokens.css` and kept only as history.

<!-- uxproof:manual-start -->
## Team rules (manual, authoritative)

The full rules, each with its source, are in `docs/design-system.md`. The ones reviewers check most often:

- Glow marks meaning only (selected, live, danger), never decoration. Selection is `--accent-selected` border plus `--glow-selected` (R01.1, A02).
- No looping or ambient animation. Allowed: a 150 ms hover transition and a single 600 ms flash when a poll changes a value. Both are 0 under reduced motion (A04).
- Color never carries meaning alone. S/D shows its number, badges carry a word (WCAG 1.4.1, conflict 45).
- S/D color is **stepped** by buy chance (easy, fair, hard, bad) and never a smooth blend or hard-coded indicator endpoints (R03.5, conflict 48, creator decision 2026-10-28).
- Goods colors only in the goods column and on goods icons. Buy/Sell tint only in the Buy/Sell column (R04.1).
- While Bidding is open, list only the signed-in player's own offers, never other players' (A03, brief R17).
- No UI for unimplemented features: fuel, hull, repair, ETA, upgrades (R01.4, R02.1).
- Text floor is 12 px (R01.2).
- A02 exceptions (default-looking elements kept on purpose): none yet. Add each one here with the date it was chosen.
- The dashboard and the map are **one screen**, with the six-system graph in the middle and overview information around it (creator decision 2026-10-28; R03.2, R03.3).
<!-- uxproof:manual-end -->
