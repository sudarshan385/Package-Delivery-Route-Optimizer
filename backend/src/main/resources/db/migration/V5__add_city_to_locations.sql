-- Add city to locations table
ALTER TABLE locations ADD COLUMN IF NOT EXISTS city VARCHAR(100);
