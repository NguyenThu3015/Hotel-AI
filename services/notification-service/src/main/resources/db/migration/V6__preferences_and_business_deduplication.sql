ALTER TABLE notification_jobs
    ADD COLUMN deduplication_key VARCHAR(200);

UPDATE notification_jobs
SET deduplication_key = source_event_id
WHERE deduplication_key IS NULL;

ALTER TABLE notification_jobs
    ALTER COLUMN deduplication_key SET NOT NULL;

ALTER TABLE notification_jobs
    ADD CONSTRAINT uq_notification_deduplication_key UNIQUE (deduplication_key);

ALTER TABLE inbox_events
    ADD COLUMN job_id UUID;

UPDATE inbox_events inbox
SET job_id = job.id
FROM notification_jobs job
WHERE job.source_event_id = inbox.event_id;

ALTER TABLE inbox_events
    ADD CONSTRAINT fk_inbox_notification_job
    FOREIGN KEY (job_id) REFERENCES notification_jobs(id);

CREATE TABLE notification_preferences (
    identity_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (identity_id, channel),
    CONSTRAINT ck_notification_preference_channel
        CHECK (channel IN ('EMAIL', 'SMS', 'ZALO'))
);
