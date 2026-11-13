CREATE TABLE IF NOT EXISTS revenue_price_constraints (
    room_type_id UUID PRIMARY KEY,
    floor_price NUMERIC(19,2) NOT NULL CHECK (floor_price > 0),
    ceiling_price NUMERIC(19,2) NOT NULL CHECK (ceiling_price >= floor_price),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS revenue_price_proposals (
    id UUID PRIMARY KEY,
    room_type_id UUID NOT NULL,
    stay_date DATE NOT NULL,
    current_price NUMERIC(19,2) NOT NULL CHECK (current_price > 0),
    recommended_price NUMERIC(19,2) NOT NULL CHECK (recommended_price > 0),
    floor_price NUMERIC(19,2) NOT NULL,
    ceiling_price NUMERIC(19,2) NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    explanation JSONB NOT NULL,
    proposal_version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by UUID,
    decision_comment TEXT,
    applied_rule_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_revenue_proposal_bounds CHECK (
        floor_price <= recommended_price AND recommended_price <= ceiling_price),
    CONSTRAINT ck_revenue_proposal_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    UNIQUE(room_type_id,stay_date,proposal_version)
);
CREATE INDEX IF NOT EXISTS idx_revenue_proposals_status_date
    ON revenue_price_proposals(status,stay_date,created_at DESC);
