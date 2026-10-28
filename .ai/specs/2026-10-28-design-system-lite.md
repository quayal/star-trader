# Design system (lite): tokens in code, one reference page, one agent guide

## 📝 TLDR

The visual decisions now live in two documents, `.ai/specs/reference.md` and `.ai/specs/tokens.md`, and nowhere in code. The two static pages still use their own ad-hoc hex values and the system font stack, which is the look the creator rejected in A01. This spec turns the approved tokens into one `tokens.css`, bundles the two approved fonts, adds one static reference page that renders the tokens and the few UI pieces the game actually has, and adds an agent-facing `docs/design-system.md` plus a CI check that rejects raw colors outside the token file.

It deliberately does **not** introduce Storybook, a JS build, a component framework, Tailwind or shadcn. See Alternatives.

## 📝 Problem Statement

- Tokens exist only as a Markdown table (`.ai/specs/tokens.md`). Nothing in the app consumes them. `[PRODUCT]` `src/main/resources/static/index.html` defines its own `--bg: #0b0e17`, `--accent: #5fb3ff` and so on, which don't match the approved values. `trade-offers.html` does the same, with 11 hex literals.
- Fonts: R01.2 approves Orbitron (display) and Rajdhani (body). The live pages use `-apple-system, Segoe UI, Roboto`, which is called out by name in A01's code signature. The mockups load the fonts from Google Fonts, and that load was already blocked once during the reference session.
- An agent building the next screen (the dashboard graph R03.3, the system screen R02.3) has no single file telling it which token to use, what each color means and what is forbidden. It would have to read and reconcile two long research documents.
- Nothing stops a new hard-coded hex value from landing.

## 📝 Proposed Solution

Four deliverables, all plain static files served by Spring Boot from `src/main/resources/static/`, matching how the frontend is already built (R05: static, lightweight page; N03: no heavy UI).

1. **`static/css/tokens.css`**: the only file allowed to contain color literals.
   - Layer 1 (raw): `--navy-950`, `--cyan-400` and the rest, copied exactly from tokens.md.
   - Layer 2 (semantic): `--surface-page`, `--text-muted`, `--accent-selected`, `--status-*`, `--trade-buy/sell`, `--good-*`, `--sd-*`, `--space-*`, `--text-*` sizes, `--radius-*`, `--glow-*`, `--duration-*`, `--font-display/body`. Each semantic token has a trailing comment citing its source (e.g. `/* R01.1 */`, `/* pick */`).
   - Layer 3 (component), only the three tokens.md defines: `--node-ship-marker-*`, `--edge-line`, `--badge-status-*`.
   - Dark only, on `:root` with `color-scheme: dark`. tokens.md says "Dark theme only", so there's no theme switch.
   - `@media (prefers-reduced-motion: reduce)` sets `--duration-*` to `0ms`.
