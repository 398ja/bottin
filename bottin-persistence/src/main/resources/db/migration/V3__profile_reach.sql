-- Profile reach statistics: stored follower counts per tracked profile,
-- plus a summary record of each scheduled calculation run.

CREATE TABLE profile_reach (
    id BIGSERIAL PRIMARY KEY,
    pubkey VARCHAR(64) NOT NULL UNIQUE,
    reach_count BIGINT NOT NULL DEFAULT 0,
    complete BOOLEAN NOT NULL DEFAULT TRUE,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_profile_reach_pubkey ON profile_reach(pubkey);

CREATE TABLE reach_calculation_runs (
    id BIGSERIAL PRIMARY KEY,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    profiles_total INTEGER NOT NULL DEFAULT 0,
    profiles_processed INTEGER NOT NULL DEFAULT 0,
    profiles_skipped INTEGER NOT NULL DEFAULT 0,
    gather_failures INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reach_runs_started_at ON reach_calculation_runs(started_at);
