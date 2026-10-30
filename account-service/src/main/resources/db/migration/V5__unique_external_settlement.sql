-- Existing duplicate credits require reconciliation, never automatic history deletion.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM funding_instructions
        WHERE external_reference IS NOT NULL
        GROUP BY channel, currency, BTRIM(external_reference)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate external settlement references exist; reconcile affected funding instructions and financial postings before applying V5';
    END IF;
END $$;

UPDATE funding_instructions
SET external_reference = BTRIM(external_reference)
WHERE external_reference IS NOT NULL;

CREATE UNIQUE INDEX uq_funding_instructions_external_settlement
    ON funding_instructions (channel, currency, external_reference)
    WHERE external_reference IS NOT NULL;
