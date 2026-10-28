# Design tokens: Star Trader (playtest MVP)

Date: 2026-10-27. Derived from `reference.md`. Approved by the project creator for the playtest MVP; values need not be exact.
**[ref]** = given by a reference. **[pick]** = best judgement, expected to change once mockups exist. Dark theme only (no light theme).

## Tokens

### Layer 1 – raw (used only in this file)

| Token | Value | Provenance |
|---|---|---|
| navy-950 | `#05070c` | [ref] R01.1 |
| navy-900 | `#0e1525` | [ref] R01.1 |
| navy-800 | `#16203a` | [pick] raised / hover surface |
| slate-500 | `#5f6f90` | [pick] control border |
| slate-700 | `#2a3550` | [pick] hairline (R01.1: 1 px blue-gray) |
| ink-100 | `#e6edf7` | [pick] |
| ink-400 | `#8fa0bd` | [pick] |
| cyan-400 | `#22d3ee` | [pick] R01.1 cyan accent |
| green-400 | `#34d399` | [pick] R01.1 green accent |
| amber-400 | `#fbbf24` | [pick] R01.1 amber accent |
| orange-400 | `#fb923c` | [pick] |
| red-400 | `#f87171` | [pick] R01.1 red accent |
| blue-400 | `#4d8dff` | [pick] lighter than "dark blue" for contrast |
| pink-400 | `#f472b6` | [pick] |
| lime-400 | `#a3e635` | [pick] |
| violet-400 | `#a78bfa` | [pick] |

### Layer 2 – semantic

| Token | Layer | Value | Provenance | Where to use | Where not to use |
|---|---|---|---|---|---|
| surface-page | semantic | navy-950 | R01.1 | App background | Cards |
| surface-panel | semantic | navy-900 | R01.1 | Panels, cards, tables | Page background |
| surface-raised | semantic | navy-800 | pick | Hover, selected row | Default state |
| border-subtle | semantic | slate-700 | R01.1 | Panel edges, row dividers | Inputs, buttons, focus (below 3:1) |
| border-control | semantic | slate-500 | pick (contrast fix) | Inputs, buttons, unselected nodes | Decoration |
| text-primary | semantic | ink-100 | pick | Values, headings | — |
| text-muted | semantic | ink-400 | pick | Labels, timestamps | Values that matter |
| text-on-accent | semantic | navy-950 | pick | Text on a cyan button | — |
| accent-selected | semantic | cyan-400 | R01.1 | Selected nav item, primary button, selected node/row | Decoration (A02, R01.1) |
| status-live | semantic | green-400 | R01.1 | Live / active / your turn | S/D scale |
| status-warn | semantic | amber-400 | R01.1 | Waiting, pending | — |
| status-danger | semantic | red-400 | R01.1 | Outbid, error | — |
| trade-buy | semantic | green-400 | R04.1 | Buy column tint only | Elsewhere |
| trade-sell | semantic | red-400 | R04.1 | Sell column tint only | Elsewhere |
| good-poly | semantic | blue-400 | R04.1 | Goods column and goods icons only | Anywhere else (conflict 49) |
| good-chips | semantic | pink-400 | R04.1 | Same | Same |
| good-food | semantic | lime-400 | R04.1 | Same | Same |
| good-iso | semantic | violet-400 | R04.1 | Same | Same |
| sd-easy | semantic | green-400 | R03.5 | S/D value, buy chance ≥97% | Buy/Sell column |
| sd-fair | semantic | amber-400 | R03.5 | S/D value, buy chance 72–92% | Buy/Sell column |
| sd-hard | semantic | orange-400 | R03.5 | S/D value, buy chance 42–58% | Buy/Sell column |
| sd-bad | semantic | red-400 | R03.5 | S/D value, buy chance ≤28% | Buy/Sell column |
| font-display | semantic | Orbitron | R01.2 | Titles, badges, numerals | Body text |
| font-body | semantic | Rajdhani | R01.2 | Body and labels | — |

