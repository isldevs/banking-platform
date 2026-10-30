-- Existing pending transfers may already have been sent before this upgrade.
-- Treat their outcome conservatively until account-service confirms it.
ALTER TABLE transfers ADD COLUMN dispatch_attempted BOOLEAN NOT NULL DEFAULT TRUE;
