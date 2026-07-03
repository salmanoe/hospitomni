/*
 * Batch store for restriction day-cells with per-field merge: an unsent
 * (null) field keeps the stored value (Channex partial-update semantics).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.domain.port.out;

import id.co.hospitomni.ari.domain.model.RestrictionCell;
import id.co.hospitomni.shared.PropertyId;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

public interface RestrictionStore {

    void upsertMerge(List<RestrictionCell> cells);

    /** Per-day cells for one property, ordered by rate plan then date. */
    List<RestrictionCell> read(PropertyId propertyId, @Nullable LocalDate dateGte, @Nullable LocalDate dateLte);
}
