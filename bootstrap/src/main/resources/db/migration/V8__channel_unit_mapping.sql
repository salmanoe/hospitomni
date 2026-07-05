-- ═══════════════════════════════════════════════════════════════
-- V8__channel_unit_mapping.sql — internal unit ↔ OTA listing codes.
--
-- One row per (channel, unit): the property itself, a room type, or a
-- rate plan, each mapped to the code the OTA knows it by. Real adapters
-- translate AriPush unit UUIDs to these codes on the way out and OTA
-- booking payloads back to UUIDs on the way in.
--
-- PUT /mappings is a full replace per channel, so rows carry no
-- identity worth preserving — a surrogate id keeps deletes simple.
-- Both UNIQUE constraints guard operator mistakes: a unit mapped twice,
-- or two units sharing one OTA code on the same channel.
--
-- @author Salman
-- @version 1.0
-- @since 2026-07-05
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE channel_unit_mapping
(
    id                  UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    property_channel_id UUID         NOT NULL REFERENCES property_channel (id),
    unit_type           VARCHAR(16)  NOT NULL CHECK (unit_type IN ('PROPERTY', 'ROOM_TYPE', 'RATE_PLAN')),
    -- PROPERTY → property id; ROOM_TYPE → room_type id; RATE_PLAN → rate_plan id.
    unit_id             UUID         NOT NULL,
    ota_code            VARCHAR(128) NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    UNIQUE (property_channel_id, unit_type, unit_id),
    UNIQUE (property_channel_id, unit_type, ota_code)
);
