/*
 * Room-type payload — Channex-compatible field names.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.adapter.web.response;

import id.co.hospitomni.property.domain.model.RoomType;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;

public record RoomTypeResponse(
        RoomTypeId id,
        PropertyId propertyId,
        String title,
        int countOfRooms,
        int occAdults,
        int occChildren) {

    public static RoomTypeResponse from(RoomType roomType) {
        return new RoomTypeResponse(
                roomType.id(),
                roomType.propertyId(),
                roomType.title(),
                roomType.countOfRooms(),
                roomType.occAdults(),
                roomType.occChildren());
    }
}
