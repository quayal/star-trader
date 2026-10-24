# N06 — A v1 turn is exactly three phases; the other 5 don't exist

- Date, owner: 2026-10-20, creator
- Context and the options weighed: the creator mentioned the original board game has 8 turn phases total, and that v1 implements "only bidding and trading" — initially recorded as a 2-phase turn with the other 6 phases out of scope. An independent skeptic review caught that R04, the Phase glossary row, and Key flow step 4 (all untouched by this session) still treat "ship jumps" as a live, in-scope example of an ordered phase, contradicting a 2-phase turn. Asked the creator directly: is ship-jump movement one of the dropped phases, or a third phase that survives?
- Decision and why: ship-jump movement is a third phase that survives alongside Bidding and Trading — it was already specified by R04 in the original architecture session. A v1 turn is Bidding, then Trading, then ordered ship-jump movement, repeated. The other 5 of the original 8 phases don't exist for v1 at all, not merely deferred.
- Consequences, and what would make us revisit it: this corrects the brief's first draft of this rule (which said "2 phases, other 6 dropped") before it was ever shown as final — no superseding entry was needed since the incorrect version was never confirmed as written. Revisit once Bidding/Trading/ship-jumps are built and playtested.
- Status: active
