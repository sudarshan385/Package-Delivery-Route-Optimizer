-- ===================================================================
-- Flyway Database Migration: V11__add_nearest_hub_and_return_lifecycle.sql
-- Project: Package Delivery Route Optimizer
-- Purpose:
--   1. Add nearest_hub_id and nearest_hub_distance to packages table.
--   2. Add route lifecycle and return-to-hub tracking columns to routes table.
--   3. Add return_confirmed_at to deliveries table.
-- Invariants:
--   - Non-destructive and additive only (uses IF NOT EXISTS).
--   - Preserves all historical V1-V10 migrations and existing data.
-- ===================================================================

-- 1. Add nearest hub recommendation columns to packages table
ALTER TABLE packages
    ADD COLUMN IF NOT EXISTS nearest_hub_id BIGINT REFERENCES locations(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS nearest_hub_distance DOUBLE PRECISION;

CREATE INDEX IF NOT EXISTS idx_packages_nearest_hub ON packages(nearest_hub_id);

-- 2. Add route lifecycle and return-to-hub tracking columns to routes table
ALTER TABLE routes
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS returned_to_hub_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS return_confirmed_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS return_distance DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS return_estimated_time DOUBLE PRECISION;

CREATE INDEX IF NOT EXISTS idx_routes_status ON routes(status);

-- 3. Add return confirmation timestamp to deliveries table
ALTER TABLE deliveries
    ADD COLUMN IF NOT EXISTS return_confirmed_at TIMESTAMP WITH TIME ZONE;

