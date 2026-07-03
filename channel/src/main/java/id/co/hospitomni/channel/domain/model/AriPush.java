/*
 * One coalesced, range-compressed push to one OTA channel — built from the
 * CURRENT stored values of the claimed dirty cells (last write wins).
 * Restriction fields are the full current set per night.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import id.co.hospitomni.shared.PropertyId;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AriPush(
        PropertyId propertyId,
        String otaName,
        long epoch,
        List<AvailabilityRange> availability,
        List<RestrictionRange> restrictions) {

    public record AvailabilityRange(
            UUID roomTypeId, LocalDate dateFrom, LocalDate dateTo, int availability) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RestrictionRange(
            UUID ratePlanId, LocalDate dateFrom, LocalDate dateTo,
            @Nullable Long rate,
            @Nullable Integer minStay,
            @Nullable Integer maxStay,
            @Nullable Boolean closedToArrival,
            @Nullable Boolean closedToDeparture,
            @Nullable Boolean stopSell) {
    }

    @JsonIgnore
    public boolean isEmpty() {
        return availability.isEmpty() && restrictions.isEmpty();
    }
}
