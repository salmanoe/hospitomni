/*
 * Relay-side read of the CURRENT stored ARI values for claimed cells —
 * last write wins; the outbox never replays stale payloads.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.domain.port.out;

import id.co.hospitomni.channel.domain.model.RestrictionValues;
import id.co.hospitomni.shared.PropertyId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AriValueReader {

    /** Current availability per night for one room type; missing nights absent. */
    Map<LocalDate, Integer> availabilityFor(UUID roomTypeId, List<LocalDate> dates);

    /** Current restriction fields per night for one rate plan; missing nights absent. */
    Map<LocalDate, RestrictionValues> restrictionsFor(UUID ratePlanId, List<LocalDate> dates);

    /** All room-type ids of a property — the reconciler's availability units. */
    List<UUID> roomTypeIdsOf(PropertyId propertyId);

    /** All rate-plan ids of a property — the reconciler's restriction units. */
    List<UUID> ratePlanIdsOf(PropertyId propertyId);
}
