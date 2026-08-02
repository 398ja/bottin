-- Reshapes admin_users for key-based administrators.
--
-- The table has existed since V1 and has never held a row: nothing outside its
-- own entity and repository referenced it, and no migration or fixture seeded
-- it. Dropping columns is therefore destructive in form and inert in effect —
-- there is no deployment with data to lose.
--
-- password_hash was NOT NULL, which made it impossible to insert an
-- administrator without inventing a password, for a feature whose whole point
-- is that there are none. username meant nothing once sign-in is by key.
--
-- Order matters: the unique index goes before the column it covers.

DROP INDEX IF EXISTS idx_admin_users_username;

ALTER TABLE admin_users DROP COLUMN username;
ALTER TABLE admin_users DROP COLUMN password_hash;

-- Human-readable, so the list can be maintained without comparing 64-character
-- keys by eye. Descriptive only: never consulted when deciding who may sign in.
ALTER TABLE admin_users ADD COLUMN label VARCHAR(100);

-- Which administrator added this one, so a change of access is attributable.
ALTER TABLE admin_users ADD COLUMN added_by_pubkey VARCHAR(64);

-- The pubkey is now the identity rather than an optional attribute. Canonical
-- lowercase hex (NIP-01); npub input is decoded before it reaches here.
ALTER TABLE admin_users ALTER COLUMN pubkey SET NOT NULL;

CREATE UNIQUE INDEX idx_admin_users_pubkey ON admin_users (pubkey);
