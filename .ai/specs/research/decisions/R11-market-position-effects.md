# R11 — Market position tiers have mechanical effects at trade resolution

- Date, owner: 2026-10-20, creator
- Context and the options weighed: the trade-offers mockup shows Retailer/Wholesaler/Dictator badges with only flavor-text tooltips; the code has a `CompetitivePosition` enum with the same three values but no logic using it. Asked whether these tiers are purely cosmetic or mechanically matter, and if so how.
- Decision and why: the creator confirmed they mechanically matter, at trade resolution: Retailer has no advantage; Wholesaler may always buy/sell up to 6 units after the main trade resolves, regardless of that trade's outcome; Dictator may submit an offer that automatically trumps competing offers, without needing to win the normal best-offer comparison (R08).
- Consequences, and what would make us revisit it: this is a decision this session captured for the first time — no prior brief content addressed it. How a player's tier is computed/advances was not asked in this session and remains unspecified; revisit if that gap blocks implementation.
- Status: active
