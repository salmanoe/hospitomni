/*
 * Batch store for availability day-cells. Last write wins per cell.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.domain.port.out;

import id.co.hospitomni.ari.domain.model.AvailabilityCell;
import id.co.hospitomni.shared.PropertyId;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

public interface AvailabilityStore {

    void upsert(List<AvailabilityCell> cells);

    /** Per-day cells for one property, ordered by room type then date. */
    List<AvailabilityCell> read(PropertyId propertyId, @Nullable LocalDate dateGte, @Nullable LocalDate dateLte);
}
