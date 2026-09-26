-- V2__create_surveillance_alerts_table.sql
-- FinSight Phase 3: Persistent Surveillance Alert Storage
-- Flyway migration is the SINGLE source of truth for schema changes.
-- Hibernate ddl-auto is strictly 'none'.

CREATE TABLE IF NOT EXISTS surveillance_alerts (
    id          BIGSERIAL PRIMARY KEY,
    alert_id    VARCHAR(36)  NOT NULL UNIQUE,           -- UUID for distributed deduplication
    trade_id    BIGINT       NOT NULL,                  -- References ingested trade (soft reference, no FK for decoupling)
    trader_id   VARCHAR(50)  NOT NULL,
    symbol      VARCHAR(20)  NOT NULL,
    rule_name   VARCHAR(60)  NOT NULL,                  -- e.g. LARGE_VOLUME_SPIKE, RAPID_ORDER_BURST
    severity    VARCHAR(20)  NOT NULL,                  -- HIGH, CRITICAL
    description TEXT         NOT NULL,
    price       NUMERIC(18, 4),
    quantity    BIGINT,
    event_time  TIMESTAMPTZ  NOT NULL,                  -- When the triggering trade event occurred
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged        BOOLEAN      NOT NULL DEFAULT FALSE,
    acknowledged_at     TIMESTAMPTZ,
    -- NOTE: severity values are stable; rule_name is NOT constrained at DB level
    -- so new Phase 4+ surveillance rules can be added without a schema migration.
    CONSTRAINT chk_alert_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- Query performance indexes aligned with REST API filter endpoints
CREATE INDEX IF NOT EXISTS idx_alerts_trader_id  ON surveillance_alerts (trader_id);
CREATE INDEX IF NOT EXISTS idx_alerts_symbol     ON surveillance_alerts (symbol);
CREATE INDEX IF NOT EXISTS idx_alerts_rule_name  ON surveillance_alerts (rule_name);
CREATE INDEX IF NOT EXISTS idx_alerts_severity   ON surveillance_alerts (severity);
CREATE INDEX IF NOT EXISTS idx_alerts_created_at ON surveillance_alerts (created_at DESC);
-- Composite: trader timeline queries for behavioural profiling
CREATE INDEX IF NOT EXISTS idx_alerts_trader_created ON surveillance_alerts (trader_id, created_at DESC);
