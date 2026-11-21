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
9. **One possible amount** (creator, same day: the slider looked stuck at its minimum): when only 1 unit can be traded, a
   browser draws a 1..1 slider with its thumb at the left, which reads as nothing. Such a slider is drawn full and locked
   at 1 instead. A trade must be 1 to the max (`Player.trade`), so there's no other value to move to. The same goes for
   the invest dialog's and Move goods' sliders, through one `fitSlider` in `index.html`.

## Verification

JS unit tests for each helper (`bidStatus`, `asksBeforeDone`, `tipOffGroups`, `turnHeading`, `phaseScreenLink`,
`placeMax`, `rebalanceSplit`), and a browser walk on a separate local instance: the dialog with Not yet and Yes, the bid
box before and after a bid, a poll moving an open Deck to News when Initiative ended, the grouped tip-offs, the Guide card
heights (Initiative 94 px), and a buy of POLY at Tau Ceti split across two ships with the linked sliders (10 → 8 + 2; Wayfarer to 3 → Lantern 7; Lantern to 9 → Wayfarer 1 with the loss warning; typing 12 → 8 + 4), confirmed
as 4 + 8 and stored by the server as such.

## Second round (creator, same day)

10. **A slider's number is where its thumb is.** With nothing possible (max 0, e.g. no money for a factory unit) the
    slider sits at the left and reads `0 units`, not `1`. With one possible amount it's full and locked at 1 (criterion 9).
11. **Bid box:** the current bid is styled like the panels' titles ("Your fleet"), not larger. The text is cut to "Your
    initiative is your bid + 2d6." under the field, "Optional." before a bid and "You can change it until Bidding
    closes." after one.
12. **Command deck layout:** left of the map, Your fleet, Your agents, Your account and the turn order; right of it, what
    must be decided first (battle, jump, connections, bid), then Place an offer, Your offers, Your trades and Your
    market positions. **What blocks goes on top:** a panel waiting on the viewer moves to the top of its column (the turn
    order while it's the viewer's pick).
13. **Opportunity tab:** the Opportunity phase moves off Infrastructure onto its own tab (nav between Trading and
    Infrastructure), which opens by itself when the phase starts (D15). From the top: the output to collect, whose turn
    it is (with the done button and any sabotage hits to place), agents, sabotage, the learned opportunities. Outside the
    phase the tab says when it comes; agents still move from there. Infrastructure keeps Investment and the loan, and
    during Opportunity points to the new tab. The creator plans two more Opportunity features; they'll go here.
14. **Shorty in the sabotage tile:** the modifiers line adds `+4 Shorty` to both rolls when the viewer's Shorty is at the
    chosen target's system (anywhere there: Port, Planet or aboard), as the server already rolled it (R108). It follows
    the chosen target and the poll.
15. **News pop-ups:** an event taking place and an opportunity expiring each get a card everyone acknowledges, like
    interceptions and battles. An event's card lists what it did (ships lost, reputation, tax) under its headline. The
    server marks those log lines with new notice kinds (`NEWS_EVENT`, `OPPORTUNITY_EXPIRED`), which have no system, so
    `Notice.system` is now optional (stored only when set; older items always have one).

Verified with JS and Kotlin tests (`shortyBonus`, the Opportunity route and phase mapping, the News cards, the notice
round trip without a system, the event lines' notice) and a browser walk: the deck columns, the bid box, the page
moving to the Opportunity tab with its panels in order, the Pirates event's card in News, and the factory dialog with
0 HT reading `0 units`. Shorty's bonus isn't in the walk: recruiting him is a random draw, so it's covered by the unit
test only.
