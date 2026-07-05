-- ═══════════════════════════════════════════════════════════════
-- V7__idempotency_record.sql — replay store for the Idempotency-Key
-- header (required on all north-API writes).
--
-- One row per (account, key). A row is claimed (response columns
-- NULL) before the write executes and completed with the response
-- afterwards, so a concurrent duplicate can be detected while the
-- first attempt is still in flight. request_hash pins the key to
-- one exact payload; reuse with a different payload is rejected.
--
-- Rows older than the retention window are purged on a schedule —
-- a replayed key only has to survive the client's retry horizon,
-- not forever.
--
-- @author Salman
-- @version 1.0
-- @since 2026-07-05
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE idempotency_record (
    id                    UUID PRIMARY KEY,
    account_id            UUID        NOT NULL REFERENCES account (id),
    idem_key              TEXT        NOT NULL,
    request_hash          TEXT        NOT NULL,
    response_status       INT,
    response_content_type TEXT,
    response_body         TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ,

    CONSTRAINT uq_idempotency_account_key UNIQUE (account_id, idem_key)
);

-- The purge job deletes by age.
CREATE INDEX idx_idempotency_created_at ON idempotency_record (created_at);
