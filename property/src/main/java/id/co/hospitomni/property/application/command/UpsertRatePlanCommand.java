/*
 * Create/update payload for a rate plan.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.application.command;

import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;

public record UpsertRatePlanCommand(
        PropertyId propertyId, RoomTypeId roomTypeId, String title, String currency) {
}
