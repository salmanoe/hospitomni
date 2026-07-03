/*
 * Published by the ari module inside the write transaction whenever ARI
 * cells change. The channel module's DirtyCellWriter listens synchronously,
 * so dirty-cell rows commit atomically with the ARI write (outbox pattern).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.shared.event;

import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;

import java.time.LocalDate;
import java.util.UUID;

public record AriChangedEvent(
        PropertyId propertyId,
        Unit unit,
        UUID unitId,
        LocalDate dateFrom,
        LocalDate dateTo) {

    /** What kind of unit changed — decides which store the relay re-reads. */
    public enum Unit {AVAILABILITY, RESTRICTION}

    public AriChangedEvent {
        Guard.notNull(propertyId, "propertyId");
        Guard.notNull(unit, "unit");
        Guard.notNull(unitId, "unitId");
        Guard.notNull(dateFrom, "dateFrom");
        Guard.notNull(dateTo, "dateTo");
        Guard.isTrue(!dateTo.isBefore(dateFrom), "dateTo must not be before dateFrom");
    }
}
