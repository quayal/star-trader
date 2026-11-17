# Withdrawing an offer during Bidding

**Brief:** `.ai/specs/product-brief.md` — R17, R33, A04, and the new R59 added with this spec.
**Source:** the creator's request of 2026-11-17, during a playtest: "add an option to clear offers while in bidding phase; just simple x or cancel".

## TLDR

A placed offer could not be undone; a typo in the price stuck for the whole turn, since R33 allows one offer per good per system. Each row of "Your offers this turn" now has a small × while Bidding is open, which withdraws that offer.

## Rule

| Id | Rule |
|---|---|
| R59 | While Bidding is open, a player may withdraw any of their own offers of this turn, then place a new one on the same good and system (R33 counts offers standing). Once Bidding closes, offers are final (A04). Private (R17), not logged. |

## Acceptance criteria

1. [R59] `DELETE /api/games/{id}/offers/{system}/{goods}` (`StarSystem` and `Goods` names) removes the caller's offer on that good at that system and answers `200` with the game; the offer is gone from `you.offers`.
2. [R59] Idempotent: withdrawing an offer the caller doesn't have (already withdrawn, a double click) answers `200` with the game unchanged. Another player's offer on the same good and system is never touched.
3. [R33] After withdrawing, the same player can place a new offer on that good and system.
4. [A04] `409` "Bidding has closed" once it has, even for an offer that doesn't exist; `400` "Choose a star system." / "Choose a good." for an unknown name; `409`, `403`, `404`, `401` otherwise as for placing.
5. [R59] Works after the player has signalled done, like placing does (they're still in Bidding).
6. UI: each offer row in "Your offers this turn" shows a × button named "Withdraw offer: Buy FOOD at 12 HT, Sigma Draconis" while Bidding is open, and none after. A click removes the row, and the offer form's goods list offers that good again. A refusal shows under the list.

## Out of scope

- Editing an offer in place (withdraw then place again does it).
- Logging withdrawals: offers are secret (R17), so is taking one back.
