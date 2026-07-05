/*
 * The OTA's current view of one property's ARI, as read back through the
 * adapter: per-night values keyed by unit. Absent entries mean the OTA
 * holds nothing for that unit/night. This is what the reconciler diffs
 * against HospitOmni's intended state.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.domain.model;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record OtaAriState(
        Map<UUID, Map<LocalDate, Integer>> availability,
        Map<UUID, Map<LocalDate, RestrictionValues>> restrictions) {

    public static OtaAriState empty() {
        return new OtaAriState(Map.of(), Map.of());
    }
}
