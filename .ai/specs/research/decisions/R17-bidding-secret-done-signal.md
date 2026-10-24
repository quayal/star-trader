# R17 — Bidding is secret; signaling done is a readiness flag, not a commit

- Date, owner: 2026-10-20, creator
- Context and the options weighed: `TradeOfferController.list()` currently returns every player's offers publicly in real time, with no scoping mechanism anywhere in the codebase. Asked how the server should know a player is "done" for the phase, and whether that's a one-way commit or a flexible readiness signal.
- Decision and why: during Bidding, a player may submit any number of offers across systems/goods, invisible to everyone else until Trading resolves. Signaling done is a readiness flag, not a commit — a player may keep submitting offers even after signaling done, until Bidding actually closes (every player has signaled done, or D04's timer catches a straggler).
- Consequences, and what would make us revisit it: today's `/api/trade-offers` endpoint directly contradicts this rule (it's fully public) — a known implementation gap tying into the architecture brief's A05 (confidential-read authorization). This needs a per-player-scoped offers view before Bidding secrecy is real.
- Status: active
