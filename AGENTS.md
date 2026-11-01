# AGENTS.md

Instructions for any AI agent (autonomous or interactive) working in this repository.

## Project

Star Trader — an online, browser-based adaptation of a classic 1980s trading/space board game, for 2–6 players, with many independent games able to run on the same deployment at once. Spring Boot + Kotlin backend, static frontend.

- Build: `./gradlew build`
- Run locally: `./gradlew bootRun` (serves on port 3000, see `src/main/resources/application.properties`)
- Test: `./gradlew test` (Kotlin); `./gradlew jsTest` runs the frontend's JavaScript unit tests and needs Node 22+

Product direction, confirmed decisions, business rules and open risks live in `.ai/specs/product-brief.md` — read it before making product or architecture decisions, not just code.

Engineering standards and architecture conventions (package structure, hexagonal/DDD boundaries, testing rules) live in `.ai/engineering-standards.md` — read it before writing or restructuring any code. Two Kotlin style rules there (§5) are enforced by ktlint, so `./gradlew check` fails on them: no trailing commas, and a function that works on one of our types is a method of that type's class, with domain↔adapter mapping as constructors or methods of the adapter-side class (`GameResponseDto(game, viewerEmail)`). No extensions on our own types, and no Kotlin `object` used as a namespace for functions; a function no class owns may be top-level. ktlint catches most of this and review catches the rest. The project's own ktlint rules live in `ktlint-rules/`.

The frontend design system (tokens, shared CSS classes, what to avoid) is summarized in `docs/design-system.md` — read it before building or restyling any screen. Color literals belong only in `static/css/tokens.css`; `./gradlew check` fails otherwise.

## Working practices

- **Plan before implementing.** For non-trivial work that means the spec described below; even inside a spec, think the approach through before touching code. If an implementation goes sideways, stop and re-plan rather than patching your way through a plan that's already proven wrong.
- **Verify before declaring done.** Run `./gradlew build` / `./gradlew test` and actually exercise the change — "should work" is not verification.
- **Never merge into `main` locally.** All changes land on `main` via a pull request on GitHub and must pass the required CI checks there first — don't `git merge`/`git rebase` onto a local `main` and push the result, even as a solo-dev convenience. The point of CI is that "it passes" is a fact GitHub checked, not a claim an agent made locally.
- **Confirm which checkout you're in.** This project is worked on from several git worktrees at once (one per dispatched task) plus the main checkout. Before writing a spec, committing, or editing a shared file like this one, check `git status`/`pwd` — never assume another worktree's or the main checkout's state matches yours.

## The `.ai/` directory

This repository uses `.ai/` as structured, long-term memory for both specs and AI run history. Keep this structure up to date; don't put spec or run material anywhere else.

```
.ai/
├── specs/
│   ├── product-brief.md       Living product brief — updated in place, not dated
│   ├── YYYY-MM-DD-slug.md     One file per spec (spec-driven development)
│   ├── implemented/           Specs whose work has shipped
│   └── archive/                Specs abandoned or superseded
├── runs/
│   └── YYYY-MM-DD-slug.md     One log per autonomous AI run
├── lessons.md                 Index/log of lessons learned
└── lessons/
    └── YYYY-MM-DD-slug.md     Full writeup for a lesson that needs more than a few lines
```

### Specs — `.ai/specs/`

This project does spec-driven development (SDD): non-trivial work is described in a spec before it's built.

- Before starting non-trivial work, write (or ask the user to confirm) a spec at `.ai/specs/YYYY-MM-DD-slug.md`, using today's date and a short kebab-case slug for the feature or change (e.g. `2026-10-14-multi-room-hosting.md`).
- A spec stays directly under `.ai/specs/` while the work it describes is in progress.
- Once the work is actually shipped, move the file unchanged into `.ai/specs/implemented/`. It becomes a historical record of what was planned and decided — don't rewrite it after the fact; if something changed during implementation, note that in the spec or in a follow-up spec, not by editing history.
- If a spec is abandoned, superseded by a different approach, or never implemented, move it into `.ai/specs/archive/` instead of deleting it.
- `product-brief.md` is the one file in `specs/` that isn't dated — it's a living document reflecting current product decisions and is updated in place, not moved.

### Runs — `.ai/runs/`

- Every autonomous AI run (an unattended/background agent run — not an interactive back-and-forth session) writes a log to `.ai/runs/YYYY-MM-DD-slug.md`, dated and slugged for what the run did.
- A run log should capture: what the run was asked to do, what it actually did, notable decisions made along the way, and the outcome (done / partial / failed / blocked).
- Run logs are history, not working notes — once a run has finished and its log is written, don't go back and rewrite it.

### Lessons — `.ai/lessons.md` + `.ai/lessons/`

Long-term memory for findings, gotchas and improvements — the kind of thing worth remembering after fixing a bug or hitting something non-obvious, so the next session (human or AI) doesn't rediscover it the hard way.

- Skim `.ai/lessons.md` before debugging something that feels like it could be a repeat, or before starting non-trivial work in an area that might have past gotchas.
- After fixing a bug, learning something worth keeping, or being corrected by the user, add a short entry to the top of `.ai/lessons.md` (newest first): date, one-line summary, enough context to be useful later.
- If a correction reveals a rule every future session should follow — not just a one-off gotcha — add it to this file directly instead of leaving it only in `lessons.md`; keep sharpening these rules until the same mistake stops recurring.
- If a lesson needs more than a few lines, write the full account in `.ai/lessons/YYYY-MM-DD-slug.md` and link it from the short entry in `lessons.md`.
- Don't delete or rewrite past lesson entries to tidy up; if a lesson is later found wrong or superseded, add a new entry saying so.

### Enforcement

- Follow these naming and placement conventions without being asked each time — they're part of this project's contract, not a one-off request.
- Move files between `specs/`, `specs/implemented/` and `specs/archive/` rather than deleting them; the history is the point.
- If a convention here seems wrong for a specific situation, say so and ask rather than silently deviating from it.
