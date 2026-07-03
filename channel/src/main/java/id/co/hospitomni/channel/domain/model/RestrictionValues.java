/*
 * Current stored restriction field values for one night (relay re-read).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.domain.model;

import org.jspecify.annotations.Nullable;

public record RestrictionValues(
        @Nullable Long rate,
        @Nullable Integer minStay,
        @Nullable Integer maxStay,
        @Nullable Boolean closedToArrival,
        @Nullable Boolean closedToDeparture,
        @Nullable Boolean stopSell) {
}
