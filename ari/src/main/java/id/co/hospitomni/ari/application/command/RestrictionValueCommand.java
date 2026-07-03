/*
 * One restriction write value: a span of nights for a rate plan, carrying
 * only the fields the caller sent.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.application.command;

import id.co.hospitomni.ari.domain.model.DateSpan;
import id.co.hospitomni.ari.domain.model.RestrictionFields;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;

public record RestrictionValueCommand(
        PropertyId propertyId, RatePlanId ratePlanId, DateSpan span, RestrictionFields fields) {
}
