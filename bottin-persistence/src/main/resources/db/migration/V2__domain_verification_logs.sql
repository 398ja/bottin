-- Domain verification logs table for tracking verification attempts
CREATE TABLE domain_verification_logs (
    id BIGSERIAL PRIMARY KEY,
    domain_id BIGINT NOT NULL,
    method VARCHAR(20) NOT NULL,
    success BOOLEAN NOT NULL,
    message VARCHAR(500),
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dv_logs_domain FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE
);

CREATE INDEX idx_dv_logs_domain_id ON domain_verification_logs(domain_id);
CREATE INDEX idx_dv_logs_attempted_at ON domain_verification_logs(attempted_at);
