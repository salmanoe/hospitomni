/*
 * One night where the OTA's observed value disagrees with HospitOmni's
 * intended value. Values are rendered as strings for reporting — the
 * reconciler compares typed values before building these. observed null
 * means the OTA holds nothing for a night HospitOmni has set.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-06
 */
package id.co.hospitomni.channel.domain.model;

import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.event.AriChangedEvent;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

public record DriftCell(
        AriChangedEvent.Unit unit,
        UUID unitId,
        LocalDate date,
        String expected,
        @Nullable String observed) {

    public DriftCell {
        Guard.notNull(unit, "unit");
        Guard.notNull(unitId, "unitId");
        Guard.notNull(date, "date");
        Guard.notBlank(expected, "expected");
    }
}
