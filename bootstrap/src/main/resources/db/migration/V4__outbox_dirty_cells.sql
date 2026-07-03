-- ═══════════════════════════════════════════════════════════════
-- V4__outbox_dirty_cells.sql — ARI dirty-cell outbox schema.
--
-- Dirty-cell outbox: ARI writes mark (channel, unit, date) cells dirty in
-- the SAME transaction as the write; the relay claims them with
-- FOR UPDATE SKIP LOCKED, reads the CURRENT ARI values (last write wins —
-- replaying stale payloads would push intermediate states), coalesces
-- into range-compressed pushes per property-channel, and deletes on
-- success. Failures back off exponentially; after max attempts rows move
-- to ari_dead_letter.
--
-- Epoch fencing: property_channel.epoch is stamped on every push; a full
-- refresh (reconciler, later step) bumps it so older in-flight deltas
-- can't land after the refresh.
--
-- @author Salman
-- @version 1.0
-- @since 2026-07-03
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE property_channel
    ADD COLUMN epoch BIGINT NOT NULL DEFAULT 0;

CREATE TABLE ari_dirty_cell
(
    property_channel_id UUID        NOT NULL REFERENCES property_channel (id),
    -- AVAILABILITY → unit_id is a room_type id; RESTRICTION → a rate_plan id.
    unit_type           VARCHAR(16) NOT NULL CHECK (unit_type IN ('AVAILABILITY', 'RESTRICTION')),
    unit_id             UUID        NOT NULL,
    date                DATE        NOT NULL,
    attempts            INT         NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    marked_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (property_channel_id, unit_type, unit_id, date)
);

CREATE INDEX idx_dirty_cell_due ON ari_dirty_cell (next_attempt_at);

CREATE TABLE ari_dead_letter
(
    id                  UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    property_channel_id UUID        NOT NULL REFERENCES property_channel (id),
    unit_type           VARCHAR(16) NOT NULL,
    unit_id             UUID        NOT NULL,
    date                DATE        NOT NULL,
    attempts            INT         NOT NULL,
    last_error          TEXT,
    failed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dead_letter_channel ON ari_dead_letter (property_channel_id);
