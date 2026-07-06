/*
 * Dirty-cell outbox over ari_dirty_cell/ari_dead_letter. mark() expands the
 * span server-side with generate_series; claimDue() uses FOR UPDATE SKIP
 * LOCKED; recordFailure() applies capped exponential backoff and moves
 * exhausted rows to the dead-letter table.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.infrastructure.persistence;

import id.co.hospitomni.channel.domain.model.DirtyCell;
import id.co.hospitomni.channel.domain.port.out.DirtyCellStore;
import id.co.hospitomni.shared.PropertyChannelId;
import id.co.hospitomni.shared.event.AriChangedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class DirtyCellJdbcStore implements DirtyCellStore {

    private final JdbcTemplate jdbc;

    public DirtyCellJdbcStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void mark(List<PropertyChannelId> channelIds, AriChangedEvent.Unit unit,
                     UUID unitId, LocalDate dateFrom, LocalDate dateTo) {
        for (PropertyChannelId channelId : channelIds) {
            jdbc.update("""
                            INSERT INTO ari_dirty_cell (property_channel_id, unit_type, unit_id, date)
                            SELECT ?, ?, ?, d::date
                            FROM generate_series(?::date, ?::date, interval '1 day') AS d
                            ON CONFLICT (property_channel_id, unit_type, unit_id, date)
                            DO UPDATE SET marked_at = NOW()""",
                    channelId.value(), unit.name(), unitId,
                    Date.valueOf(dateFrom), Date.valueOf(dateTo));
        }
    }

    @Override
    public List<PropertyChannelId> dueChannelIds() {
        return jdbc.query("""
                        SELECT DISTINCT dc.property_channel_id
                        FROM ari_dirty_cell dc
                        JOIN property_channel pc ON pc.id = dc.property_channel_id
                        WHERE dc.next_attempt_at <= NOW() AND pc.paused = FALSE""",
                (rs, i) -> PropertyChannelId.of(rs.getObject(1, UUID.class)));
    }

    @Override
    public List<DirtyCell> claimDue(PropertyChannelId channelId) {
        return jdbc.query("""
                        SELECT property_channel_id, unit_type, unit_id, date, attempts
                        FROM ari_dirty_cell
                        WHERE property_channel_id = ? AND next_attempt_at <= NOW()
                        ORDER BY unit_type, unit_id, date
                        FOR UPDATE SKIP LOCKED""",
                (rs, i) -> new DirtyCell(
                        PropertyChannelId.of(rs.getObject("property_channel_id", UUID.class)),
                        AriChangedEvent.Unit.valueOf(rs.getString("unit_type")),
                        rs.getObject("unit_id", UUID.class),
                        rs.getDate("date").toLocalDate(),
                        rs.getInt("attempts")),
                channelId.value());
    }

    @Override
    public void delete(List<DirtyCell> cells) {
        jdbc.batchUpdate("""
                        DELETE FROM ari_dirty_cell
                        WHERE property_channel_id = ? AND unit_type = ? AND unit_id = ? AND date = ?""",
                cells, cells.size(), (ps, cell) -> {
                    ps.setObject(1, cell.channelId().value());
                    ps.setString(2, cell.unit().name());
                    ps.setObject(3, cell.unitId());
                    ps.setDate(4, Date.valueOf(cell.date()));
                });
    }

    @Override
    public void recordFailure(PropertyChannelId channelId, String error,
                              long backoffBaseSeconds, int maxAttempts) {
        // Exhausted rows → dead letter, then remove from the outbox.
        jdbc.update("""
                        INSERT INTO ari_dead_letter
                            (property_channel_id, unit_type, unit_id, date, attempts, last_error)
                        SELECT property_channel_id, unit_type, unit_id, date, attempts + 1, ?
                        FROM ari_dirty_cell
                        WHERE property_channel_id = ? AND next_attempt_at <= NOW()
                          AND attempts + 1 >= ?""",
                error, channelId.value(), maxAttempts);
        jdbc.update("""
                        DELETE FROM ari_dirty_cell
                        WHERE property_channel_id = ? AND next_attempt_at <= NOW()
                          AND attempts + 1 >= ?""",
                channelId.value(), maxAttempts);
        // Remaining due rows: bump attempts, capped exponential backoff.
        jdbc.update("""
                        UPDATE ari_dirty_cell
                        SET attempts = attempts + 1,
                            next_attempt_at = NOW()
                                + (? * POWER(2, LEAST(attempts, 5))) * interval '1 second'
                        WHERE property_channel_id = ? AND next_attempt_at <= NOW()""",
                backoffBaseSeconds, channelId.value());
    }

    @Override
    public void recordRateLimited(PropertyChannelId channelId, long retryAfterSeconds) {
        jdbc.update("""
                        UPDATE ari_dirty_cell
                        SET next_attempt_at = NOW() + ? * interval '1 second'
                        WHERE property_channel_id = ? AND next_attempt_at <= NOW()""",
                retryAfterSeconds, channelId.value());
    }
}
