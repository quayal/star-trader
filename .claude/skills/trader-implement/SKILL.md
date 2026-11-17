---
name: trader-implement
description: Use before writing or changing any Kotlin or JavaScript in Star Trader — a feature, a fix, a refactor, a spec being implemented. Puts each rule in the class that owns its data, builds test-first, and runs a self-review of the diff against the engineering standards before anything is committed. Also applied by om-auto-create-pr, om-auto-continue-pr and om-fix through their .ai/skills overrides.
---

# Implementing in Star Trader

The standards (`.ai/engineering-standards.md`) and the review rules (`CODE_REVIEW.md`) are not background reading.
Past sessions read them and still put battle rules in `Game`, decisions in DTOs and field-by-field lookups in
`Player`, because the slip happens while writing, not while reading. This skill moves the check to three points:
before code (placement), while coding (test first), and after (a diff audit you must actually run).

## 1. Before code: read, then place

1. Read `.ai/engineering-standards.md` and the "Repo-specific checks" in `CODE_REVIEW.md` in this session, even if
   you think you know them. Skim `.ai/lessons.md` for the area you're touching.
2. Write a placement table, in the spec or your plan, one row per new rule or behaviour:

   | Rule | Whose data does it read or change? | Where it goes |
   |---|---|---|
   | e.g. "a rocket roll ≤ hits destroys the target" | the battle round's salvos | `Battle.fired` |

   - The answer is the class that owns that data: `Ship`, `Player`, `Battle`, `Turn`, `Offer`, `Factory`.
     `Game` (or a service) gets a row only when the rule spans several parts: membership, whose turn it is, the log,
     looking parts up and passing values between them.
   - A part's method takes values, not another part (`battle.fired(salvos, hitsSurvivable)`, not
     `battle.fired(ships)`): the root looks the other part up and passes what's needed.
   - A DTO or item class constructor maps fields; any `if`, `&&`, `filter` or `firstOrNull` over domain fields in it is a
     rule, and gets a method on the domain class instead.
   - A new concept with several classes (like `battle`) gets a subpackage of `game` (§1), not more files in the flat
     package.
3. Rule numbers (Rxx) and stories (Sxx/Exx) go in the KDoc, as the existing code does.

## 2. While coding: test first

- A game rule starts from a failing domain test on the class that owns it (`BattleTest`, `ShipBattleTest`), plain JUnit
  and fakes (`FakeDice`), no Spring (§6, §7). A `GameXxxTest` proves the coordination, not every branch of the rule.
- New data in `Game` or anything inside it extends its item class and round-trip test in the same change (§12).
- Kotlin style ktlint enforces: no trailing commas, no extensions on our types, no `object` namespaces,
  `copy` only on `this`. Don't fight ktlint with `@Suppress`.

## 3. After coding: audit the diff (required before commit)

Run these against your diff and fix every hit, or write down in the PR why it's fine (§0 allows that; silence doesn't):

```bash
BASE=$(git merge-base HEAD origin/main)
ADDED() { git diff "$BASE" -U0 -- 'src/main/**/*.kt' | grep '^+' | grep -v '^+++'; }
# A decision made from another object's fields (CODE_REVIEW §8 bullets 1 and 4):
ADDED | grep -nE 'it\.[a-z]\w* == [^&]+&& *it\.[a-z]\w* ==|\b[a-z]\w*\.(stage|done|status|tier|location|size) (==|!=|in)'
# Logic in an adapter (DTO / item constructors should only map):
git diff "$BASE" -U0 -- 'src/main/**/adapter/**/*.kt' | grep '^+' | grep -nE '\.(filter|firstOrNull|any|none)\s*\{|takeIf|\bif \('
# A part's method taking another part (Ship/Player/Turn/Battle as a parameter of another part's method):
ADDED | grep -nE 'fun \w+\([^)]*: (Ship|Player|Turn|Battle|Offer|Factory)\b'
# Growth of the aggregate root: if Game.kt grew, name the part each new line's rule belongs to.
git diff "$BASE" --stat -- src/main/kotlin/dev/adriangrzebyk/trader/game/Game.kt
```

The greps are a net, not a judge: a hit can be legitimate (the root coordinating, a mapping that only renames), but
look at each one. Then read your own diff once as the reviewer in `CODE_REVIEW.md` would, top to bottom.

## 4. Verify and record

- `env -u AWS_REGION ./gradlew check` (ktlint, Kotlin tests, JS tests; Docker for the DynamoDB tests). For a UI change,
  exercise it in the browser too (AGENTS.md: "should work" isn't verification).
- A rule move with no behaviour change is proven by the existing coordination tests passing with only import changes.
- Lessons, specs and run logs as AGENTS.md says. When a reviewer or the creator catches a standards slip this skill
  should have caught, add the pattern to step 3, not just a lesson.
