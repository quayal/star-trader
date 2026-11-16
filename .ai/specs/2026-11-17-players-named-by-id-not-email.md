# Players are named by an id, never by their email address

**Brief:** `.ai/specs/product-brief.md` — R29 (what is private), A05 (a non-player learns nothing), D03 (email-code login).
**Source:** the project review of 2026-11-16, finding 2; the creator asked for it to be fixed (2026-11-17).

## 📝 TLDR

Every response of the game API named players by their email address: `players[].email`, `creatorEmail`, the initiative and turn order, who is done, whose offer or trade it is. The screens never showed an email, but anyone in a game could read every other player's address from the JSON. Responses now name a player by an **id**: `p1` for the creator, `p2` for the next to join, and so on. No response carries an email, the caller's own included.

## 📋 Acceptance criteria

1. In every game response a player is named by their id: `players[].id`, `creatorId`, `you.id`, and `playerId` wherever an offer, trade or wholesale names its player. Every list that held emails (`doneBidding`, `initiative`, `turnOrder`, `doneJumping`, `doneInvesting`, `collected`, `wholesalers`, `demoted`, `winners`, `standings[].id`, `dictator`, `awaitingDictator`, `awaitingWholesaler`) holds ids.
2. A test polls a lobby and a running game as a player and finds no `@` anywhere in the body.
3. An id is the player's place in join order. Players are only added, and only before the start, so it never changes and needs no storing. It means nothing outside its game.
4. Requests are unchanged: none ever carried an email, the caller being known from their session.
5. Inside the game nothing changes: a player is still keyed by their email (`Player.email`), and stored games are untouched. The translation is at the web border only (`PlayerIds`, engineering standards §3).
6. The page knows which player it is from `you.id`, not from the session's email.
7. `BACKWARD_COMPATIBILITY.md` §6 states the renamed fields; this is a breaking change to the response shape, made while the only consumer is the in-repo frontend, updated in the same commit.

## Defaults I chose

- **Join order, not a random stored id.** It needs no new stored field and no migration, and it reveals nothing the player list doesn't already show in the same order.
- **The fields are renamed** (`email` → `id`, `playerEmail` → `playerId`, `creatorEmail` → `creatorId`) rather than kept with new values: a field called `email` holding `p2` would mislead the next reader.
