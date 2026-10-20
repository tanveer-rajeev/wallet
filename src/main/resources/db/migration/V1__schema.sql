CREATE TABLE wallets (
    id          uuid         PRIMARY KEY,
    owner_id    varchar(64)  NOT NULL,
    kind        varchar(20)  NOT NULL,
    currency    char(3)      NOT NULL DEFAULT 'IQD',
    balance     bigint       NOT NULL DEFAULT 0,
    status      varchar(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_wallet_kind     CHECK (kind IN ('CUSTOMER', 'FEE', 'CASH_IN', 'BILLER_CLEARING')),
    CONSTRAINT chk_wallet_status   CHECK (status IN ('ACTIVE', 'FROZEN')),
    CONSTRAINT chk_wallet_currency CHECK (currency = 'IQD'),
    CONSTRAINT chk_customer_balance_non_negative CHECK (kind <> 'CUSTOMER' OR balance >= 0)
);

CREATE INDEX idx_wallets_owner ON wallets (owner_id);
CREATE UNIQUE INDEX uq_one_system_wallet_per_kind ON wallets (kind) WHERE kind <> 'CUSTOMER';

CREATE TABLE transactions (
    id                       uuid         PRIMARY KEY,
    type                     varchar(20)  NOT NULL,
    idempotency_key          varchar(100) NOT NULL,
    request_hash             varchar(64)  NOT NULL,
    reverses_transaction_id  uuid         NULL REFERENCES transactions (id),
    created_at               timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_tx_type CHECK (type IN ('DEPOSIT', 'TRANSFER', 'BILL_HOLD', 'BILL_REFUND')),
    CONSTRAINT uq_tx_type_idempotency_key UNIQUE (type, idempotency_key)
);

CREATE TABLE ledger_entries (
    id              bigserial    PRIMARY KEY,
    transaction_id  uuid         NOT NULL REFERENCES transactions (id),
    wallet_id       uuid         NOT NULL REFERENCES wallets (id),
    direction       varchar(6)   NOT NULL,
    amount          bigint       NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_entry_amount    CHECK (amount > 0)
);

CREATE INDEX idx_ledger_transaction ON ledger_entries (transaction_id);
CREATE INDEX idx_ledger_wallet      ON ledger_entries (wallet_id);

CREATE TABLE bill_payments (
    id                      uuid          PRIMARY KEY,
    wallet_id               uuid          NOT NULL REFERENCES wallets (id),
    biller_code             varchar(50)   NOT NULL,
    amount                  bigint        NOT NULL,
    biller_reference        varchar(100)  NOT NULL,
    status                  varchar(10)   NOT NULL,
    attempts                int           NOT NULL DEFAULT 0,
    next_attempt_at         timestamptz   NOT NULL,
    hold_transaction_id     uuid          NOT NULL REFERENCES transactions (id),
    refund_transaction_id   uuid          NULL REFERENCES transactions (id),
    created_at              timestamptz   NOT NULL DEFAULT now(),
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_bill_biller_reference UNIQUE (biller_reference),
    CONSTRAINT chk_bill_amount CHECK (amount > 0),
    CONSTRAINT chk_bill_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_bill_pending_due ON bill_payments (next_attempt_at) WHERE status = 'PENDING';

CREATE TABLE outbox_events (
    id            uuid         PRIMARY KEY,
    aggregate_id  uuid         NOT NULL,
    event_type    varchar(50)  NOT NULL,
    payload       jsonb        NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    published_at  timestamptz  NULL
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
