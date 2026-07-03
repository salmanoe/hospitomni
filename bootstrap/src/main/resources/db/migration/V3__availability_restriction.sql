-- ═══════════════════════════════════════════════════════════════
-- V3__availability_restriction.sql — PLAN.md build step 3 ARI storage.
--
-- Set-shaped, high-volume tables written via batch SQL
-- (INSERT … ON CONFLICT DO UPDATE), not JPA — see decision log
-- "Persistence split by workload". One row per day-cell; reads are
-- range-compressed in the application layer.
--
-- property_id is denormalized onto both tables: every Channex-shaped
-- read filters by property, and the relay fans out per property-channel.
--
-- Restriction fields are nullable: NULL = "never set". Partial updates
-- merge per field (only sent fields change); a field cannot be reset
-- to NULL once set — matches Channex semantics.
--
-- @author Salman
-- @version 1.0
-- @since 2026-07-03
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE availability
(
    property_id  UUID        NOT NULL REFERENCES property (id),
    room_type_id UUID        NOT NULL REFERENCES room_type (id),
    date         DATE        NOT NULL,
    availability INT         NOT NULL CHECK (availability >= 0),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (room_type_id, date)
);

CREATE INDEX idx_availability_property_date ON availability (property_id, date);

CREATE TABLE restriction
(
    property_id         UUID        NOT NULL REFERENCES property (id),
    rate_plan_id        UUID        NOT NULL REFERENCES rate_plan (id),
    date                DATE        NOT NULL,
    -- Integer minor units of the rate plan's currency (IDR is exponent-0).
    rate                BIGINT      CHECK (rate IS NULL OR rate >= 0),
    min_stay            INT         CHECK (min_stay IS NULL OR min_stay >= 1),
    max_stay            INT         CHECK (max_stay IS NULL OR max_stay >= 1),
    closed_to_arrival   BOOLEAN,
    closed_to_departure BOOLEAN,
    stop_sell           BOOLEAN,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (rate_plan_id, date)
);

CREATE INDEX idx_restriction_property_date ON restriction (property_id, date);
