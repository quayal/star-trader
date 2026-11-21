# Open games and join requests

Date: 2026-11-21 · Status: in progress · Rules: R119, R120 · Decision: D28

## Why

The creator asked (2026-11-21): players should see the games waiting to start (who created them, which corporations are
in, how long they are) and click to join, and the creator then accepts. Today a player can only join through a link with
the game id in it, which the creator has to send around. With this, the only thing to send is the service's URL (the
ngrok endpoint, D10).

## What changes

**R119 — Open games.** The Lobby screen (s11) lists every game still in its lobby, for every signed-in player: the
creator's corporation and name, the corporations already in, the number of players, and the length in turns. Each row
says where the viewer stands and offers what fits: *Ask to join*, *Waiting for {creator} to accept* with *Cancel*, or
*Open* for a game they are in. Started and finished games aren't listed. The list refreshes itself while it's on screen.

**R120 — Joining needs the creator's acceptance.** A player asks to join a game in its lobby; the creator sees each request
in the lobby, with the player's corporation and name, and accepts or declines it. Accepting adds the player (the usual
checks: still in the lobby, not full). The requester can cancel their request. Starting the game drops any request still
waiting. A request is refused when the game has started, is full, or the player is already in it or has already asked.

This applies to the game link too: someone opening a link to a game they're not in gets *Ask to join* instead of *Join
this game*. Otherwise the open list, which has to show game ids to be clickable, would let anyone skip the creator by
calling the old join directly. `POST /games/{id}/join` is removed (its only consumer is our page, updated here).

While waiting, the requester stays on the Lobby screen. When the creator accepts, the page opens the game for them; when
their request disappears without them being let in, it says the request was declined (or the game is no longer open, if
it left the list).

**Not in this change:** a "created at" time or age of a game (games don't record one yet; "how long" is read as the
length in turns), a private/unlisted game option, limits on how often a declined player can ask again.

## Placement (engineering standards §13)

| Rule | Data it reads / changes | §13 row | Where it goes |
|---|---|---|---|
| A player may ask to join only a lobby that isn't full, once, and not when already in it | status, players, join requests | 7 (membership) | `Game.requestToJoin(player)` |
| Only the creator accepts or declines; accepting moves the requester into the players with `addPlayer`'s checks | creator, join requests, players | 7 | `Game.acceptJoinRequest(callerEmail, email)`, `Game.declineJoinRequest(callerEmail, email)` |
| The requester can withdraw their request | join requests | 7 | `Game.withdrawJoinRequest(email)` |
| Starting drops waiting requests | join requests | 7 | `Game.start` |
| Where a viewer stands in a lobby: in it, asked, or neither | players, join requests | 0 / 7 | `Game.lobbyStandingOf(email)` → `LobbyStanding` |
| Listing every game still in its lobby | the store | 2/3 | `GameStore.lobbies()` port (fake + DynamoDB scan), `OpenGamesService` |
| Naming a request in HTTP without its email | mapping | 1 | `JoinRequestIds` in the web adapter: `r` + a hash of game id and email, stable while the request exists, so accepting a stale row can never let in someone else |
| Rows' texts and actions | the standing the server sends | 9 | `open-games.js` formats only |

Join requests are stored on the game item (`joinRequests`, a list of player maps like `players`); an item without the
attribute reads as no requests (BACKWARD_COMPATIBILITY.md §7). The DynamoDB listing is a filtered, projected scan: fine
for the playtest's handful of games; a status index is the next step if the table grows.

## HTTP

- `GET /api/open-games` → `200 [{ id, turns, creator: { name, corporationName }, corporations: [..], players, maxPlayers, standing: IN | ASKED | NONE }]`, the viewer's own games first.
- `POST /api/games/{id}/join-requests` → `200` the row above; `409` started / full / already in / already asked; `404`.
- `DELETE /api/games/{id}/join-requests/mine` → `200` the row; withdrawing a request the caller doesn't have changes nothing.
- `POST /api/games/{id}/join-requests/{requestId}/accept` and `/decline` → `200` the game; `403` for anyone but the creator; `409` for an unknown request (already handled or withdrawn), full or started.
- The game shape gains `joinRequests: [{ id, name, corporationName }]`, filled in the lobby.

## Done when

- Domain tests cover each rule above, including a non-creator accepting and a stale request id.
- Item round trip covers `joinRequests`; controller test covers the list, request, accept, decline and withdraw.
- Browser walk: two signed-in players; B sees A's game in the list, asks, A accepts in the lobby, B's page opens the game.
