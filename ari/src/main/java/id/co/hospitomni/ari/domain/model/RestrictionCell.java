/*
 * One (rate plan, night) restriction row — the storage grain.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.domain.model;

import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;

import java.time.LocalDate;

public record RestrictionCell(
        PropertyId propertyId, RatePlanId ratePlanId, LocalDate date, RestrictionFields fields) {

    public RestrictionCell {
        Guard.notNull(propertyId, "propertyId");
        Guard.notNull(ratePlanId, "ratePlanId");
        Guard.notNull(date, "date");
        Guard.notNull(fields, "fields");
    }
}
