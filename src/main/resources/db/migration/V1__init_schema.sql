CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ANALYST', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    account_id VARCHAR(64) PRIMARY KEY,
    bank_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    from_account_id VARCHAR(64) NOT NULL REFERENCES accounts(account_id),
    to_account_id VARCHAR(64) NOT NULL REFERENCES accounts(account_id),
    amount NUMERIC(18,2) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    payment_format VARCHAR(32),
    occurred_at TIMESTAMPTZ NOT NULL,
    is_laundering BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_transactions_from ON transactions(from_account_id);
CREATE INDEX idx_transactions_to ON transactions(to_account_id);

CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    pattern_type VARCHAR(32) NOT NULL,
    subgraph_json JSONB NOT NULL,
    narrative TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','REVIEWING','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
