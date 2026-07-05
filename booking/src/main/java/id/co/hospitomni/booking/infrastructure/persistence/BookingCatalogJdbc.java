/*
 * EXISTS checks against the property module's tables. Schema-level coupling
 * accepted instead of a module dependency; the property module's Flyway
 * migrations own these tables.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.infrastructure.persistence;

import id.co.hospitomni.booking.domain.port.out.BookingCatalog;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingCatalogJdbc implements BookingCatalog {

    private final JdbcTemplate jdbc;

    public BookingCatalogJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean propertyOwnedBy(PropertyId propertyId, AccountId accountId) {
        return exists("SELECT EXISTS(SELECT 1 FROM property WHERE id = ? AND account_id = ?)",
                propertyId.value(), accountId.value());
    }

    @Override
    public boolean roomTypeInProperty(RoomTypeId roomTypeId, PropertyId propertyId) {
        return exists("SELECT EXISTS(SELECT 1 FROM room_type WHERE id = ? AND property_id = ?)",
                roomTypeId.value(), propertyId.value());
    }

    @Override
    public boolean ratePlanInProperty(RatePlanId ratePlanId, PropertyId propertyId) {
        return exists("SELECT EXISTS(SELECT 1 FROM rate_plan WHERE id = ? AND property_id = ?)",
                ratePlanId.value(), propertyId.value());
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }
}
