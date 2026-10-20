# D05 — A disconnected player can resume their same seat at any time, no expiry

- Date, owner: 2026-10-20, creator
- Context and the options weighed: Q02 asked what happens when a player disconnects or closes the tab mid-game, beyond D04's per-phase auto-skip. Options presented: resume indefinitely via re-login, resume within a time-limited window, or leave undecided.
- Decision and why: the creator chose indefinite resume — a player re-authenticates (D03) and rejoins the same seat in the same game with no expiry. This follows naturally from the already-chosen stateless email-code login and persisted per-game state (D02); no extra mechanism is required to allow it.
- Consequences, and what would make us revisit it: combined with D06 and N04, an indefinitely-resumable seat with no removal mechanism means an absent player can stall a game forever. See A06 in the product brief. Revisit if real play shows players want (or need) an expiry.
- Status: active
