/*
 * Create/update payload for a rate plan.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.adapter.web.request;

import id.co.hospitomni.property.application.command.UpsertRatePlanCommand;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpsertRatePlanRequest(
        @NotNull UUID propertyId,
        @NotNull UUID roomTypeId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(min = 3, max = 3) String currency) {

    public UpsertRatePlanCommand toCommand() {
        return new UpsertRatePlanCommand(
                PropertyId.of(propertyId), RoomTypeId.of(roomTypeId), title, currency);
    }
}
