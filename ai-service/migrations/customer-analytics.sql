CREATE TABLE IF NOT EXISTS review_analyses (
    review_id UUID PRIMARY KEY,
    sentiment VARCHAR(20) NOT NULL,
    sentiment_score NUMERIC(6,5) NOT NULL,
    topics JSONB NOT NULL,
    urgency VARCHAR(20) NOT NULL,
    confidence NUMERIC(6,5) NOT NULL,
    draft_response TEXT NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    analyzed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS booking_risk_scores (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    reservation_reference VARCHAR(40),
    cancellation_probability NUMERIC(6,5) NOT NULL,
    no_show_probability NUMERIC(6,5) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    features JSONB NOT NULL,
    explanations JSONB NOT NULL,
    recommended_action TEXT NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    scored_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_booking_risk_probability CHECK (
        cancellation_probability BETWEEN 0 AND 1 AND no_show_probability BETWEEN 0 AND 1),
    CONSTRAINT ck_booking_risk_level CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX IF NOT EXISTS idx_booking_risk_reservation_time
    ON booking_risk_scores(reservation_id,scored_at DESC);
CREATE INDEX IF NOT EXISTS idx_booking_risk_level_time
    ON booking_risk_scores(risk_level,scored_at DESC);
