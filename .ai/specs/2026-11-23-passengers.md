# Passengers in Opportunity (R125)

Creator's request, 2026-11-23. Players carry passenger groups between systems: a fare paid up front when they board, a
penalty if the ship isn't in the destination Port by the next Opportunity.

## Rules

1. **Offers.** In every Opportunity, every route from system A to system B (A ≠ B) has `portClass(A) + portClass(B)`
   passenger groups on offer (Beta Hydri 4 → Tau Ceti 3 = 7; Mu Herculis 0 → Gamma Leporis 1 = 1). They are fresh each
   turn: groups nobody boards are gone when the turn's Opportunity ends.
2. **Taking.** On their Opportunity turn after Collect (R102, R111), a player with reputation 15 or more boards `n` groups (1 or more) onto one of
   their ships in the origin's Port, bound for a destination of their choice. Players take in turn order; what one
   player takes the next can't. If one player takes every group of a route, nobody else can board for it.
3. **Seats.** One group takes one passenger seat (R52: a Passenger module has 2, so a full module holds 2 groups). A ship's
   free seats are its working Passenger modules' seats, less slaves, a Passenger-module delivery and groups already booked
   on it. A ship may carry groups for several destinations. Booked passengers are never evicted: while groups are booked,
   slaves, a delivery or a Passenger module taken off (storage, swap, refit) that would leave fewer seats than are booked
   is refused. Likewise a module that is taken stays taken: nothing can reclaim its seats.
4. **Fare.** 5 HT per group, paid to the player at once, when they board (private amount, in their ledger).
5. **The player owes the trip, not the ship** (creator, 2026-11-23). A booked group is the player's obligation until the next
   Opportunity opens. It is delivered only if its ship is then in the destination's Port and has working seats for it.
6. **Penalty, no excuses.** Every group not delivered, for any reason (jump failed, ship elsewhere, destroyed, seized or
   sold, Passenger module damaged and not repaired by then), costs 200% of its fare, 10 HT a group, charged as the next
   Opportunity opens, and is gone. The player pays as far as their cash goes: at 0 HT the rest is forgiven, and money never goes
   below 0 through the penalty. A damaged module must be repaired in the same Investment: seats beyond the working
   ones fail, so a ship with 2 groups and one working module (2 seats) is fine with 2, short with 3.
7. **Visibility.** Boarding and each penalty are public: a log line (so a toast for everyone else) such as `Vega Lines took
   2 passenger groups from Beta Hydri to Tau Ceti.` How many groups of each route are left is public. What a player has
   booked is private.
8. **The pool never refills.** Groups taken stay taken for the turn, whatever happens to the ship afterwards.

## Defaults I chose (creator may change)

- Seats are checked when booking, whenever something else would take them, and again at settlement (a module damaged in a
  battle, which nobody chose, is the one way a booked group loses its seat, and costs the penalty).
- Ships can still be sold with passengers booked; the penalty follows the player.
- No new notification card: the penalty is a public log line, a toast to everyone (a setback card for the owner, like R124's,
  can follow).

## Where each rule goes (standards §13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| groups on offer for a route | two systems' port classes | 4 | `PassengerRoute.groups()` (new value type in `game`) |
| a group's fare and penalty | its group count | 6 | `PassengerGroup.fare()`, `.penalty()` (bookings are `PassengerGroup(ship, from, to, groups)` on `Player`) |
| a ship delivers groups bound for a system, as far as its seats go | one ship's position, seats | 5 (values passed in) | `Ship.passengersDeliveredTo` |
| boarding: ship in the origin Port, free seats, fare | one player's ships, bookings, money | 4 | `Player.boardPassengers`, `Player.freeSeatsOn` |
| settling: delivered or penalty, bookings cleared | one player's ships, bookings, money | 4 | `Player.settlePassengers` |
| groups of a route taken | one player's bookings | 4 | `Player.passengerGroupsOn(route)` |
| how many are left, whose turn, log | all players, turn, log | 7 | `Game.takePassengers`, `Game.passengerGroupsLeft`, `Game.withPassengersSettled` |
| persistence | | 1 | `PassengerGroupItem` in `PlayerItem` (+ round-trip test) |
| route table, ship's groups for the page | | 1, 9 | `GameResponseDto`, `YourShipResponseDto` map only |
| the form | | 9 | `opportunities.js` + `index.html` |
