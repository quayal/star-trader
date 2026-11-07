# E01-S03 — Sign-in screens

**Brief:** `.ai/specs/product-brief.md` — D03, D10.
**Builds on:** E01-S01 (`POST /api/login/request-code`), E01-S01-T01 (email delivery), E01-S02 (`POST /api/login/exchange-code`, `GET /api/login/session`).
**Design:** `docs/design-system.md`, `static/css/tokens.css`, `components.css` (D10).

## 📝 TLDR

The login API exists or is specced, but there is no screen for it. A player enters name, corporation name and email, receives a one-time code by email, enters it, and is signed in. From there they go back to the game link they came from, or to "Create a game" (s11, E02-S05).

## 📝 Screens

| ID | Screen | Route (proposed) |
|---|---|---|
| s10 | Sign in: details, then code | `/#sign-in` (keeps a `next` link to return to) |

## 📋 Acceptance criteria

1. [s10] [D03] Step 1 asks for **Name**, **Corporation name** and **Email**, then **Send code**. Each field shows E01-S01's validation messages inline, with focus on the first error.
2. [s10] On success: "We sent a code to {email}. It may take a minute." Step 2 asks for the **Code** with **Sign in**, plus **Send a new code** and **Use a different email**.
3. [s10] A wrong or expired code shows E01-S02's message inline; the code field keeps focus.
4. [s10] When signed in, the player returns to the link they came from (a game's lobby or Deck), or to s11 if there is none.
5. [—] The session token is kept so a reload stays signed in; when the server no longer knows it, the player is sent back to s10 with the link kept.
6. [all game screens] Not signed in → s10, then back.
7. [—] For the first playtest the code must actually arrive by email (E01-S01-T01). Today `LoggingOneTimeCodeSender` only writes it to the server log, which remote players can't see.

## 📝 States checklist

| State | Covered by |
|---|---|
| Invalid details | 1 |
| Code sent / resend | 2 |
| Wrong or expired code | 3 |
| Session lost on reload | 5 |
| Email delivery failing | E01-S01-T01's behaviour; show "We couldn't send the code. Try again." |

## ❓ Open questions

None.

## 📋 Phasing and dependencies

- Depends on E01-S02 (exchange and session) and E01-S01-T01 (real email) for a remote playtest.
- E02-S05's lobby link flow depends on criterion 4.
