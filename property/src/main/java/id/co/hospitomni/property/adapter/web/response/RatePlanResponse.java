/*
 * Rate-plan payload — Channex-compatible field names.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.adapter.web.response;

import id.co.hospitomni.property.domain.model.RatePlan;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;

public record RatePlanResponse(
        RatePlanId id,
        PropertyId propertyId,
        RoomTypeId roomTypeId,
        String title,
        String currency) {

    public static RatePlanResponse from(RatePlan ratePlan) {
        return new RatePlanResponse(
                ratePlan.id(),
                ratePlan.propertyId(),
                ratePlan.roomTypeId(),
                ratePlan.title(),
                ratePlan.currency().getCurrencyCode());
    }
}
