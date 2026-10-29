-- Migration V8: Add user verification and blocking
-- Independent flags for account verification and security blocking

ALTER TABLE users 
    ADD COLUMN is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_blocked BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing administrators retain operational access by defaulting to verified
UPDATE users 
SET is_verified = TRUE 
WHERE role = 'ADMIN';

-- Performance indexes for administrative search, filtering, and authentication state checks
CREATE INDEX idx_users_is_verified ON users (is_verified);
CREATE INDEX idx_users_is_blocked ON users (is_blocked);
CREATE INDEX idx_users_role_status ON users (role, active, is_blocked, is_verified);
