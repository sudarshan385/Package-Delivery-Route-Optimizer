-- ===================================================================
-- Flyway Database Migration: V4__add_customer_id_to_packages.sql
-- ===================================================================

ALTER TABLE packages ADD COLUMN customer_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_packages_customer ON packages(customer_id);
