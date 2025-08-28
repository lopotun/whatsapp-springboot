-- Truncate all tables except users table
-- This will clear all data but preserve the table structure

-- Truncate tables in the correct order to avoid foreign key constraint issues
-- CASCADE ensures all dependent data is also truncated

TRUNCATE TABLE chat_entries CASCADE;

TRUNCATE TABLE attachments CASCADE;

-- Reset sequences (PostgreSQL equivalent of auto-increment)
ALTER SEQUENCE IF EXISTS locations_id_seq RESTART WITH 1;

ALTER SEQUENCE IF EXISTS chat_entries_id_seq RESTART WITH 1;

ALTER SEQUENCE IF EXISTS attachments_id_seq RESTART WITH 1;
