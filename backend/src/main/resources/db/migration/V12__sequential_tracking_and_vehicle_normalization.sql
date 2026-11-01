-- Migration V12: Sequential Package Tracking Sequence and Vehicle Registration Normalization

-- 1. Create concurrency-safe sequence for sequential package tracking numbers (PKG-BLR-000001, etc.)
CREATE SEQUENCE IF NOT EXISTS package_tracking_number_seq START WITH 1 INCREMENT BY 1;

-- 2. Safely normalize any existing vehicle registration numbers to standard KA-01-AB-1234 format
UPDATE vehicles
SET registration_number = regexp_replace(
    upper(replace(replace(registration_number, ' ', ''), '-', '')),
    '^([A-Z]{2})([0-9]{2})([A-Z]{1,2})([0-9]{4})$',
    '\1-\2-\3-\4'
)
WHERE upper(replace(replace(registration_number, ' ', ''), '-', '')) ~ '^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}$';
