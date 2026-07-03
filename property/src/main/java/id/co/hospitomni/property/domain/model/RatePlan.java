/*
 * A sellable rate for a room type (Channex-shaped). Rates on this plan are
 * integer minor units of its currency (IDR is exponent-0).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.domain.model;

import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;

import java.util.Currency;

public record RatePlan(
        RatePlanId id,
        PropertyId propertyId,
        RoomTypeId roomTypeId,
        String title,
        Currency currency) {

    public RatePlan {
        Guard.notNull(id, "id");
        Guard.notNull(propertyId, "propertyId");
        Guard.notNull(roomTypeId, "roomTypeId");
        Guard.notBlank(title, "title");
        Guard.maxLength(title, 200, "title");
        Guard.notNull(currency, "currency");
    }
}
