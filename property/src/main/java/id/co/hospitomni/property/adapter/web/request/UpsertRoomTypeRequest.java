/*
 * Create/update payload for a room type.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.adapter.web.request;

import id.co.hospitomni.property.application.command.UpsertRoomTypeCommand;
import id.co.hospitomni.shared.PropertyId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpsertRoomTypeRequest(
        @NotNull UUID propertyId,
        @NotBlank @Size(max = 200) String title,
        @Min(1) @Max(10_000) int countOfRooms,
        @Min(1) @Max(20) int occAdults,
        @Min(0) @Max(20) int occChildren) {

    public UpsertRoomTypeCommand toCommand() {
        return new UpsertRoomTypeCommand(
                PropertyId.of(propertyId), title, countOfRooms, occAdults, occChildren);
    }
}
