# R12 — A BUY is capped by what the player can afford

- Date, owner: 2026-10-20, creator
- Context and the options weighed: R10 established that a SELL is capped by the player's ship/warehouse inventory, with no equivalent cap for a BUY. Asked whether a BUY should similarly be capped by the player's money (price × amount), or whether a trade can leave a player in debt (possibly tying into the existing but unused `Loan` mechanic).
- Decision and why: the creator chose to cap a BUY by affordability — if the formula-computed amount (R09) would cost more than the player's current money, the traded amount is scaled down to what they can actually pay.
- Consequences, and what would make us revisit it: this makes BUY and SELL symmetric — each is capped by the player's own resource (money vs. inventory), never by the system. The unused `Loan` mechanic remains out of scope (N05); revisit if going into debt via a loan becomes a wanted mechanic later.
- Status: active
