/*
 * Current-value reads for the relay (named-parameter IN lists over the ari
 * tables — same schema-level coupling note as ari's ContentCatalogJdbc).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.infrastructure.persistence;

import id.co.hospitomni.channel.domain.model.RestrictionValues;
import id.co.hospitomni.channel.domain.port.out.AriValueReader;
import id.co.hospitomni.shared.PropertyId;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AriValueJdbcReader implements AriValueReader {

    private final NamedParameterJdbcTemplate jdbc;

    public AriValueJdbcReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<LocalDate, Integer> availabilityFor(UUID roomTypeId, List<LocalDate> dates) {
        Map<LocalDate, Integer> values = new HashMap<>();
        jdbc.query("""
                        SELECT date, availability FROM availability
                        WHERE room_type_id = :roomTypeId AND date IN (:dates)""",
                Map.of("roomTypeId", roomTypeId, "dates", dates),
                rs -> {
                    values.put(rs.getDate("date").toLocalDate(), rs.getInt("availability"));
                });
        return values;
    }

    @Override
    public Map<LocalDate, RestrictionValues> restrictionsFor(UUID ratePlanId, List<LocalDate> dates) {
        Map<LocalDate, RestrictionValues> values = new HashMap<>();
        jdbc.query("""
                        SELECT date, rate, min_stay, max_stay,
                               closed_to_arrival, closed_to_departure, stop_sell
                        FROM restriction
                        WHERE rate_plan_id = :ratePlanId AND date IN (:dates)""",
                Map.of("ratePlanId", ratePlanId, "dates", dates),
                rs -> {
                    values.put(rs.getDate("date").toLocalDate(), new RestrictionValues(
                            rs.getObject("rate", Long.class),
                            rs.getObject("min_stay", Integer.class),
                            rs.getObject("max_stay", Integer.class),
                            rs.getObject("closed_to_arrival", Boolean.class),
                            rs.getObject("closed_to_departure", Boolean.class),
                            rs.getObject("stop_sell", Boolean.class)));
                });
        return values;
    }

    @Override
    public List<UUID> roomTypeIdsOf(PropertyId propertyId) {
        return unitIdsOf("room_type", propertyId);
    }

    @Override
    public List<UUID> ratePlanIdsOf(PropertyId propertyId) {
        return unitIdsOf("rate_plan", propertyId);
    }

    private List<UUID> unitIdsOf(String table, PropertyId propertyId) {
        // Table name comes from the two fixed call sites above, never input.
        return jdbc.query(
                "SELECT id FROM " + table + " WHERE property_id = :propertyId ORDER BY id",
                Map.of("propertyId", propertyId.value()),
                (rs, i) -> rs.getObject("id", UUID.class));
    }
}
