-- ===================================================================
-- Flyway Database Migration: V3__add_ddqn_ppdrp_research_schema.sql
-- Project: Package Delivery Route Optimizer
-- Description: Research-data foundation for DDQN-PPDRP pipeline
-- Tables:
--   1. research_historical_routes
--   2. research_historical_tasks
-- ===================================================================

-- 1. RESEARCH_HISTORICAL_ROUTES
CREATE TABLE research_historical_routes (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT REFERENCES drivers(id) ON DELETE SET NULL,
    total_distance DOUBLE PRECISION,
    total_time DOUBLE PRECISION,
    route_date TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_research_routes_driver ON research_historical_routes(driver_id);
CREATE INDEX idx_research_routes_date ON research_historical_routes(route_date);

-- 2. RESEARCH_HISTORICAL_TASKS
CREATE TABLE research_historical_tasks (
    id BIGSERIAL PRIMARY KEY,
    historical_route_id BIGINT NOT NULL REFERENCES research_historical_routes(id) ON DELETE CASCADE,
    delivery_id BIGINT REFERENCES deliveries(id) ON DELETE SET NULL,
    location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    task_type VARCHAR(20) NOT NULL,
    sequence_order INTEGER NOT NULL,
    time_window_start TIMESTAMP WITH TIME ZONE,
    time_window_end TIMESTAMP WITH TIME ZONE,
    actual_arrival_time TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_historical_route_task_order UNIQUE (historical_route_id, sequence_order)
);

CREATE INDEX idx_research_tasks_route ON research_historical_tasks(historical_route_id);
CREATE INDEX idx_research_tasks_delivery ON research_historical_tasks(delivery_id);
CREATE INDEX idx_research_tasks_location ON research_historical_tasks(location_id);
