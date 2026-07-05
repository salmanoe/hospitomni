-- ═══════════════════════════════════════════════════════════════
-- V9__reconciliation.sql — drift-correction runs + channel stamps.
--
-- A reconciliation run diffs HospitOmni's intended ARI against the
-- OTA's observed state for one channel and window. Drift triggers a
-- full refresh: the channel epoch is bumped (fencing — older in-flight
-- deltas carry a stale epoch and can't land after the refresh) and the
-- whole window is re-marked dirty for the relay.
--
-- sample_drift holds a capped plain-text sample of drifted cells for
-- ops eyeballs — ARI numbers only, never guest data (UU PDP: no PII
-- in audit tables). last_reconciled_at/last_drift_count on the channel
-- feed /sync-status.
--
-- @author Salman
-- @version 1.0
-- @since 2026-07-06
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE property_channel
    ADD COLUMN last_reconciled_at TIMESTAMPTZ,
    ADD COLUMN last_drift_count   INT;

CREATE TABLE reconciliation_run
(
    id                  UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    property_channel_id UUID        NOT NULL REFERENCES property_channel (id),
    date_from           DATE        NOT NULL,
    date_to             DATE        NOT NULL,
    drift_count         INT         NOT NULL,
    refreshed           BOOLEAN     NOT NULL,
    -- Channel epoch AFTER the run (bumped when refreshed).
    epoch               BIGINT      NOT NULL,
    sample_drift        TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reconciliation_run_channel
    ON reconciliation_run (property_channel_id, created_at DESC);
