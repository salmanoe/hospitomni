-- ═══════════════════════════════════════════════════════════════
-- V2__room_type_rate_plan.sql — room-type / rate-plan content schema.
--
-- Channex-compatible content resources. HospitOmni owns these UUIDs;
-- the PMS stores them as external ids in its channel mappings.
-- rate: integer minor units (IDR is exponent-0 — see shared Money notes).
--
-- @author Salman
-- @version 1.0
-- @since 2026-07-03
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE room_type
(
    id             UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    property_id    UUID         NOT NULL REFERENCES property (id),
    title          VARCHAR(200) NOT NULL,
    count_of_rooms INT          NOT NULL CHECK (count_of_rooms > 0),
    occ_adults     INT          NOT NULL DEFAULT 2 CHECK (occ_adults > 0),
    occ_children   INT          NOT NULL DEFAULT 0 CHECK (occ_children >= 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (property_id, title)
);

CREATE INDEX idx_room_type_property ON room_type (property_id);

CREATE TABLE rate_plan
(
    id           UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    property_id  UUID         NOT NULL REFERENCES property (id),
    room_type_id UUID         NOT NULL REFERENCES room_type (id),
    title        VARCHAR(200) NOT NULL,
    -- ISO 4217; rates on this plan are integer minor units of this currency.
    currency     VARCHAR(3)   NOT NULL DEFAULT 'IDR',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (room_type_id, title)
);

CREATE INDEX idx_rate_plan_property ON rate_plan (property_id);
CREATE INDEX idx_rate_plan_room_type ON rate_plan (room_type_id);
