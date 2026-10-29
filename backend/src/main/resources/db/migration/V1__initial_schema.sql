-- ===================================================================
-- Flyway Database Migration: V1__initial_schema.sql
-- Project: Package Delivery Route Optimizer
-- Description: Clean Initial Schema DDL (Zero Seed/Dummy Data)
-- Tables:
--   1. users
--   2. drivers
--   3. vehicles
--   4. locations
--   5. location_edges
--   6. packages
--   7. deliveries
--   8. delivery_status_history
--   9. routes
--  10. route_stops
--  11. route_edges
-- ===================================================================

-- 1. USERS
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- 2. DRIVERS
CREATE TABLE drivers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    license_number VARCHAR(50) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_drivers_user_id ON drivers(user_id);
CREATE INDEX idx_drivers_status ON drivers(status);
CREATE INDEX idx_drivers_active ON drivers(active);

-- 3. VEHICLES
CREATE TABLE vehicles (
    id BIGSERIAL PRIMARY KEY,
    registration_number VARCHAR(30) NOT NULL UNIQUE,
    vehicle_type VARCHAR(20) NOT NULL,
    capacity DOUBLE PRECISION NOT NULL CHECK (capacity > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vehicles_registration ON vehicles(registration_number);
CREATE INDEX idx_vehicles_status ON vehicles(status);
CREATE INDEX idx_vehicles_active ON vehicles(active);

-- 4. LOCATIONS
CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    address VARCHAR(255),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_type VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_locations_name ON locations(name);
CREATE INDEX idx_locations_active ON locations(active);

-- 5. LOCATION_EDGES (Directed road graph between locations)
CREATE TABLE location_edges (
    id BIGSERIAL PRIMARY KEY,
    source_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    destination_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    distance DOUBLE PRECISION NOT NULL CHECK (distance > 0),
    estimated_time DOUBLE PRECISION CHECK (estimated_time IS NULL OR estimated_time >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_no_self_loop CHECK (source_location_id != destination_location_id),
    CONSTRAINT uq_location_edge_pair UNIQUE (source_location_id, destination_location_id)
);

CREATE INDEX idx_location_edges_source ON location_edges(source_location_id);
CREATE INDEX idx_location_edges_destination ON location_edges(destination_location_id);
CREATE INDEX idx_location_edges_active ON location_edges(active);

-- 6. PACKAGES
CREATE TABLE packages (
    id BIGSERIAL PRIMARY KEY,
    tracking_number VARCHAR(50) NOT NULL UNIQUE,
    sender_name VARCHAR(100) NOT NULL,
    sender_phone VARCHAR(20),
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20),
    source_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    destination_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    weight DOUBLE PRECISION NOT NULL CHECK (weight > 0),
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_packages_tracking ON packages(tracking_number);
CREATE INDEX idx_packages_source ON packages(source_location_id);
CREATE INDEX idx_packages_destination ON packages(destination_location_id);
CREATE INDEX idx_packages_status ON packages(status);
CREATE INDEX idx_packages_active ON packages(active);

-- 7. DELIVERIES
CREATE TABLE deliveries (
    id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES packages(id) ON DELETE RESTRICT,
    source_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    destination_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    driver_id BIGINT REFERENCES drivers(id) ON DELETE RESTRICT,
    vehicle_id BIGINT REFERENCES vehicles(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    estimated_distance DOUBLE PRECISION,
    estimated_time DOUBLE PRECISION,
    notes VARCHAR(500),
    completed_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_deliveries_package ON deliveries(package_id);
CREATE INDEX idx_deliveries_driver ON deliveries(driver_id);
CREATE INDEX idx_deliveries_vehicle ON deliveries(vehicle_id);
CREATE INDEX idx_deliveries_status ON deliveries(status);
CREATE INDEX idx_deliveries_active ON deliveries(active);

-- 8. DELIVERY_STATUS_HISTORY
CREATE TABLE delivery_status_history (
    id BIGSERIAL PRIMARY KEY,
    delivery_id BIGINT NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dsh_delivery ON delivery_status_history(delivery_id);
CREATE INDEX idx_dsh_user ON delivery_status_history(changed_by_user_id);

-- 9. ROUTES
CREATE TABLE routes (
    id BIGSERIAL PRIMARY KEY,
    delivery_id BIGINT REFERENCES deliveries(id) ON DELETE SET NULL,
    source_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    destination_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    version_number INTEGER NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    total_distance DOUBLE PRECISION NOT NULL,
    estimated_time DOUBLE PRECISION,
    algorithm_used VARCHAR(50) NOT NULL DEFAULT 'DIJKSTRA',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_routes_delivery ON routes(delivery_id);
CREATE INDEX idx_routes_source ON routes(source_location_id);
CREATE INDEX idx_routes_destination ON routes(destination_location_id);
CREATE INDEX idx_routes_current ON routes(is_current);
CREATE INDEX idx_routes_active ON routes(active);

-- 10. ROUTE_STOPS (Ordered waypoints/stops for a route)
CREATE TABLE route_stops (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    stop_order INTEGER NOT NULL,
    stop_type VARCHAR(20) NOT NULL DEFAULT 'WAYPOINT',
    estimated_arrival_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_route_stop_order UNIQUE (route_id, stop_order)
);

CREATE INDEX idx_route_stops_route ON route_stops(route_id);
CREATE INDEX idx_route_stops_location ON route_stops(location_id);

-- 11. ROUTE_EDGES (Ordered sequence of graph edges traversed by a route)
CREATE TABLE route_edges (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    edge_order INTEGER NOT NULL,
    source_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    destination_location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    distance DOUBLE PRECISION NOT NULL,
    estimated_time DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_route_edge_order UNIQUE (route_id, edge_order)
);

CREATE INDEX idx_route_edges_route ON route_edges(route_id);
CREATE INDEX idx_route_edges_source ON route_edges(source_location_id);
CREATE INDEX idx_route_edges_destination ON route_edges(destination_location_id);
