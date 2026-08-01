-- Admin-maintained deployment settings: the media server, the system relays,
-- the profile discovery relays, and the public rate limit. One row, always.
--
-- The row is inserted here so that "no settings row" is never a state the
-- application handles: unconfigured is NULL or '[]', which is a value.

CREATE TABLE settings (
    id                    BIGINT PRIMARY KEY,
    blossom_url           VARCHAR(512),
    default_relays_json   TEXT      NOT NULL DEFAULT '[]',
    discovery_relays_json TEXT      NOT NULL DEFAULT '[]',
    rate_limit_per_minute INT       NOT NULL DEFAULT 30,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT settings_singleton CHECK (id = 1)
);

INSERT INTO settings (id, updated_at) VALUES (1, CURRENT_TIMESTAMP);
