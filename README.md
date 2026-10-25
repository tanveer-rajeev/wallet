# Wallet

A user can deposit money, send money to each other (with a fee), and pay bills through an unreliable external biller.

The main goal is **correctness with money**. No money is created or lost, retries never move money twice, balances never go below zero under parallel requests, and every operation is traceable in an append-only ledger.

**Stack:** Java 21 · Spring Boot 4 · Spring Data JPA (Hibernate) · PostgreSQL 16 · Flyway · Maven · Testcontainers

---

## Quick start

You need Java 21, Maven and Docker.

```bash
docker compose up -d      # start PostgreSQL
mvn spring-boot:run       # start the service on http://localhost:8080
mvn test                  # run all tests (Docker must be running)
```

Tests start their own real PostgreSQL with Testcontainers. There is no in-memory database anywhere.

### Configuration

| Property | Default | Meaning |
|---|---|---|
| `spring.datasource.*` | `localhost:5432/wallet`, user and password `wallet` | Database (matches `docker-compose.yml`) |
| `biller.base-url` | `http://localhost:9090` | Base URL of the external biller |
| `bill-payments.reconcile-interval-ms` | `30000` | How often open bill payments are checked again |

> No real biller is included. Without one, bill payments are saved and stay `PENDING`. The tests use a fake biller.

---

## API

Every request that moves money needs an **`Idempotency-Key`** header (use a new UUID for each new operation, and reuse the same key when retrying).

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/wallets` | Create a wallet (balance starts at 0) |
| `GET` | `/api/v1/wallets/{id}` | Wallet details and balance |
| `POST` | `/api/v1/wallets/{id}/deposits` | Top up a wallet (cash-in) |
| `POST` | `/api/v1/transfers` | Send money from one wallet to another |
| `POST` | `/api/v1/bill-payments` | Pay a bill from a wallet |

### Try it

```bash
# 1. Create two wallets
curl -X POST localhost:8080/api/v1/wallets -H "Content-Type: application/json" \
  -d '{"ownerId":"ali"}'
curl -X POST localhost:8080/api/v1/wallets -H "Content-Type: application/json" \
  -d '{"ownerId":"sara"}'

# 2. Deposit 200,000 BDT into Ali's wallet
curl -X POST localhost:8080/api/v1/wallets/<ALI_ID>/deposits \
  -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":200000}'

# 3. Ali sends 100,000 BDT to Sara (fee 500, so Ali pays 100,500)
curl -X POST localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" \
  -d '{"fromWalletId":"<ALI_ID>","toWalletId":"<SARA_ID>","amount":100000}'

# 4. Check the balance
curl localhost:8080/api/v1/wallets/<ALI_ID>
```

Transfer response:

```json
{
  "transactionId": "…",
  "fromWalletId": "…",
  "toWalletId": "…",
  "amount": 100000,
  "fee": 500,
  "createdAt": "2026-10-24T10:15:30.123456Z"
}
```

A bill payment returns `{ "id", "walletId", "amount", "status", "createdAt" }`, where `status` is `SUCCEEDED`, `FAILED` or `PENDING`.

### Errors

Errors use the standard `ProblemDetail` format with a stable `code` field:

```json
{ "title": "INSUFFICIENT_FUNDS", "status": 422, "detail": "Wallet … has insufficient funds",
  "instance": "/api/v1/transfers", "code": "INSUFFICIENT_FUNDS" }
