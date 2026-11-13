CREATE TABLE stay_review_projections (
    stay_id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    reservation_reference VARCHAR(40) NOT NULL,
    contact_name VARCHAR(200) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    status VARCHAR(40) NOT NULL,
    scheduled_check_in DATE NOT NULL,
    scheduled_check_out DATE NOT NULL,
    actual_check_out TIMESTAMPTZ,
    room_numbers VARCHAR(500) NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_review_stay_dates CHECK (
        scheduled_check_out > scheduled_check_in
    )
);

CREATE INDEX ix_review_stays_customer_status
    ON stay_review_projections(customer_id, status, actual_check_out DESC);

CREATE TABLE customer_identity_projections (
    customer_id UUID PRIMARY KEY,
    identity_id UUID NOT NULL UNIQUE,
    source_version BIGINT NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    stay_id UUID NOT NULL UNIQUE,
    reservation_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    reservation_reference VARCHAR(40) NOT NULL,
    author_display_name VARCHAR(120) NOT NULL,
    customer_email VARCHAR(320) NOT NULL,
    rating INTEGER NOT NULL,
    comment TEXT NOT NULL,
    comment_hash VARCHAR(64) NOT NULL,
    topics VARCHAR(300) NOT NULL,
    moderation_flags JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    verified BOOLEAN NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_review_status CHECK (
        status IN ('PUBLISHED', 'PENDING_MODERATION', 'HIDDEN', 'REJECTED')
    ),
    CONSTRAINT ck_review_comment_hash CHECK (length(comment_hash) = 64)
);

CREATE INDEX ix_reviews_public
    ON reviews(status, created_at DESC);
CREATE INDEX ix_reviews_customer
    ON reviews(customer_id, created_at DESC);
CREATE INDEX ix_reviews_spam
    ON reviews(customer_id, comment_hash, created_at DESC);

CREATE TABLE review_responses (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL UNIQUE REFERENCES reviews(id),
    content TEXT NOT NULL,
    responded_by UUID NOT NULL,
    responded_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revision_number INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_review_response_revision CHECK (revision_number > 0)
);

CREATE TABLE review_response_revisions (
    id UUID PRIMARY KEY,
    response_id UUID NOT NULL REFERENCES review_responses(id),
    review_id UUID NOT NULL REFERENCES reviews(id),
    revision_number INTEGER NOT NULL,
    content TEXT NOT NULL,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT uq_review_response_revision
        UNIQUE (response_id, revision_number)
);

CREATE INDEX ix_review_response_history
    ON review_response_revisions(review_id, revision_number);

CREATE TABLE review_moderation_decisions (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES reviews(id),
    action VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    decided_by UUID NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT ck_review_moderation_action CHECK (
        action IN ('PUBLISH', 'HIDE', 'REJECT')
    )
);

CREATE INDEX ix_review_moderation_history
    ON review_moderation_decisions(review_id, decided_at);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(140) NOT NULL,
    producer VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE review_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(140) NOT NULL,
    correlation_id VARCHAR(120) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_review_outbox_pending
    ON review_outbox_events(occurred_at)
    WHERE published_at IS NULL;
