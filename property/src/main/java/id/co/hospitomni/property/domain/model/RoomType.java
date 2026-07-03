/*
 * A bookable room category under a property (Channex-shaped).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.domain.model;

import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;

public record RoomType(
        RoomTypeId id,
        PropertyId propertyId,
        String title,
        int countOfRooms,
        int occAdults,
        int occChildren) {

    public RoomType {
        Guard.notNull(id, "id");
        Guard.notNull(propertyId, "propertyId");
        Guard.notBlank(title, "title");
        Guard.maxLength(title, 200, "title");
        Guard.positive(countOfRooms, "countOfRooms");
        Guard.positive(occAdults, "occAdults");
        Guard.nonNegative(occChildren, "occChildren");
    }
}
