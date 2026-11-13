ALTER TABLE payment_intents
    DROP CONSTRAINT ck_payment_provider;

ALTER TABLE payment_intents
    ADD CONSTRAINT ck_payment_provider
    CHECK (provider IN ('MOMO','CASSO','SANDBOX','CASH'));
