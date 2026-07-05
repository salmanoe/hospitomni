/*
 * AvailabilityAdjuster over plain SQL against the ari module's availability
 * table (schema-level coupling, same rationale as BookingCatalogJdbc).
 * GREATEST(…, 0) floors decrements — the day the last room sells, the cell
 * hits 0 and stays there until the PMS's authoritative push.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.infrastructure.persistence;

import id.co.hospitomni.booking.domain.port.out.AvailabilityAdjuster;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class AvailabilityAdjusterJdbc implements AvailabilityAdjuster {

    private final JdbcClient jdbc;

    public AvailabilityAdjusterJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void adjust(PropertyId propertyId, RoomTypeId roomTypeId, LocalDate from, LocalDate to, int delta) {
        jdbc.sql("""
                        UPDATE availability
                        SET availability = GREATEST(availability + :delta, 0),
                            updated_at   = NOW()
                        WHERE property_id = :propertyId
                          AND room_type_id = :roomTypeId
                          AND date BETWEEN :from AND :to""")
                .param("delta", delta)
                .param("propertyId", propertyId.value())
                .param("roomTypeId", roomTypeId.value())
                .param("from", from)
                .param("to", to)
                .update();
    }
}
