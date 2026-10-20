# R10 — A resolved trade updates money and market position; selling is capped by inventory

- Date, owner: 2026-10-20, creator
- Context and the options weighed: `Player` already has unused `money` and `positions` fields; nothing in the code updates them when a trade resolves, and there's no concept of a finite goods pool anywhere. Asked whether money/position/inventory are part of the MVP trading loop, and whether ship/warehouse/system stock caps what a trade can move.
- Decision and why: the creator confirmed money and market position are part of the MVP trade outcome, together with the quantity of goods held in warehouse and ship-in-port. A sell is capped by what the player actually holds there. Buying has no system-side stock cap — the creator clarified that a system's supply/demand is fully simulated through its S/D indicator and the dice roll, not a depleting pool, so there is nothing to cap against on the buy side.
- Consequences, and what would make us revisit it: implementing the sell-cap needs the actual ship/warehouse inventory model (capacities, how goods move between them, starting stock), which was not covered in this trading-only session — tracked as Q03 for its own discovery session.
- Status: active
