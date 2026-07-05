-- ═══════════════════════════════════════════════════════════════
-- V5__booking_events.sql — inbound bookings + durable event stream.
--
-- booking / booking_room hold the CURRENT state of each OTA booking
-- (aggregate-shaped, low-volume — managed via JPA).
--
-- booking_revision is the append-only stream the PMS polls:
-- seq (BIGSERIAL) is the cursor — oldest-first, replayable from any
-- position, nothing expires, no ack. Each row snapshots the full
-- booking payload at that revision so a replay returns exactly what
-- was originally delivered.
--
-- Dedupe is schema-level: an OTA redelivering the same notification
-- (same reservation code + revision_seq) hits the UNIQUE constraint
-- and produces no second event. Ordering within one booking is by
-- revision_seq, never timestamps.
--
-- Guest PII (UU PDP): only the minimum contact fields the PMS
-- contract requires are stored, on booking + inside the revision
-- payload (the delivery mechanism itself). PII must never be copied
-- into logs, dead-letter rows, or audit tables — reference bookings
-- by id/reservation code there. Retention/erasure policy lands
-- before the first live property.
--
-- @author Salman
-- @version 1.0
-- @since 2026-07-05
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE booking
(
    id                   UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    account_id           UUID         NOT NULL REFERENCES account (id),
    property_id          UUID         NOT NULL REFERENCES property (id),
    ota_name             VARCHAR(64)  NOT NULL,
    ota_reservation_code VARCHAR(128) NOT NULL,
    status               VARCHAR(16)  NOT NULL CHECK (status IN ('new', 'modified', 'cancelled')),
    -- Highest revision applied; lower/equal inbound revisions are ignored.
    latest_revision_seq  INT          NOT NULL CHECK (latest_revision_seq >= 1),
    customer_name        VARCHAR(200) NOT NULL,
    customer_surname     VARCHAR(200),
    customer_mail        VARCHAR(320),
    customer_phone       VARCHAR(32),
    -- ISO 3166-1 alpha-2.
    customer_country     VARCHAR(2),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (account_id, ota_name, ota_reservation_code)
);

CREATE INDEX idx_booking_property ON booking (property_id);

CREATE TABLE booking_room
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    booking_id    UUID        NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    room_type_id  UUID        NOT NULL REFERENCES room_type (id),
    rate_plan_id  UUID        NOT NULL REFERENCES rate_plan (id),
    -- Hotel-local dates (property.timezone); nights = [checkin, checkout).
    checkin_date  DATE        NOT NULL,
    checkout_date DATE        NOT NULL,
    occ_adults    INT         NOT NULL CHECK (occ_adults >= 1),
    occ_children  INT         NOT NULL DEFAULT 0 CHECK (occ_children >= 0),
    CHECK (checkout_date > checkin_date)
);

CREATE INDEX idx_booking_room_booking ON booking_room (booking_id);

CREATE TABLE booking_revision
(
    -- The stream cursor: clients poll ?after=<seq> and keep the last seq they saw.
    seq                  BIGSERIAL PRIMARY KEY,
    account_id           UUID         NOT NULL REFERENCES account (id),
    booking_id           UUID         NOT NULL REFERENCES booking (id),
    ota_name             VARCHAR(64)  NOT NULL,
    ota_reservation_code VARCHAR(128) NOT NULL,
    revision_seq         INT          NOT NULL CHECK (revision_seq >= 1),
    status               VARCHAR(16)  NOT NULL CHECK (status IN ('new', 'modified', 'cancelled')),
    -- Full booking-event snapshot as delivered to the PMS.
    payload              JSONB        NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (account_id, ota_name, ota_reservation_code, revision_seq)
);

CREATE INDEX idx_booking_revision_stream ON booking_revision (account_id, seq);
