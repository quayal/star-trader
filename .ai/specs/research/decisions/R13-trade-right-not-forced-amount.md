# R13 — Winning a trade grants a right to trade 1-to-max, not a forced amount (supersedes R09)

- Date, owner: 2026-10-20, creator
- Context and the options weighed: R09 (recorded earlier this session) stated the formula-computed amount was simply "the traded amount... not chosen by the player." Follow-up discussion surfaced that the player actually chooses how much of that computed maximum to execute.
- Decision and why: the creator confirmed the formula computes a *maximum* a winner has the right to trade, not a forced amount — the player may execute anywhere from 1 unit up to that maximum (never 0; a winner must trade at least one unit). The maximum itself is the distance between the dice-resolved position and the target indicator implied by the winning price, using R15's clamped value for an extreme bid.
- Consequences, and what would make us revisit it: this corrects, not just extends, R09 — R09's "not chosen by the player" framing was wrong and is marked superseded. R14 (granular price update) and R16 (storage overflow) both build on this right-not-forced framing.
- Status: active (supersedes R09)
