# Playtest UI feedback, 2026-11-21

**Brief:** `.ai/specs/product-brief.md`: R27 and R29 (initiative bid), R17 and D09 (done bidding), R109–R110 (News and
tip-offs), D15 (the screen follows the phase).
**Source:** the creator's UI notes of 2026-11-21, after playing the build with News (PR 171).
**Status:** implemented on `feat/playtest-ui-2026-11-21`.

## 📝 TLDR

Six frontend fixes, no rule changes and no API changes:
- the Guide's Initiative card is no taller than its one line;
- the Deck is called **Command deck**;
- the **bid box shows the placed bid**, says it can still change, and says a bid is optional;
- **"I'm done bidding" asks first** when the player has placed no offers and no bid;
- **News opens when News starts** (it was a bug), and its **tip-offs are grouped by turn**;
- the Trading **amount is a slider**, and with two or more places to put it, **each place has a linked slider**.

## 📋 Acceptance criteria

1. **Guide:** each card is as tall as its own lines. The Initiative card was stretched to the height of the tallest card
   in its row (Sabotage).
2. **Command deck:** the nav item reads "Command deck", and so do the links back to it ("Back to the Command deck", "Go to
   the Command deck"). The page title was already "Command Deck".
3. **The bid box** shows "Your bid" with the amount large (`12 HT`), or `None yet` before a bid. Under it:
   - before a bid: a bid is optional, and without one the player bids 0 HT;
   - after: it's placed and can be changed until Bidding closes, and it's paid then, win or lose.

   The bid no longer appears in "Your offers this turn" while Bidding is open; once paid, it does, as before.
4. **Done with nothing placed:** pressing "I'm done bidding" with no offers and no initiative bid opens a dialog: "Are you
   done bidding?", saying nothing is placed and that Bidding closes as soon as the last player is done. "Yes, I'm done"
   signals done; "Not yet" (or Escape) doesn't, and focus returns to the button. With a bid (even 0 HT) or any offer, done
   is signalled at once, as before.
5. **News opens when News starts.** D15 sends every player to News when the phase starts, but the page had no link for
   that screen and threw instead (`link is not a function`), so players stayed where they were. The link per screen now
   lives in `game.js` (`phaseScreenLink`) with a test that every screen a phase change can open has one.
6. **Tip-offs by turn:** the Tip-offs card groups them under the turn they take place or expire, soonest first: "This
   turn (turn 3)", "Next turn (turn 4)", then "Turn 6". The per-row "effective turn N" goes. Within a turn the order
   doesn't matter (creator), so it's the server's.
7. **Amount slider** (creator, same day: "change amount to buy to slider"): the Trading amount form (a trade at the
   counter, and the wholesale step, which uses the same form, buy or sell) has a slider from 1 to the max, linked to the
   number field beside it so an exact amount can still be typed. Moving either moves the other.
8. **Linked place sliders:** with two or more places to put the goods (ships in port, plus the warehouse there), each place
   gets a slider and shows its units. Moving one moves the others so they always add up to the amount: what it takes comes
   off the last places first, and what it gives back goes to the first places (into a buy's free space first). A buy may
   still put more on a place than fits, and the loss warning says so as before; a sell's slider stops where the others can
   still cover the rest. Changing the amount resets the places to the default split. With one place, it takes the whole
   amount and only shows it.

## Verification

JS unit tests for each helper (`bidStatus`, `asksBeforeDone`, `tipOffGroups`, `turnHeading`, `phaseScreenLink`,
`placeMax`, `rebalanceSplit`), and a browser walk on a separate local instance: the dialog with Not yet and Yes, the bid
box before and after a bid, a poll moving an open Deck to News when Initiative ended, the grouped tip-offs, the Guide card
heights (Initiative 94 px), and a buy of POLY at Tau Ceti split across two ships with the linked sliders (10 → 8 + 2; Wayfarer to 3 → Lantern 7; Lantern to 9 → Wayfarer 1 with the loss warning; typing 12 → 8 + 4), confirmed
as 4 + 8 and stored by the server as such.
