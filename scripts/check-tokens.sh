#!/usr/bin/env bash
# Fails when a color literal appears in the static frontend outside css/tokens.css.
# Spec: .ai/specs/2026-10-28-design-system-lite.md. Guide: docs/design-system.md.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
static="$root/src/main/resources/static"

# Hex colors (#rgb, #rgba, #rrggbb, #rrggbbaa) not preceded by "&" (HTML entities like &#8212;),
# and color functions. color-mix() is allowed because its arguments must themselves be tokens.
pattern='(^|[^&0-9A-Za-z])#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})([^0-9A-Za-z_-]|$)|\b(rgba?|hsla?|oklch|oklab|lab|lch|hwb)\('

# Self-test so a broken pattern can't silently pass everything.
for bad in 'color: #fff;' 'background:#0b0e17' 'color: rgba(0, 0, 0, .5)' 'color: oklch(70% 0.1 200)'; do
	if ! grep -Eq "$pattern" <<<"$bad"; then echo "check-tokens self-test: missed '$bad'" >&2; exit 2; fi
done
for ok in '&#8212;' 'href="#top"' 'color: var(--text-muted)' 'color-mix(in oklch, var(--sd-easy) 50%, var(--sd-bad))' 'getElementById("message")'; do
	if grep -Eq "$pattern" <<<"$ok"; then echo "check-tokens self-test: false positive on '$ok'" >&2; exit 2; fi
done

hits="$(grep -rnE "$pattern" "$static" --include='*.html' --include='*.css' --include='*.js' \
	| grep -v "^$static/css/tokens.css:" || true)"

if [[ -n "$hits" ]]; then
	echo "Color literals outside css/tokens.css — use a semantic token (docs/design-system.md):" >&2
	echo "${hits//$root\//}" >&2
	exit 1
fi
echo "check-tokens: no color literals outside css/tokens.css"
