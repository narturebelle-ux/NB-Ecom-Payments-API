# NB-Ecom-Payments-API

## Run locally

The API needs a Postgres database that already holds Care's tables in `public`. Set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` first. Starting it applies this repo's migrations, so use a private local database and never production.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew bootRun
```

Check it at http://localhost:8083/api/v1/hello, which returns "Hello Payments".

## Tests

```powershell
.\gradlew test
```

Tests start their own private Postgres, so they need no database settings. Windows must have about 2 GB of memory free.

They use a stand-in for Care's tables. To run them on Care's real ones, set `CARE_MIGRATIONS_DIR` to a copy of Care's `src/main/resources/db/migration` folder, and run `.\gradlew cleanTest test`.

## Status

This is a copy of the Care API's money code. It isn't live, and Care still serves every request.

- **Sign-in:** set `CARE_API_URL` and `INTERNAL_API_TOKEN` to use Care's private lookups. While `CARE_API_URL` is unset, every signed-in caller is refused, so only `/api/v1/hello`, `/api/v1/plans` and `/health/db` work.
- **Events:** `PaymentSucceeded` and `RefundIssued` are saved in `money.outbox_events`, but nothing delivers them yet (F4-S4, D18). Until then, Care doesn't grant sessions from this API.
- **Private endpoints for Care:** `POST /internal/v1/orders/reward`, `GET /internal/v1/users/{id}/order-summary` and `GET /internal/v1/users/{id}/money-export`. The export adds refunds and instalment schedules to what Care exports today.
- **Older orders:** an order with no `money.plan_validity` row uses an expiry estimated from its payment or order date. Not checked against production rows.
- **Tables:** plans, orders, payments and refunds stay in `public`, migrated by Care. This repo migrates only the `money` schema.
