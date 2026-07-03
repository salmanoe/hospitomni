/*
 * Create/update payload for a room type.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.application.command;

import id.co.hospitomni.shared.PropertyId;

public record UpsertRoomTypeCommand(
        PropertyId propertyId, String title, int countOfRooms, int occAdults, int occChildren) {
}
