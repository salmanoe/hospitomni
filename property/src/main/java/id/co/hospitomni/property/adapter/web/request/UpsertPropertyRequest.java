/*
 * Create/update payload for a property (Channex-shaped field names;
 * snake_case on the wire via global Jackson config).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.adapter.web.request;

import id.co.hospitomni.property.application.command.UpsertPropertyCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertPropertyRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank @Size(max = 64) String timezone) {

    public UpsertPropertyCommand toCommand() {
        return new UpsertPropertyCommand(title, currency, timezone);
    }
}
