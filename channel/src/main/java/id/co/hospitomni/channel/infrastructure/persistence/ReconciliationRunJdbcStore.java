/*
 * ReconciliationRunStore over reconciliation_run (V9).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.infrastructure.persistence;

import id.co.hospitomni.channel.domain.model.ReconciliationRun;
import id.co.hospitomni.channel.domain.port.out.ReconciliationRunStore;
import id.co.hospitomni.shared.PropertyChannelId;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class ReconciliationRunJdbcStore implements ReconciliationRunStore {

    private final JdbcClient jdbc;

    public ReconciliationRunJdbcStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReconciliationRun record(
            PropertyChannelId channelId, LocalDate dateFrom, LocalDate dateTo,
            int driftCount, boolean refreshed, long epoch, @Nullable String sampleDrift) {
        return jdbc.sql("""
                        INSERT INTO reconciliation_run
                            (property_channel_id, date_from, date_to,
                             drift_count, refreshed, epoch, sample_drift)
                        VALUES (:channelId, :dateFrom, :dateTo,
                                :driftCount, :refreshed, :epoch, :sampleDrift)
                        RETURNING id, property_channel_id, date_from, date_to,
                                  drift_count, refreshed, epoch, sample_drift, created_at""")
                .param("channelId", channelId.value())
                .param("dateFrom", dateFrom)
                .param("dateTo", dateTo)
                .param("driftCount", driftCount)
                .param("refreshed", refreshed)
                .param("epoch", epoch)
                .param("sampleDrift", sampleDrift)
                .query(ReconciliationRunJdbcStore::mapRow)
                .single();
    }

    @Override
    public List<ReconciliationRun> recentRuns(PropertyChannelId channelId, int limit) {
        return jdbc.sql("""
                        SELECT id, property_channel_id, date_from, date_to,
                               drift_count, refreshed, epoch, sample_drift, created_at
                        FROM reconciliation_run
                        WHERE property_channel_id = :channelId
                        ORDER BY created_at DESC
                        LIMIT :limit""")
                .param("channelId", channelId.value())
                .param("limit", limit)
                .query(ReconciliationRunJdbcStore::mapRow)
                .list();
    }

    private static ReconciliationRun mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ReconciliationRun(
                rs.getObject("id", UUID.class),
                PropertyChannelId.of(rs.getObject("property_channel_id", UUID.class)),
                rs.getDate("date_from").toLocalDate(),
                rs.getDate("date_to").toLocalDate(),
                rs.getInt("drift_count"),
                rs.getBoolean("refreshed"),
                rs.getLong("epoch"),
                rs.getString("sample_drift"),
                rs.getTimestamp("created_at").toInstant());
    }
}