```

| Status | Codes |
|---|---|
| 400 | `VALIDATION_FAILED` (with an `errors` map), `INVALID_IDEMPOTENCY_KEY`, malformed JSON, missing header, bad UUID |
| 404 | `WALLET_NOT_FOUND` |
| 422 | `INSUFFICIENT_FUNDS`, `WALLET_FROZEN`, `SYSTEM_WALLET_NOT_ALLOWED`, `SAME_WALLET_TRANSFER`, `IDEMPOTENCY_KEY_REUSED` |
| 500 | `INTERNAL_ERROR` (no internal details are returned) |

---

## Business rules

- Amounts are **whole dinars**, from 1 to **5,000,000**. Anything else is rejected (this applies to deposits, transfers and bill payments).
- **Transfer fee:** 0.5% of the amount, rounded HALF_UP to whole dinars, **minimum 250**, **maximum 10,000**. The sender pays `amount + fee`. Deposits and bill payments have no fee.

  | Amount | Fee |
    |---|---|
  | 10,000 | 250 (minimum) |
  | 50,100 | 251 (250.5 rounds up) |
  | 100,000 | 500 |
  | 5,000,000 | 10,000 (maximum) |

- A customer balance **never goes below zero**.
- A **frozen** wallet cannot send money or pay bills. It can still receive money. There is no endpoint to freeze a wallet; set it in the database: `UPDATE wallets SET status = 'FROZEN' WHERE id = '…'`.
- Customers **cannot use the system wallets** (fee, cash-in, biller clearing) to send, receive or deposit. They can read them with `GET`.
- Sending money to your own wallet is rejected.

---

## How money is modeled

### System wallets

Three wallets are created by a Flyway migration, with fixed ids. They exist so every dinar always has a "from" and a "to".

| Wallet | Role |
|---|---|
| `CASH_IN` | Source of every deposit. Its balance goes **negative** on purpose: it is the total cash we hold for customers. |
| `FEE` | Receives the fee of every transfer. |
| `BILLER_CLEARING` | Holds money for bill payments. After settling, it equals what the biller received. |

### Ledger

Every operation writes one row in `transactions` and one row per movement in `ledger_entries`. A `DEBIT` lowers a wallet's balance and a `CREDIT` raises it. For one operation, debits always equal credits.

Example: Ali deposits 200,000, then sends 100,000 to Sara:

| Operation | Account | Direction | Amount |
|---|---|---|---|
| Deposit | CASH_IN | DEBIT | 200,000 |
| Deposit | Ali | CREDIT | 200,000 |
| Transfer | Ali | DEBIT | 100,500 |
| Transfer | Sara | CREDIT | 100,000 |
| Transfer | FEE | CREDIT | 500 |

`transactions` and `ledger_entries` are **append-only**: a database trigger rejects `UPDATE` and `DELETE`. A mistake is fixed with a new operation, never an edit. For example, a declined bill payment is undone by a new `BILL_REFUND` operation.

### Proving the books add up

```sql
-- 1. All balances add up to zero
SELECT SUM(balance) FROM wallets;

-- 2. Total credits equal total debits
SELECT SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END) FROM ledger_entries;

-- 3. Wallets whose balance does not match their own ledger (should return no rows)
SELECT w.id FROM wallets w
WHERE w.balance <> (
    SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0)
    FROM ledger_entries WHERE wallet_id = w.id);
```

The integration tests run these same three checks after every test.

---

## Design decisions

### All or nothing
Each operation runs in **one database transaction**: the ledger rows, the balance changes and the notification row commit together or not at all. A crash can never leave a debit without its credit, or a fee without its transfer.

### Parallel requests
The wallets an operation changes are locked with `SELECT ... FOR UPDATE`, **always in id order**.
- Requests on the same wallet wait in line, and each one sees the latest balance. I chose locks over optimistic retries because many requests can hit one wallet at the same time, and I want them to queue instead of failing.
- A fixed order means A→B and B→A at the same moment cannot deadlock.
- A `CHECK` constraint on the table is the last line of defense for the "no negative balance" rule.

### Idempotency (safe retries)
- **The client makes the key.** The server never makes keys, because a client that lost the response would never learn the key.
- **Scope:** a key is unique per operation type, `(type, idempotency_key)`. There is no login in this service, so keys are not scoped per customer. UUIDs make a clash very unlikely.
- **How it works:** the first database step of every operation is `INSERT ... ON CONFLICT DO NOTHING` on `transactions`, in the same transaction as the money. The unique index picks one winner, so ten identical requests at the same moment cannot all pass. A retry gets the saved answer.
- The same key with a different body returns `422 IDEMPOTENCY_KEY_REUSED`.
- A **failed** request rolls back, key included. A retry is evaluated again (for example, after a top-up it can succeed).

### Notifications (outbox)
A "transfer completed" row is saved in `outbox_events` in the same transaction as the transfer. So a customer is never told about a transfer that failed, and never misses one that worked. A background sender would read the rows where `published_at IS NULL`, send them, and mark them sent. The sender is not built yet.

### Bill payments and the unreliable biller
The biller can time out without telling us whether it paid. So the service never guesses:

```
1. HOLD     wallet -amount, BILLER_CLEARING +amount, payment saved as PENDING   (transaction 1)
2. ASK      call the biller with a fixed reference (the payment id)
3. RECORD   approved -> SUCCEEDED
            declined -> refund once, FAILED
            timeout  -> do nothing, stays PENDING                               (transaction 2)
