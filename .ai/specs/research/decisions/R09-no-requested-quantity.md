# R09 — A trade offer carries no requested quantity

- Date, owner: 2026-10-20, creator
- Context and the options weighed: `design/mockups/trade-offers.html` shows a quantity input (e.g. 500 units) on a trade offer, but `TransactionAmountCalculator` derives a traded amount purely from the winning price's offset from the system's base price and the roll+indicator, with no quantity input anywhere in the domain code. Asked which was authoritative.
- Decision and why: the creator confirmed the formula alone decides the traded amount; the mockup's quantity field is stale and should be corrected, not treated as a product requirement.
- Consequences, and what would make us revisit it: `trade-offers.html` needs a design fix to drop the quantity input. No further scope impact — R08's resolution mechanic already covers amount calculation.
- Status: active
