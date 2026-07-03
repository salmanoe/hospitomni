-- ═══════════════════════════════════════════════════════════════
-- V1__init_account_property.sql — PLAN.md build step 1 schema.
--
-- Design notes:
--   - Flyway owns all DDL; JPA runs ddl-auto=validate only.
--   - All PKs are UUIDs (HospitOmni owns and hands out these IDs).
--   - TIMESTAMPTZ everywhere (deviation from HospitOps's TIMESTAMP):
--     feed-expiry windows and cursors use database time; business
--     dates are hotel-local via property.timezone.
--   - api_key stores a SHA-256 hex digest only — raw keys are never
--     persisted; revoked_at supports revocation from day one.
--   - property_channel reserves the per-channel kill switch (paused)
--     and encrypted OTA credential columns (app-level AES-GCM, key
--     held outside the DB) required before any real adapter ships.
--
-- @author Salman
-- @version 1.0
-- @since 2026-07-03
-- ═══════════════════════════════════════════════════════════════

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── MODULE: account ─────────────────────────────────────────────
CREATE TABLE account
(
    id         UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    name       VARCHAR(200)  NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE api_key
(
    id         UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    account_id UUID          NOT NULL REFERENCES account (id),
    -- SHA-256 of the raw key, lowercase hex (64 chars). Lookup key.
    key_hash   VARCHAR(64)      NOT NULL UNIQUE,
    label      VARCHAR(200)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_api_key_account ON api_key (account_id);

-- ── MODULE: property ────────────────────────────────────────────
CREATE TABLE property
(
    id         UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    account_id UUID          NOT NULL REFERENCES account (id),
    title      VARCHAR(200)  NOT NULL,
    currency   VARCHAR(3)       NOT NULL DEFAULT 'IDR',
    -- IANA zone id (e.g. 'Asia/Jakarta'). Business dates are
    -- hotel-local — WIB, not server/UTC (PLAN.md step 1 anchor).
    timezone   VARCHAR(64)   NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_property_account ON property (account_id);

-- ── MODULE: channel (mapping shell — adapters arrive in step 4) ─
CREATE TABLE property_channel
(
    id                     UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    property_id            UUID         NOT NULL REFERENCES property (id),
    ota_name               VARCHAR(64)  NOT NULL,
    -- Per-channel kill switch honored by the outbox relay.
    paused                 BOOLEAN      NOT NULL DEFAULT FALSE,
    -- OTA secrets: AES-GCM ciphertext + the id of the app-held key
    -- that encrypted it (rotation support). Never plaintext.
    credentials_ciphertext BYTEA,
    credentials_key_id     VARCHAR(64),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (property_id, ota_name)
);

CREATE INDEX idx_property_channel_property ON property_channel (property_id);
