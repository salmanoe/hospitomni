-- ═══════════════════════════════════════════════════════════════
-- V6__sync_status.sql — per-channel push health for /sync-status.
--
-- The relay stamps last_push_at on every successful push unit and
-- records the failure message on last_push_error (cleared again by
-- the next success). Together with live counts over ari_dirty_cell
-- and ari_dead_letter these back the sync-status endpoint the
-- doctor command reads.
--
-- last_push_error carries adapter/transport messages only — ARI
-- pushes contain no guest data, so no PII can reach this column.
--
-- @author Salman
-- @version 1.0
-- @since 2026-07-05
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE property_channel
    ADD COLUMN last_push_at TIMESTAMPTZ,
    ADD COLUMN last_push_error TEXT;
