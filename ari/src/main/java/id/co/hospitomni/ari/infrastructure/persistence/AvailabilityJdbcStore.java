/*
 * Availability day-cells via batch INSERT … ON CONFLICT DO UPDATE
 * (last write wins per cell).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.infrastructure.persistence;

import id.co.hospitomni.ari.domain.model.AvailabilityCell;
import id.co.hospitomni.ari.domain.port.out.AvailabilityStore;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
public class AvailabilityJdbcStore implements AvailabilityStore {

    private static final String UPSERT = """
            INSERT INTO availability (property_id, room_type_id, date, availability)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (room_type_id, date) DO UPDATE
            SET availability = EXCLUDED.availability, updated_at = NOW()""";

    private static final String READ = """
            SELECT property_id, room_type_id, date, availability
            FROM availability
            WHERE property_id = ? AND date >= COALESCE(?, date) AND date <= COALESCE(?, date)
            ORDER BY room_type_id, date""";

    private final JdbcTemplate jdbc;

    public AvailabilityJdbcStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(List<AvailabilityCell> cells) {
        jdbc.batchUpdate(UPSERT, cells, cells.size(), (ps, cell) -> {
            ps.setObject(1, cell.propertyId().value());
            ps.setObject(2, cell.roomTypeId().value());
            ps.setDate(3, Date.valueOf(cell.date()));
            ps.setInt(4, cell.availability());
        });
    }

    @Override
    public List<AvailabilityCell> read(
            PropertyId propertyId, @Nullable LocalDate dateGte, @Nullable LocalDate dateLte) {
        return jdbc.query(READ,
                (rs, i) -> new AvailabilityCell(
                        PropertyId.of(rs.getObject("property_id", java.util.UUID.class)),
                        RoomTypeId.of(rs.getObject("room_type_id", java.util.UUID.class)),
                        rs.getDate("date").toLocalDate(),
                        rs.getInt("availability")),
                propertyId.value(),
                dateGte == null ? null : Date.valueOf(dateGte),
                dateLte == null ? null : Date.valueOf(dateLte));
    }
}
