# D06 — Once a player has already been auto-skipped, later phases pause for them instead of skipping again

- Date, owner: 2026-10-20, creator
- Context and the options weighed: Q02's follow-up asked whether, once a player has already missed one phase (triggering D04's auto-skip), the game should keep advancing by auto-skipping them on every later phase too, or pause and wait for them to return. This is resilience hardening beyond D04's single-phase timer, named but left undecided in Scope/Later.
- Decision and why: the creator chose pause — once a player has already been auto-skipped for a phase, the game waits for them on every subsequent phase ("even repeatedly") rather than continuing to auto-skip. D04 itself is unchanged: it still governs the single in-progress phase's timeout and resolution.
- Consequences, and what would make us revisit it: combined with D05 (indefinite resume) and N04 (no kick mechanism), this means a long-absent player can stall the game indefinitely for the rest of the group — flagged as A06 in the product brief, accepted as a known risk rather than resolved. Revisit if early playtests show this happens often enough to hurt real games.
- Status: active
