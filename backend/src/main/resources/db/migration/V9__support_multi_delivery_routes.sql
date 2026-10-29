-- ===================================================================
-- Flyway Database Migration: V9__support_multi_delivery_routes.sql
-- Project: Package Delivery Route Optimizer
-- Purpose: Support multi-delivery routes and numbered customer stops
-- Invariants:
--   1. Retains routes.delivery_id for backward compatibility and historical audit.
--   2. Adds deliveries.route_id allowing multiple deliveries per route.
--   3. Adds route_stops.delivery_id and customer_stop_number to distinguish
--      actual customer delivery stops from intermediate road waypoints.
--   4. Enforces customer_stop_number > 0 and per-route uniqueness for non-null values.
--   5. Safely and deterministically backfills existing current single-delivery routes.
-- ===================================================================

-- 1. Add route_id foreign key to deliveries table
ALTER TABLE deliveries
    ADD COLUMN IF NOT EXISTS route_id BIGINT REFERENCES routes(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_deliveries_route ON deliveries(route_id);

-- 2. Deterministic backfill: Link deliveries to their active current route
UPDATE deliveries d
SET route_id = sub.id
FROM (
    SELECT DISTINCT ON (delivery_id) id, delivery_id
    FROM routes
    WHERE delivery_id IS NOT NULL
      AND is_current = TRUE
      AND active = TRUE
    ORDER BY delivery_id, version_number DESC, id DESC
) sub
WHERE d.id = sub.delivery_id
  AND d.route_id IS NULL;

-- 3. Add delivery_id and customer_stop_number to route_stops table
ALTER TABLE route_stops
    ADD COLUMN IF NOT EXISTS delivery_id BIGINT REFERENCES deliveries(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS customer_stop_number INTEGER;

CREATE INDEX IF NOT EXISTS idx_route_stops_delivery ON route_stops(delivery_id);

-- 4. Enforce positive customer stop numbers (when provided)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_route_stops_customer_stop_positive'
    ) THEN
        ALTER TABLE route_stops
            ADD CONSTRAINT chk_route_stops_customer_stop_positive
            CHECK (customer_stop_number IS NULL OR customer_stop_number > 0);
    END IF;
END $$;

-- 5. Enforce uniqueness of customer_stop_number within a route (excluding waypoints where NULL)
CREATE UNIQUE INDEX IF NOT EXISTS uq_route_stops_route_customer_stop
    ON route_stops (route_id, customer_stop_number)
    WHERE customer_stop_number IS NOT NULL;

-- 6. Deterministic backfill: Associate existing single-delivery DESTINATION stops
UPDATE route_stops rs
SET delivery_id = sub.delivery_id,
    customer_stop_number = 1
FROM (
    SELECT DISTINCT ON (rs2.route_id) rs2.id, r.delivery_id
    FROM route_stops rs2
    JOIN routes r ON rs2.route_id = r.id
    WHERE r.delivery_id IS NOT NULL
      AND rs2.stop_type = 'DESTINATION'
    ORDER BY rs2.route_id, rs2.stop_order DESC
) sub
WHERE rs.id = sub.id
  AND rs.delivery_id IS NULL
  AND rs.customer_stop_number IS NULL;
