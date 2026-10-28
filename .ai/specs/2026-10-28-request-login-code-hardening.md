# Request-login-code hardening (issue #12 review fixes)

- Date: 2026-10-28
- Status: implemented on `feat/issue-12-request-login-code`

## Decisions

- Codes come from `SecureRandom`, 6 digits.
- Email is trimmed and lowercased in the service; whitespace/control characters are rejected.
- Name/email validation lives in `LoginCodeRequestService`, not the controller.
- Codes expire after 10 minutes (`InMemoryOneTimeCodeStore.CODE_TTL`); expired entries are purged on each `store`.
- The code is sent before it is stored, so a failed send keeps the player's previous code.
- `LoggingOneTimeCodeSender` is disabled under the `prod` profile; the real email adapter is #13.
- One `@RestControllerAdvice` maps `ResponseStatusException`, replacing per-controller handlers.

## Deferred

- `corporationName` is validated but not persisted; the verify-code story decides where it goes.
- Rate limiting on `/api/login/request-code`.
- Expiry is enforced when codes are read, which lands with the verify-code story.
