/*
 * The per-night restriction field set. Null = "not sent" on writes
 * (partial-update merge keeps the stored value) and "never set" on reads.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.domain.model;

import id.co.hospitomni.shared.Guard;
import org.jspecify.annotations.Nullable;

public record RestrictionFields(
        @Nullable Long rate,
        @Nullable Integer minStay,
        @Nullable Integer maxStay,
        @Nullable Boolean closedToArrival,
        @Nullable Boolean closedToDeparture,
        @Nullable Boolean stopSell) {

    public RestrictionFields {
        Guard.isTrue(rate == null || rate >= 0, "rate must be >= 0");
        Guard.isTrue(minStay == null || minStay >= 1, "min_stay must be >= 1");
        Guard.isTrue(maxStay == null || maxStay >= 1, "max_stay must be >= 1");
    }

    public boolean isEmpty() {
        return rate == null && minStay == null && maxStay == null
                && closedToArrival == null && closedToDeparture == null && stopSell == null;
    }
}
