-- ===================================================================
-- Flyway Database Migration: V7__remove_dispatcher_role.sql
-- Project: Package Delivery Route Optimizer
-- Description: Deprecate and remove DISPATCHER role. Migrate any
--              existing DISPATCHER accounts to ADMIN preserving all
--              user IDs and attributes. Enforce supported roles
--              (ADMIN, DRIVER, USER) via database check constraint.
-- ===================================================================

-- 1. Migrate any existing DISPATCHER accounts to ADMIN
UPDATE users
SET role = 'ADMIN',
    updated_at = CURRENT_TIMESTAMP
WHERE role = 'DISPATCHER';

-- 2. Enforce valid roles constraint on users table
ALTER TABLE users DROP CONSTRAINT IF EXISTS check_users_role;
ALTER TABLE users ADD CONSTRAINT check_users_role CHECK (role IN ('ADMIN', 'DRIVER', 'USER'));
