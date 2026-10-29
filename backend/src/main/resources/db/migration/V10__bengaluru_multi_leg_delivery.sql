-- ===================================================================
-- Flyway Database Migration: V10__bengaluru_multi_leg_delivery.sql
-- Project: Package Delivery Route Optimizer
-- Purpose: Support Bengaluru Smart Parcel Network:
--          1. Detailed package content metadata (dimensions, fragile, declared value).
--          2. Multi-leg delivery architecture (Leg A: First-Mile, Leg B: Final-Mile).
--          3. Authoritative six-stage shipment event timeline.
-- Invariants:
--   - Preserves all historical V1-V9 migrations and existing records.
--   - Non-destructive: uses IF NOT EXISTS for columns, tables, and constraints.
-- ===================================================================

-- 1. Enhance packages table with item details, dimensions, and instructions
ALTER TABLE packages
    ADD COLUMN IF NOT EXISTS item_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS item_category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS quantity INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS fragile BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS declared_value DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS length_cm DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS width_cm DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS height_cm DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pickup_instructions VARCHAR(500),
    ADD COLUMN IF NOT EXISTS delivery_instructions VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sender_email VARCHAR(100),
    ADD COLUMN IF NOT EXISTS receiver_email VARCHAR(100);

-- 2. Enhance deliveries table with nearest hub reference and current stage
ALTER TABLE deliveries
    ADD COLUMN IF NOT EXISTS hub_id BIGINT REFERENCES locations(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS hub_selection_basis VARCHAR(255),
    ADD COLUMN IF NOT EXISTS current_stage VARCHAR(50) DEFAULT 'REQUEST_CREATED';

CREATE INDEX IF NOT EXISTS idx_deliveries_hub ON deliveries(hub_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_current_stage ON deliveries(current_stage);

-- 3. Create delivery_legs table for independent First-Mile and Final-Mile dispatch
CREATE TABLE IF NOT EXISTS delivery_legs (
    id BIGSERIAL PRIMARY KEY,
    delivery_id BIGINT NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    leg_type VARCHAR(20) NOT NULL, -- 'FIRST_MILE', 'FINAL_MILE'
    leg_order INTEGER NOT NULL DEFAULT 1,
    source_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    destination_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    driver_id BIGINT REFERENCES drivers(id) ON DELETE RESTRICT,
    vehicle_id BIGINT REFERENCES vehicles(id) ON DELETE RESTRICT,
    route_id BIGINT REFERENCES routes(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, ASSIGNED, IN_TRANSIT, COMPLETED, FAILED, CANCELLED
    assigned_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    distance DOUBLE PRECISION,
    estimated_time DOUBLE PRECISION,
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_delivery_leg_type CHECK (leg_type IN ('FIRST_MILE', 'FINAL_MILE')),
    CONSTRAINT uq_delivery_leg_order UNIQUE (delivery_id, leg_order)
);

CREATE INDEX IF NOT EXISTS idx_delivery_legs_delivery ON delivery_legs(delivery_id);
CREATE INDEX IF NOT EXISTS idx_delivery_legs_driver ON delivery_legs(driver_id);
CREATE INDEX IF NOT EXISTS idx_delivery_legs_vehicle ON delivery_legs(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_delivery_legs_status ON delivery_legs(status);
CREATE INDEX IF NOT EXISTS idx_delivery_legs_active ON delivery_legs(active);

-- 4. Create shipment_events table for authoritative six-stage timeline
CREATE TABLE IF NOT EXISTS shipment_events (
    id BIGSERIAL PRIMARY KEY,
    delivery_id BIGINT NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    package_id BIGINT NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    stage VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    location_name VARCHAR(150),
    hub_id BIGINT REFERENCES locations(id) ON DELETE SET NULL,
    driver_id BIGINT REFERENCES drivers(id) ON DELETE SET NULL,
    driver_name VARCHAR(100),
    actor_username VARCHAR(100),
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shipment_events_delivery ON shipment_events(delivery_id);
CREATE INDEX IF NOT EXISTS idx_shipment_events_package ON shipment_events(package_id);
CREATE INDEX IF NOT EXISTS idx_shipment_events_stage ON shipment_events(stage);
CREATE INDEX IF NOT EXISTS idx_shipment_events_timestamp ON shipment_events(event_timestamp);

-- 5. Deterministic backfill for historical deliveries:
-- Create initial REQUEST_CREATED events for existing deliveries that do not have any events yet
INSERT INTO shipment_events (delivery_id, package_id, stage, title, description, location_name, event_timestamp)
SELECT d.id, d.package_id, 'REQUEST_CREATED', 'Shipment Request Created', 
       COALESCE(d.notes, 'Initial delivery request created in system.'),
       COALESCE(l.name, 'Bengaluru Center'),
       d.created_at
FROM deliveries d
JOIN locations l ON d.source_location_id = l.id
WHERE NOT EXISTS (
    SELECT 1 FROM shipment_events se WHERE se.delivery_id = d.id
);
