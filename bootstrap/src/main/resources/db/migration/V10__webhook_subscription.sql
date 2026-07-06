-- ═══════════════════════════════════════════════════════════════
-- V10__webhook_subscription.sql — signed webhook push (fast path).
--
-- One row per (account, url): where to push booking events, the HMAC
-- signing secret (AES-GCM at rest, key held outside the DB), and the
-- delivery state inline — the relay worker claims exactly this row
-- FOR UPDATE SKIP LOCKED, so state and claim share one lock.
--
-- last_delivered_seq starts at the stream tip: webhooks are the fast
-- path for NEW events; history bootstrap is the polling backstop's job
-- (GET /booking-events, which never expires).
--
-- There is no dead-letter and no auto-disable: a failing endpoint backs
-- off to a capped interval forever, because the stream is replayable
-- and webhook loss is never data loss. Note: the plaintext secret
-- returned once at registration also transits idempotency_record
-- .response_body until the 24h purge (accepted trade-off — a replayed
-- registration must return the same secret).
--
-- @author Salman
-- @version 1.0
-- @since 2026-07-06
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE webhook_subscription
(
    id                   UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    account_id           UUID          NOT NULL REFERENCES account (id),
    url                  VARCHAR(2048) NOT NULL,
    secret_ciphertext    BYTEA         NOT NULL,
    secret_key_id        VARCHAR(64)   NOT NULL,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    last_delivered_seq   BIGINT        NOT NULL,
    consecutive_failures INT           NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_error           VARCHAR(500),
    last_success_at      TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    UNIQUE (account_id, url)
);

CREATE INDEX idx_webhook_subscription_due
    ON webhook_subscription (next_attempt_at) WHERE active;