S/D scale is stepped (pick; stepped vs smooth is Gap 1). Steps come from buy chance, never hard-coded endpoints (conflict 48). The number always stays visible beside the color. Seller meaning is still open.

### Spacing (scale 4, 8, 12, 16, 24, 32, 48)

| Token | Value | Relationship |
|---|---|---|
| space-group | 8 | Within a group (label and value) |
| space-related | 12 | Rows in a list |
| space-panel | 16 | Inside a panel |
| space-between-groups | 24 | Between groups |
| space-section | 32 | Between sections |
| space-screen | 48 | Screen margins |

### Type (floor 12 px, R01.2)

text-caption 12, text-body 14, text-value 16, text-title 20, text-display 32. (Scale 12, 14, 16, 20, 24, 32.)

### Radius (pick, no reference)

radius-control 4, radius-panel 8.

### Glow (R01.1, A04; static, meaning only)

- glow-selected: `0 0 8px` cyan-400 at 50%
- glow-live: `0 0 8px` green-400 at 50%
- glow-danger: `0 0 8px` red-400 at 50%

### Motion (A04, Gap 1)

- duration-hover: 150 ms (hover glow transition)
- duration-flash: 600 ms (one flash when a poll changes a value; plays once, never loops)

### Component tokens

- node-ship-marker: ship on a graph node; accent-selected for own ships, text-muted for other players' ships
- edge-line: 1 px glowing graph line; border-control now, ease-of-jump scale later (Gap 4)
- badge-status: status-* colors, font-display, 12 px

## Color pairs and contrast (WCAG)

| Pair | on page | on panel | on raised | Result |
|---|---|---|---|---|
| text-primary | 17.1 | 15.5 | 13.7 | pass |
| text-muted | 7.6 | 6.9 | 6.1 | pass |
| cyan / green / amber / orange / red as text | 7.3–12.1 | 6.6–10.9 | 5.8–9.7 | pass |
| good-poly | 6.3 | 5.7 | 5.1 | pass |
| good-chips | 7.6 | 6.9 | 6.1 | pass |
| good-food | 13.4 | 12.1 | 10.7 | pass |
| good-iso | 7.4 | 6.7 | 5.9 | pass |
| text-on-accent on cyan | 11.2 | – | – | pass |
| border-control (needs ≥3:1) | 4.0 | 3.6 | 3.2 | pass |
| border-subtle | 1.7 | 1.5 | 1.3 | fails 3:1, decorative only |

Flags:
1. border-subtle is decorative only; controls and focus use border-control or accent-selected.
2. "Dark blue" POLY (e.g. `#1e40af`) is 2.1:1 on a panel and fails; blue-400 used instead. Revisit on mockups.
3. Lime FOOD vs green are only 1.27:1 apart; separated by placement (goods column vs Buy/Sell tint), not color alone.
4. Red serves Sell, danger and S/D bad; separated by placement. Judge visually.

## Decisions that are not tokens

### Principles
- Glow marks meaning only: selected, live, active, your turn (R01.1).
- No ambient or looping animation (A04).
- Text floor is 12 px (R01.2).
- Color on data, never a plain table of numbers (conflict 45); the number stays visible beside the S/D color.
- Color in the offers table sits only in the Buy/Sell and goods columns (R04.1).
- Default-looking elements allowed only if the creator picks them on purpose in mockups (A02).
- Other players' offers are never shown during Bidding (A03).
- No fuel, hull, repair or ETA UI until implemented (R01.4, R02.1).

### Component traits
- Cargo meter: `used / capacity u` plus a thin bar (R01.3).
- A ship shows name, cargo and location as its top three facts (R01.4).
- Left rail is the shortcut to detail screens (R03.1).
- Dashboard is a graph of 6 nodes and 15 edges, with ships (including other players') on each node; click opens the system screen (R03.3).
- Warehouses and factories share one "Industry" screen, later (R02.2).
- System screen lists the ships present (R02.3).
- Selection is a highlight or glow transition; a polled change flashes once (Gap 1).
