/*
 * One (room type, night) availability value — the storage grain.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.domain.model;

import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;

import java.time.LocalDate;

public record AvailabilityCell(
        PropertyId propertyId, RoomTypeId roomTypeId, LocalDate date, int availability) {

    public AvailabilityCell {
        Guard.notNull(propertyId, "propertyId");
        Guard.notNull(roomTypeId, "roomTypeId");
        Guard.notNull(date, "date");
        Guard.nonNegative(availability, "availability");
    }
}
