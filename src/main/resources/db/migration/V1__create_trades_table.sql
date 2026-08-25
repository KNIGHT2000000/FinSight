-- V1__create_trades_table.sql
-- FinSight Phase 2: Schema definition for Trade Persistence
-- Flyway migration is the SINGLE source of truth for the database schema.
-- Hibernate ddl-auto is strictly set to 'none'.

CREATE TABLE IF NOT EXISTS trades (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity BIGINT NOT NULL,
    price NUMERIC(18, 4) NOT NULL,
    trader_id VARCHAR(50) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_trade_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_trade_status CHECK (status IN ('PENDING', 'EXECUTED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_trade_quantity CHECK (quantity > 0),
    CONSTRAINT chk_trade_price CHECK (price > 0.0)
);

-- Surveillance Query Performance Indexes
-- Index on symbol for instrument-specific trade flow analysis
CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades (symbol);

-- Index on trader_id for entity aggregation and individual behavioral profiling
CREATE INDEX IF NOT EXISTS idx_trades_trader_id ON trades (trader_id);

-- Composite index on (trader_id, created_at) for time-series window queries (e.g. spoofing/layering detection)
CREATE INDEX IF NOT EXISTS idx_trades_trader_id_created_at ON trades (trader_id, created_at);
