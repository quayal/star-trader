# Parking hangars (R127)

Date: 2026-11-23. Status: in progress. Source: the creator, 2026-11-23:

> Add parking hangars to the ports. All systems but Sigma and Mu Herc have them, and storing a ship in them makes them
> immune to sabotage. The cost is 5HT per turn and they can be used when landing in port during jump phase (additional
> yes/no popup). If ship was put in hangar in turn n (paying 5 HT) and didn't jump to another system in turn n+1, ask
> the player if they click `finish jumping` if it should stay in hangar for another turn (5 HT). On the map, hangar
> should be shown with P sign (like Parking), next to the Shipyard (add legend below, saying that this is Parking
> Hangar, protects ship from sabotage)

## R127: the Parking Hangar

1. **Where.** Gamma Leporis, Beta Hydri, Epsilon Eridani and Tau Ceti have one; Sigma Draconis and Mu Herculis don't.
   A ship in a hangar is still in the Port (it can jump from there, trade, be refitted); the hangar is a flag on it.
2. **Parking.** When a ship that jumped lands in the Port of a system with a hangar (R30), its owner is asked yes/no:
   park it for 5 HT? Yes pays (private, in their ledger) and the ship is parked for this turn. No leaves it as it was.
   The player isn't asked when they can't pay.
3. **Protection.** A ship parked this turn can't be sabotaged (R97). It isn't offered as a target, and an attempt on it
   is refused before anything is paid.
4. **Staying.** A ship parked in turn n that hasn't left its hangar in turn n+1 asks its owner, when they end their jump
   slot (the Finish jumping button, or the slot ending by itself once every ship has jumped or failed), whether it stays
   another turn for 5 HT. Yes pays and renews it for turn n+1; No takes it out. Each such ship is asked in turn, and the
   slot ends after the last answer. A "yes" the player can't pay for is refused, and they choose No.
5. **Leaving.** A ship that moves (a jump to another system, or into its own system's Space) leaves the hangar. Landing
   again in a Port with a hangar asks again (rule 2). A failed jump leaves the ship where it is, so it is still parked
   and is asked at the slot's end (rule 4).
6. **Visibility.** Which ships are parked is public: the map shows a `P` beside the Shipyard wrench on each system with a
   hangar, with a legend under the map, and system details mark parked ships. Parking and leaving are public log lines
   without the fee; the fee isn't public (R29).

### Defaults I chose where the creator's note was silent (cheap to change)

- **A ship placed in Port after a battle (R77) isn't asked** about the hangar, to keep the battle's landing as it is. It can be
  parked the next time it jumps and lands.
- **A parked ship asked again isn't charged until it answers yes**; a player who never ends their slot holds the game up,
  as for any other jump-slot step (D09).
- **A Port forced on a ship by Planet security (R116)** counts as landing in the Port: the hangar is offered.
- Selling a ship, or any other way it leaves the Port, drops the flag with the ship.

## Where each rule goes (standards §13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| which systems have a hangar | the system | 4 | `StarSystem.hasHangar()` |
| the fee | a constant | 4 | `Ship.HANGAR_FEE` |
| parked, protected, due for renewal, leaves on a move | one ship's flag, location, system and the turn number passed in | 5 | `Ship.parkedOn`, `Ship.isParkedOn`, `Ship.isDueHangarRenewal`, `Ship.jumpTo` |
| pay the fee, refuse without the money | one player's money and ship | 4 | `Player.parkShip`, `Player.leaveHangar`, `Player.canAffordHangar`, `Player.hangarRenewalsDue` |
| the question being asked, in order, and whether its last answer ends the slot | the question's ships | 5 | `HangarAsk` (new value type in `game`), held by `Turn` |
| nothing else happens while it's open | the turn's question | 5 | `Turn.refuseWhileHangarAsked` |
| sabotage on a parked ship refused | one player's ship and the turn number | 4 | `Player.checkSabotageTarget` (takes the turn number) |
| whose turn, log, ending the slot | players, turn, log | 7 | `Game.answerHangar`, `Game.landShip`, `Game.endJumpSlot` |
| persistence | | 1 | `ShipItem` (`hangarPaidOnTurn`), `TurnItem` (`hangarAsk`) + round-trip tests |
| DTOs | | 1 | `ShipResponseDto.hangar`, `SystemResponseDto.hangar`, `TurnResponseDto.hangarShip`; map only |
| the popup, the P on the map and its legend, target list | | 9 | `hangar.js`, `board.js`, `index.html`, `opportunity.js` |