2. **Bundled fonts**: `static/fonts/` holds Orbitron and Rajdhani as `woff2` files (both SIL OFL 1.1), with `OFL.txt`. `@font-face` rules go in `static/css/fonts.css`. Nothing loads from an external CDN.
3. **`static/design-system.html`**: one hand-written page, reachable at `/design-system.html` under `bootRun`, that renders the real CSS variables:
   - Color: swatches per semantic role, plus the contrast table from tokens.md re-measured against the saved values.
   - Type roles (display, title, value, body, caption) at the 12 px floor (R01.2).
   - The spacing ladder with its relationships (group → related → panel → between-groups → section → screen).
   - Radius and glow, with when to use each (glow marks meaning only: R01.1, A04).
   - Only the UI pieces that exist or are approved now, each in default, hover, focus-visible, selected and disabled states where applicable: primary button, control (input/select), panel, status badge, cargo meter (R01.3), own-offers table row with Buy/Sell and goods tints (R04.1), S/D value with stepped color plus visible number (R03.5).
   - Pieces are plain CSS classes in `static/css/components.css`, so the app pages use the same classes the reference page shows, with no copies (the intent of the Storybook prompt's rule 5 without the tooling).
4. **`docs/design-system.md`**, linked from `AGENTS.md`. This is what an agent reads before building a screen:
   - Character words and anti-character (from reference.md).
   - A semantic token table: token, purpose, where not to use.
   - A component/class table: class, when to use, when not to use.
   - Short "if… then use…" rules (e.g. selected state means `accent-selected` plus `glow-selected`, never decoration; red text means check placement: Sell column, danger or S/D bad).
   - What to avoid: A01–A04 and the generic-AI list from A02, each with how to recognize it in code.
   - Every rule cites an ID (R…, A…, brief R/N…) or is marked **proposal**.
   - Commands: how to view the page and run the token check.

Plus a **token check**: `scripts/check-tokens.sh` greps `src/main/resources/static/**/*.{html,css,js}`, excluding `css/tokens.css`, for `#hex`, `rgb(`, `rgba(`, `hsl(` and `oklch(` color literals and exits non-zero with `file:line` for each hit. It's wired into `./gradlew check` (an `Exec` task that `check` depends on), so the existing CI step (`./gradlew build`) enforces it with no workflow change.

### Migrating the two existing pages

`index.html` and `trade-offers.html` link `fonts.css`, `tokens.css` and `components.css`. Their inline `:root` palettes are deleted, and every color, font, spacing and radius is mapped to the nearest semantic token, so the check passes from day one.

This is a **token swap, not a redesign.** Layout and structure stay as they are, even though A01 rejects them. The redesign (graph dashboard, system screen) waits for the mockups that reference.md's Gap 5 says are still to come, and gets its own spec. One exception: A03 forbids showing other players' offers during Bidding. If `trade-offers.html` has an "All Offers" view, it's left as is and flagged in the PR, not silently changed. That's a product change, not a styling one.

## 📝 Alternatives considered

- **Full Storybook design system** (the `lesson_06/prompt-storybook.md` approach). Rejected for now. There's no JS toolchain, no component framework and no components; the frontend is two static HTML files. Storybook would require choosing React/Vue, Tailwind and shadcn first, which is an architecture decision the brief never made and which cuts against R05/N03. Revisit if the frontend moves to a component framework, which would need its own spec and brief decision.
- **Tokens only, no reference page.** Rejected. The creator decides several open items "by looking at mockups" (S/D stepped vs smooth, goods shades, lime vs green: Gaps 1 and 3). A page that renders the real tokens side by side is the cheapest place to look.
- **Reference page under `design/` instead of `static/`.** Rejected. Under `static/` it uses the exact same CSS files as the app with no copying, and it's viewable with `bootRun`. Serving it in production is harmless for a hobby project. Proposal: revisit if that changes.

## 📝 Architecture

No backend change. New and changed files:

```
src/main/resources/static/
  css/tokens.css          new: only home of color literals
  css/fonts.css           new: @font-face, local files
  css/components.css      new: shared classes (button, control, panel, badge, cargo-meter, offer-row, sd-value)
  fonts/*.woff2, OFL.txt  new
  design-system.html      new: reference page
  index.html              changed: inline palette removed, links the CSS above
  trade-offers.html       changed: same
docs/design-system.md     new: agent guide
scripts/check-tokens.sh   new
build.gradle.kts          changed: checkTokens Exec task, check dependsOn it
AGENTS.md                 changed: one line linking docs/design-system.md
```

## 📝 Edge Cases & Failure Scenarios

- **Contrast drift:** tokens.md's contrast table was measured on paper. During implementation, re-measure every text/background pair from the actual `tokens.css` values. If any text pair falls below 4.5:1 or a control border below 3:1, stop and ask; don't silently adjust a value.
- **border-subtle is 1.3–1.7:1**, so it's decorative only (tokens.md flag 1). The guide must say controls and focus rings never use it.
- **Color never carries meaning alone:** the S/D value always shows the number, Buy/Sell rows carry the text label, and status badges carry text (conflict 45, WCAG 1.4.1).
- **Reduced motion:** `prefers-reduced-motion` zeroes the hover-glow transition and the poll flash.
- **False positives in the check:** `#` in URLs or HTML entities (`&#8212;`) mustn't trip the grep. The pattern matches only `#` followed by 3, 4, 6 or 8 hex characters at a word boundary in a CSS-value context, and the script ships with a quick self-test on a fixture line.
- **Font license:** both faces are OFL 1.1. `OFL.txt` ships next to them.

## 📝 Risks & Impact Review

- **Visual change on live pages.** Both pages shift to the approved palette and fonts. That's intended, but it's a visible change, so the PR includes before/after screenshots.
- **No redesign yet.** The pages will still have A01's structure (plain sidebar, equal cards). Stated explicitly so a reviewer doesn't expect this PR to fix A01.
- **Pick values.** Most raw values are `[pick]` and expected to change once mockups exist (tokens.md). Because they all live in one file, a change is a one-line edit followed by re-running the contrast check.

## 📝 Decisions in play

- R05 / N03 (brief): static lightweight client, no animation budget. This drives "no Storybook, no JS build".
- R01.1, R01.2, R01.3, R03.5, R04.1 (reference.md): palette, type pairing, cargo meter, S/D color, offer-row tints.
- A01–A04 (reference.md): what to avoid.
- tokens.md: all values, "dark only", contrast flags 1–4.

## Open questions

1. Is a token swap on the two existing pages wanted now, or should they stay untouched until the redesign? **Default: swap**, so the token check can be on from day one.
2. Should the S/D scale on the reference page show stepped steps only (the tokens.md pick), or stepped and smooth side by side for the creator to choose (Gap 1)? **Default: both, labelled.**

**Resolved 2026-10-28 (creator):** 1 — swap both pages to the new tokens and fonts now. 2 — show both.

## Implementation notes (2026-10-28)

- Fonts are the Google Fonts latin subsets (Orbitron variable 400–900; Rajdhani 400/500/600/700), with each family's `OFL-*.txt` beside them instead of one shared `OFL.txt`, since the two have different copyright lines.
- Added `--space-hairline` (4 px, from the tokens.md scale but without a named role there), `--text-heading` (24 px, in the scale but unnamed), `--border-width`, `--focus-ring-width` and `--tint-buy/-sell`. All are marked pick/proposal in `tokens.css`.
- `trade-offers.html` really does list every player's offers (A03). Left as is and flagged, as planned.

## 📋 Phasing

One PR. Small enough that splitting adds overhead without reducing risk.

## 📋 Implementation Plan

1. Add `css/tokens.css` (three layers, source comments, reduced-motion override). Re-measure contrast from the saved values and stop on any regression.
2. Add the fonts (`woff2` and `OFL.txt`) and `css/fonts.css`.
3. Add `css/components.css` with the classes listed in Architecture, using only `var(--…)` values.
4. Add `design-system.html` rendering foundations and component states.
5. Migrate `index.html` and `trade-offers.html` to the shared CSS (token swap only).
6. Add `scripts/check-tokens.sh` and the `checkTokens` Gradle task. Confirm it fails on a planted `#fff` and passes on the clean tree.
7. Write `docs/design-system.md` and link it from `AGENTS.md`.
8. Gate: `./gradlew build`. Run `bootRun` and check `/`, `/trade-offers.html` and `/design-system.html` at 320, 768 and 1440 px and with keyboard focus. Attach screenshots to the PR.
