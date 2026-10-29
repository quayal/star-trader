# Lessons learned

Running log of findings, gotchas and improvements discovered while working on this project — after fixing a bug, hitting a surprising root cause, or learning anything worth remembering across sessions.

**Before starting non-trivial work or debugging something that feels familiar, skim this file for relevant prior entries.**

How to add a lesson:

- New entries go at the **top** of the list below (newest first), so skimming recent lessons doesn't require reading the whole file.
- Default to a short entry here: date, one line summarizing the lesson, and just enough context (what happened, what fixed it, why it matters) to be useful to a future reader who has no memory of this session.
- If the lesson needs more than a few lines — a non-obvious root cause, a multi-step investigation, something future debugging should be able to retrace — write the full account in `.ai/lessons/YYYY-MM-DD-slug.md` and link it from the one-line entry here.
- Don't edit or delete past entries to "clean up" — if a lesson turns out to be wrong or superseded, add a new entry saying so and leave the old one for context.

## Entries

- 2026-10-29: **Clicking through a prototype finds the states you cause, not the ones that happen to you.** Writing E03–E05 specs from revision 013 with a per-screen states checklist (empty, loading, error, no access, conflict, timeout, someone else stalls) found gaps that 13 revisions of clicking hadn't: no rule for an absent player in ordered steps (pick, jump slot, Resolve next), a roll with no offers on the winning side, and no end of game. Run the checklist before turning a prototype into stories.

- 2026-10-28: **Ask for a sketch before building a spatial layout from words.** "2:2:2 rows" meant a flat-topped hexagon ring, like a benzene ring, but was first built as a rectangular grid with bent edges, which cost a wasted prototype revision. When a layout is described in words (rows, ratios, "like X"), confirm the shape first with a 3-line ASCII sketch.

- 2026-10-28: **`./gradlew bootRun` serves static files from `build/resources/main`, not `src/`.** Edits to `src/main/resources/static/` don't show up on refresh until `processResources` runs. Fixed in `build.gradle.kts` with `bootRun { sourceResources(sourceSets.main.get()) }`. Note that an already-running server on port 3000, perhaps started by IntelliJ, may serve a different copy.

- 2026-10-28: **In this sandbox, auto mode blocks running remote packages via `npx`** (for example `npx uxproof@0.3.1`, which `om-ux-setup` uses). Use the skill's documented manual fallback (here `references/contract-format.md`) instead of retrying, and say in the output that the result was produced by hand.

- 2026-10-28: **The default shell is zsh, which doesn't word-split unquoted variables.** `for spec in "a 1 2"; do set -- $spec; ...` leaves `$1` holding the whole string. That produced wrong URLs and file names for headless-Chrome screenshots. Wrap such loops in `bash -c '...'`.

- 2026-10-18: Never `pkill -f` by the app's main class name (`StarTraderApplicationKt`) to stop a local `bootRun` — this repo is worked on from several worktrees at once, and the main class name is identical across all of them, so the pattern matches and kills *any* running instance, not just the one you started. Killed another worktree's dev server on port 3000 this way while manually verifying a UI change. Always capture and kill by the exact PID of the process you launched (`$!` / a PID file), never a name/pattern match, in this sandbox.

- **2026-10-15 — `git push`/`pull` over HTTPS fails in this sandbox until `gh auth setup-git` is run.** `gh` is pre-authenticated (browser/passkey login), but plain `git` doesn't use that login on its own — it tries legacy username/password auth, which GitHub no longer accepts, so pushes fail with errors like "could not read Username" even though `gh auth status` shows logged in. Fix: run `gh auth setup-git` once per sandbox. It writes **per-host** credential config (`credential."https://github.com".helper`, `credential."https://gist.github.com".helper`), not the generic `credential.helper` key — so `git config --get credential.helper` will look empty even after it's correctly set; check with `git config --get-all 'credential.https://github.com.helper'` or just test with `git fetch` instead.

<!-- Add new entries above this line, newest first. -->
