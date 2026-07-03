/*
 * One availability write value: a span of nights for a room type.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.application.command;

import id.co.hospitomni.ari.domain.model.DateSpan;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;

public record AvailabilityValueCommand(
        PropertyId propertyId, RoomTypeId roomTypeId, DateSpan span, int availability) {
}
