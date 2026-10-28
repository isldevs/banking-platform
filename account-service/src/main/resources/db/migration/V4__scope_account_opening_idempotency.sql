-- Move account opening deduplication from a global client key to (principal, client key).
-- Existing requests were not associated with an initiating principal. For migration,
-- infer the owning principal from the account; historical admin-initiated requests
-- may need a fresh idempotency key if retried by that admin.
ALTER TABLE account_opening_requests ADD COLUMN id UUID;
ALTER TABLE account_opening_requests ADD COLUMN principal_id UUID;

UPDATE account_opening_requests AS opening
SET id = gen_random_uuid(),
    principal_id = account.owner_user_id
FROM accounts AS account
WHERE account.id = opening.account_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM account_opening_requests
        WHERE id IS NULL OR principal_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot backfill idempotency principal: account owner missing';
    END IF;
END
$$;

ALTER TABLE account_opening_requests ALTER COLUMN id SET NOT NULL;
ALTER TABLE account_opening_requests ALTER COLUMN principal_id SET NOT NULL;

ALTER TABLE account_opening_requests DROP CONSTRAINT account_opening_requests_pkey;
ALTER TABLE account_opening_requests ADD CONSTRAINT account_opening_requests_pkey
    PRIMARY KEY (id);

ALTER TABLE account_opening_requests ADD CONSTRAINT uq_account_opening_principal_key
    UNIQUE (principal_id, idempotency_key);