```

- A **timeout never causes a refund**, because the bill may have been paid.
- A scheduled **reconciler** asks again for every `PENDING` payment, using the same reference. The biller never pays one reference twice, so asking again is safe. This also covers a restart in the middle of a payment.
- Only one caller at a time can settle a payment (`FOR UPDATE SKIP LOCKED` on the payment row), so several service instances cannot get different answers for the same payment.
- Refund only happens while the payment is `PENDING`, and its idempotency key is the payment id, so a wallet cannot be refunded twice.
- The biller call happens between two transactions, so no wallet row stays locked while waiting for the network.

| What happened at the biller | Service result |
|---|---|
| Approved | `SUCCEEDED`, wallet charged |
| Declined | `FAILED`, wallet refunded |
| Paid, but the reply timed out | `PENDING`, then `SUCCEEDED` on the next check, paid once |
| Not paid, and the reply timed out | `PENDING`, then paid or declined on the next check |
| Service crashes at any step | The `PENDING` row survives and the reconciler finishes it |

The service does not call a "status" endpoint of the biller. A "not paid" answer can be old if a request is still on its way, so a refund based on it is risky. Asking again with the same reference gives a final answer. `HttpBillerClient` assumes this contract: `POST /payments` with `{reference, amount}` returns `200` (approved), `422` (declined), and anything else counts as unknown.

---

## Project structure

```
src/main/java/com/tanveer/wallet_service/
├── api/         controllers, request/response records, global exception handler
├── service/     business logic (transfer, deposit, bill payments, idempotency, reconciler)
├── domain/      entities, enums, fee policy
├── repository/  Spring Data repositories (including the locking queries)
├── biller/      BillerClient interface and its HTTP implementation
└── exception/   BusinessException and ErrorCode
src/main/resources/db/migration/   Flyway: V1 schema, V2 system wallets
```

The service layer depends on the `BillerClient` interface only, so the HTTP code can change without touching payment logic, and tests can plug in a fake biller.

---

## Testing

All integration tests run against a real PostgreSQL started by Testcontainers (one shared container).

- **Unit tests:** the fee rule (rounding, minimum and maximum edges), wallet debit and credit rules, bill payment state rules.
- **Concurrency:** a wallet holds 300,000 BDT and 50 parallel transfers of 10,000 are sent. Exactly 29 succeed and 2,750 is left. Also, opposite transfers (A→B and B→A) at the same time do not deadlock.
- **Retries:** the same transfer is sent 10 times in parallel over HTTP. The database is checked: one transaction, one set of ledger rows, one outbox row, and the right balances.
- **Atomicity:** a failure after the money moved rolls everything back.
- **Bill payments:** each biller outcome one by one, plus 200 payments against a fake biller (fixed random seed, 60% approved, 15% declined, 15% timeout after paying, 10% timeout before paying). Every payment ends `SUCCEEDED` or `FAILED`, the money taken from wallets equals the money the biller received, and nothing is paid or refunded twice.
- **API and database rules:** validation, error responses, system wallets, frozen wallets, and the database triggers and constraints.

After every integration test, the suite checks that all balances add up to zero and match the ledger.

---

## Limitations and roadmap

- **Hot rows.** Every transfer updates the single fee wallet, and every deposit updates the single cash-in wallet, so these requests wait for each other. Next step: keep writing ledger rows in the transaction, but update these two balances in batches in the background.
- **Bill payment retries have no end.** A `PENDING` payment is retried forever, with no back-off. Next step: a deadline (for example 24 hours), then ask the biller for its real status once, and either mark it paid or refund.
- **The biller call holds a database connection** (up to about 7 seconds) while it waits.
- **The outbox sender is missing.** Messages are saved but not delivered. It should deliver at least once, so receivers should ignore duplicate event ids.
- **No authentication**, one currency (BDT), and no history or search endpoints.
- **Not covered by tests yet:** the HTTP biller client, and two instances settling the same payment at the same time.
