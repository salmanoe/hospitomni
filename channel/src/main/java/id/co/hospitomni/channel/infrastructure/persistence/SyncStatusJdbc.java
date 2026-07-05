/*
 * SyncStatusReader over SQL: property_channel joined with live aggregates
 * of ari_dirty_cell / ari_dead_letter, plus the booking_revision tip.
 * Read-only reporting path — counts are computed fresh on every call.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.channel.infrastructure.persistence;

import id.co.hospitomni.channel.domain.model.ChannelSyncStatus;
import id.co.hospitomni.channel.domain.port.out.SyncStatusReader;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.PropertyId;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class SyncStatusJdbc implements SyncStatusReader {

    private final JdbcClient jdbc;

    public SyncStatusJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ChannelSyncStatus> channelStatuses(AccountId accountId, @Nullable PropertyId propertyId) {
        return jdbc.sql("""
                        SELECT pc.id, pc.property_id, pc.ota_name, pc.paused, pc.epoch,
                               pc.last_push_at, pc.last_push_error,
                               pc.last_reconciled_at, pc.last_drift_count,
                               COALESCE(pend.pending, 0)  AS pending_cells,
                               pend.oldest_marked_at,
                               COALESCE(dead.letters, 0)  AS dead_letters
                        FROM property_channel pc
                        JOIN property p ON p.id = pc.property_id
                        LEFT JOIN (SELECT property_channel_id,
                                          COUNT(*)       AS pending,
                                          MIN(marked_at) AS oldest_marked_at
                                   FROM ari_dirty_cell
                                   GROUP BY property_channel_id) pend
                            ON pend.property_channel_id = pc.id
                        LEFT JOIN (SELECT property_channel_id, COUNT(*) AS letters
                                   FROM ari_dead_letter
                                   GROUP BY property_channel_id) dead
                            ON dead.property_channel_id = pc.id
                        WHERE p.account_id = :accountId
                          AND (CAST(:propertyId AS uuid) IS NULL OR pc.property_id = :propertyId)
                        ORDER BY p.title, pc.ota_name""")
                .param("accountId", accountId.value())
                .param("propertyId", propertyId == null ? null : propertyId.value())
                .query(SyncStatusJdbc::mapRow)
                .list();
    }

    @Override
    public StreamStatus bookingStream(AccountId accountId) {
        return jdbc.sql("""
                        SELECT COALESCE(MAX(seq), 0) AS tip_seq, MAX(created_at) AS last_event_at
                        FROM booking_revision
                        WHERE account_id = :accountId""")
                .param("accountId", accountId.value())
                .query((rs, rowNum) -> new StreamStatus(
                        rs.getLong("tip_seq"), instant(rs, "last_event_at")))
                .single();
    }

    private static ChannelSyncStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChannelSyncStatus(
                PropertyChannelId.of(rs.getObject("id", UUID.class)),
                PropertyId.of(rs.getObject("property_id", UUID.class)),
                rs.getString("ota_name"),
                rs.getBoolean("paused"),
                rs.getLong("epoch"),
                instant(rs, "last_push_at"),
                rs.getString("last_push_error"),
                rs.getLong("pending_cells"),
                instant(rs, "oldest_marked_at"),
                rs.getLong("dead_letters"),
                instant(rs, "last_reconciled_at"),
                rs.getObject("last_drift_count", Integer.class));
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
