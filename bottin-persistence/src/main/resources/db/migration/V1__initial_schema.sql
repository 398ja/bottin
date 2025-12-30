-- Bottin Initial Schema
-- V1: Core tables for NIP-05 registry

-- domains: Registered domains with verification status
CREATE TABLE domains (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    verified BOOLEAN DEFAULT FALSE NOT NULL,
    verification_token VARCHAR(64),
    verification_method VARCHAR(20),
    verified_at TIMESTAMP,
    owner_pubkey VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_domains_name ON domains(name);

-- nip05_records: NIP-05 identifier mappings
CREATE TABLE nip05_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain_id BIGINT NOT NULL,
    username VARCHAR(255) NOT NULL,
    pubkey VARCHAR(64) NOT NULL,
    relays_json TEXT DEFAULT '[]',
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nip05_domain FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE,
    CONSTRAINT uk_nip05_domain_username UNIQUE (domain_id, username)
);

CREATE INDEX idx_nip05_records_pubkey ON nip05_records(pubkey);
CREATE INDEX idx_nip05_records_domain_username ON nip05_records(domain_id, username);

-- admin_users: Admin dashboard users
CREATE TABLE admin_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    pubkey VARCHAR(64),
    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_users_username ON admin_users(username);

-- verification_logs: Audit trail for verifications
CREATE TABLE verification_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nip05 VARCHAR(511) NOT NULL,
    verification_type VARCHAR(20) NOT NULL,
    result VARCHAR(20) NOT NULL,
    error_message TEXT,
    remote_response TEXT,
    verified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_verification_logs_nip05 ON verification_logs(nip05);
CREATE INDEX idx_verification_logs_verified_at ON verification_logs(verified_at);
