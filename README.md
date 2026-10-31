# star-trader

This is an online version of the classic board game from the 1980's

The game is for 2-6 players; multiple instances of the game can be played simultaneously

It's a Spring Boot Kotlin app with a static frontend. Games are stored in DynamoDB.

## Running locally

Requires JDK 17 and Docker.

```
./gradlew bootRun
```

The app serves on http://localhost:3000. `bootRun` also starts DynamoDB Local from `compose.yaml` (port 8000) and stops it on exit; games are kept in the `dynamodb-data` Docker volume, so they survive restarts. To start from an empty database, run `docker compose down -v`.

Without `RESEND_API_KEY` set, sign-in codes are printed to the server log instead of emailed.

To use real AWS DynamoDB instead, set `DYNAMODB_ENDPOINT_OVERRIDE=` (empty), `AWS_REGION`, and AWS credentials through the SDK's standard chain (environment or `~/.aws`), and create the `star-trader-games` table (partition key `id`, String) yourself; the app only creates the table on DynamoDB Local.