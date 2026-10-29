-- Add city, pickup_location, and delivery_location columns to packages table
ALTER TABLE packages ADD COLUMN IF NOT EXISTS city VARCHAR(100);
ALTER TABLE packages ADD COLUMN IF NOT EXISTS pickup_location VARCHAR(255);
ALTER TABLE packages ADD COLUMN IF NOT EXISTS delivery_location VARCHAR(255);

-- Allow source_location_id and destination_location_id to be nullable for user-entered addresses
ALTER TABLE packages ALTER COLUMN source_location_id DROP NOT NULL;
ALTER TABLE packages ALTER COLUMN destination_location_id DROP NOT NULL;

-- Indexes for search/filtering
CREATE INDEX IF NOT EXISTS idx_packages_city ON packages(city);
