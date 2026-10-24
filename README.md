# Wallet Service

A small REST service for IQD wallets: create wallets, deposit, transfer, and pay bills.
Stack: Java 21, Spring Boot 4, Maven, PostgreSQL 18, Flyway, Testcontainers.

## How to run

```
docker compose up -d && mvn spring-boot:run     # run the service
mvn test                                        # run all tests (Docker must be running)
```

Tests start their own real PostgreSQL with Testcontainers. No in-memory database is used.

## Decisions where the brief was silent

- **Retries:** deposits, transfers and bill payments need an `Idempotency-Key` header. A retry with the same key returns the first answer and moves no money. The same key with a different body returns 422. Failed requests are not saved, so a retry is checked again.
- **System accounts:** the fee and cash-in accounts are normal wallets with a `kind` column. Cash-in can go negative on purpose (it is the cash we hold for customers). Only customer wallets cannot. All balances always add up to 0.
- **Rules I added:** deposit limits are the same as transfers (1 to 5,000,000). Sending to yourself is rejected. A frozen wallet can still receive money. Customers cannot use system wallets, but anyone can read them with GET.
- **Request bodies:** transfer uses `fromWalletId`, `toWalletId`, `amount`. Bill payment uses `walletId`, `amount`. There is one biller, so no biller code.
- **Wallets:** creating a wallet is not idempotent (a retry just makes another empty wallet). One owner can have many wallets.
- **Status codes:** 400 for a bad request, 404 for an unknown wallet, 422 for a broken business rule (not enough money, frozen wallet, and so on).
- **Bill payment result:** the POST returns 201 with a `status`: `SUCCEEDED`, `FAILED` or `PENDING`. `PENDING` means the biller has not answered yet and it will be settled automatically. There is no lookup endpoint, so a client can send the same POST again to see the current status.
- **Biller API:** the brief only asks for a fake biller, so the real HTTP client (`HttpBillerClient`) is my own guess of the API. It exists so the app can start.

## Key design choices

- **Ledger:** every operation writes rows in `transactions` and `ledger_entries`. Debits always equal credits. A database trigger blocks update and delete on both tables, so history cannot change. A fix is a new operation (for example `BILL_REFUND`), linked from `bill_payments`.
- **Balance:** the wallet balance is kept in the wallet row, and a `CHECK` rule stops customer balances going below 0. Tests check that balances add up to 0 and match the ledger after every integration test.
- **Concurrency (locking).** Each operation runs in one database transaction and locks the wallets it changes with `SELECT ... FOR UPDATE`.
    - **Why lock rows instead of retrying:** many requests can hit the same wallet at once (the 50-transfer test). With row locks they simply wait in line and each one sees the latest balance, so the balance cannot go below 0. The other option (optimistic locking) would make most of the 50 requests fail and retry.
    - **Why in id order:** if A sends to B while B sends to A, and each locks its own wallet first, each waits for the other forever (a deadlock). If every transfer locks the lower id first, one of them always gets both wallets and the other waits.
    - **System wallets:** the fee, cash-in and clearing wallets are always updated after the customer wallets, so the order stays the same everywhere.
    - **Trade-off:** all transfers update the one fee wallet row, so they wait for each other there (see Known gaps).
    - **Retries:** the key is saved first with `INSERT ... ON CONFLICT DO NOTHING`, in the same transaction as the money. Parallel retries wait for the first one and then get its answer.
- **Notification:** a "transfer completed" row is saved in an outbox table in the same transaction as the transfer. A customer is never told about a transfer that failed, and never misses one that worked. A sender job would read the unsent rows. I did not build the sender.
- **Bill payments:** the money is first moved to a system wallet (`BILLER_CLEARING`) and the payment is saved as `PENDING`. Then the biller is called.
    - Approved: `SUCCEEDED`. Declined: refund once, then `FAILED`.
    - A timeout changes nothing. We never refund on a timeout, because the bill may be paid.
    - A scheduled job asks again every 30 seconds with the same reference (the payment id). The biller never pays one reference twice, so asking again is safe. This also covers a restart.
    - Only one caller at a time can settle a payment (`FOR UPDATE SKIP LOCKED`). This keeps several instances safe.
    - I do not use `status()` in the service. A "not paid" answer could be old if a request is still on its way, so a refund based on it is risky. The test uses `status()` to check the truth.
- **Tests (only the ones in the brief):** fee unit test, 50 parallel transfers (exactly 29 succeed, 2,750 left), 10 parallel retries sent over HTTP with database checks, and 200 bill payments with a fake biller (fixed seed 42, 60/15/15/10).he fake biller uses a fixed seed (42), so every run gives the same outcomes. The test checks totals and consistency, so it should hold for any seed
- **Thread pool in tests:** I used a fixed thread pool, so all clients start at the same moment. Virtual threads would give the same result here, because the database lock decides the outcome. I would use them for thousands of clients.

## Known gaps

- Fee and cash-in are single rows, so all transfers (or deposits) wait for each other on one row.
  If fee-balance updates became a bottleneck, one option would be to maintain an append-only ledger and update the aggregated fee balance asynchronously using a transactional outbox. This would reduce contention, but the aggregate balance could temporarily lag behind completed transfers.
  If synchronous fee accounting were required at higher volumes, I could consider sharding the fee accounts to distribute writes. However, that would increase the complexity of reconciliation and balance management.
- A bill payment is retried forever. There is no back-off and no deadline. After a deadline I would check `status()` on the biller and refund if it was not paid.
- The biller call holds one database connection while it waits (up to about 7 seconds).
- No dedicated tests for deposit retries, input validation, error responses, the HTTP biller client, or two instances settling the same payment.
- No lock timeout on the database, and the outbox sender is missing.

## With one more day

- **Add the missing tests.** Deposit retries, input validation and error responses, the HTTP biller client, and two instances settling the same bill payment at the same time. These paths work through the required tests today, but they have no tests of their own.
- **Update the shared accounts (fee, cash-in) asynchronously.** Today every transfer updates the one fee row, and every deposit updates the one cash-in row, so these requests wait for each other. I would keep writing the ledger rows inside the transaction, so the history stays exact, and update those two balances in small batches in the background. Trade-off: their balances can be a few seconds behind, and I would adjust the "balances add up to 0" check to allow for that.
- **Build the outbox sender.** A background job that reads the unsent "transfer completed" rows, sends the notification, and marks each row as sent. It can send a message twice after a crash, so the receiver would use the event id to ignore duplicates.
- **Add a payment deadline.** Today a pending bill payment is retried forever. After a time limit (for example 24 hours), I would stop retrying and ask the biller for the real status once. If the bill was paid, mark it succeeded. If not, refund the wallet and mark it failed. This needs a `status()` call on the real biller client.

## Time spent

Approximately 16 hours.