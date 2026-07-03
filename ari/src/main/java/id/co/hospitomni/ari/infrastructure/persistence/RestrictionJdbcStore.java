/*
 * Restriction day-cells via batch INSERT … ON CONFLICT DO UPDATE with
 * per-field COALESCE merge: an unsent (null-bound) field keeps the stored
 * value — Channex partial-update semantics. Consequence: a field cannot be
 * reset to NULL through this path (documented in the V3 migration).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.infrastructure.persistence;

import id.co.hospitomni.ari.domain.model.RestrictionCell;
import id.co.hospitomni.ari.domain.model.RestrictionFields;
import id.co.hospitomni.ari.domain.port.out.RestrictionStore;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class RestrictionJdbcStore implements RestrictionStore {

    private static final String UPSERT_MERGE = """
            INSERT INTO restriction (property_id, rate_plan_id, date, rate, min_stay, max_stay,
                                     closed_to_arrival, closed_to_departure, stop_sell)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (rate_plan_id, date) DO UPDATE SET
                rate                = COALESCE(EXCLUDED.rate, restriction.rate),
                min_stay            = COALESCE(EXCLUDED.min_stay, restriction.min_stay),
                max_stay            = COALESCE(EXCLUDED.max_stay, restriction.max_stay),
                closed_to_arrival   = COALESCE(EXCLUDED.closed_to_arrival, restriction.closed_to_arrival),
                closed_to_departure = COALESCE(EXCLUDED.closed_to_departure, restriction.closed_to_departure),
                stop_sell           = COALESCE(EXCLUDED.stop_sell, restriction.stop_sell),
                updated_at          = NOW()""";

    private static final String READ = """
            SELECT property_id, rate_plan_id, date, rate, min_stay, max_stay,
                   closed_to_arrival, closed_to_departure, stop_sell
            FROM restriction
            WHERE property_id = ? AND date >= COALESCE(?, date) AND date <= COALESCE(?, date)
            ORDER BY rate_plan_id, date""";

    private final JdbcTemplate jdbc;

    public RestrictionJdbcStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertMerge(List<RestrictionCell> cells) {
        jdbc.batchUpdate(UPSERT_MERGE, cells, cells.size(), (ps, cell) -> {
            RestrictionFields f = cell.fields();
            ps.setObject(1, cell.propertyId().value());
            ps.setObject(2, cell.ratePlanId().value());
            ps.setDate(3, Date.valueOf(cell.date()));
            ps.setObject(4, f.rate(), Types.BIGINT);
            ps.setObject(5, f.minStay(), Types.INTEGER);
            ps.setObject(6, f.maxStay(), Types.INTEGER);
            ps.setObject(7, f.closedToArrival(), Types.BOOLEAN);
            ps.setObject(8, f.closedToDeparture(), Types.BOOLEAN);
            ps.setObject(9, f.stopSell(), Types.BOOLEAN);
        });
    }

    @Override
    public List<RestrictionCell> read(
            PropertyId propertyId, @Nullable LocalDate dateGte, @Nullable LocalDate dateLte) {
        return jdbc.query(READ,
                (rs, i) -> new RestrictionCell(
                        PropertyId.of(rs.getObject("property_id", UUID.class)),
                        RatePlanId.of(rs.getObject("rate_plan_id", UUID.class)),
                        rs.getDate("date").toLocalDate(),
                        new RestrictionFields(
                                rs.getObject("rate", Long.class),
                                rs.getObject("min_stay", Integer.class),
                                rs.getObject("max_stay", Integer.class),
                                rs.getObject("closed_to_arrival", Boolean.class),
                                rs.getObject("closed_to_departure", Boolean.class),
                                rs.getObject("stop_sell", Boolean.class))),
                propertyId.value(),
                dateGte == null ? null : Date.valueOf(dateGte),
                dateLte == null ? null : Date.valueOf(dateLte));
    }
}
