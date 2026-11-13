ALTER TABLE reviews
    ADD COLUMN ai_sentiment VARCHAR(20),
    ADD COLUMN ai_sentiment_score NUMERIC(6,5),
    ADD COLUMN ai_topics JSONB,
    ADD COLUMN ai_urgency VARCHAR(20),
    ADD COLUMN ai_confidence NUMERIC(6,5),
    ADD COLUMN ai_draft_response TEXT,
    ADD COLUMN ai_model_version VARCHAR(100),
    ADD COLUMN ai_provider VARCHAR(30),
    ADD COLUMN ai_analyzed_at TIMESTAMPTZ;

ALTER TABLE reviews
    ADD CONSTRAINT ck_review_ai_sentiment
        CHECK (ai_sentiment IS NULL OR ai_sentiment IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE')),
    ADD CONSTRAINT ck_review_ai_urgency
        CHECK (ai_urgency IS NULL OR ai_urgency IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    ADD CONSTRAINT ck_review_ai_sentiment_score
        CHECK (ai_sentiment_score IS NULL OR ai_sentiment_score BETWEEN -1 AND 1),
    ADD CONSTRAINT ck_review_ai_confidence
        CHECK (ai_confidence IS NULL OR ai_confidence BETWEEN 0 AND 1);

CREATE INDEX ix_reviews_ai_triage
    ON reviews(ai_urgency, ai_sentiment, created_at DESC)
    WHERE ai_analyzed_at IS NOT NULL;
