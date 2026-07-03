/*
 * POST /availability payload (Channex-shaped): bulk values, each a single
 * date or an inclusive date_from/date_to range.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.adapter.web.request;

import id.co.hospitomni.ari.application.command.AvailabilityValueCommand;
import id.co.hospitomni.ari.domain.model.DateSpan;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AvailabilityWriteRequest(@NotEmpty List<@Valid Value> values) {

    public record Value(
            @NotNull UUID propertyId,
            @NotNull UUID roomTypeId,
            @Nullable LocalDate date,
            @Nullable LocalDate dateFrom,
            @Nullable LocalDate dateTo,
            @Min(0) int availability) {

        public AvailabilityValueCommand toCommand() {
            return new AvailabilityValueCommand(
                    PropertyId.of(propertyId), RoomTypeId.of(roomTypeId), toSpan(), availability);
        }

        private DateSpan toSpan() {
            if (date != null) {
                Guard.isTrue(dateFrom == null && dateTo == null,
                        "send either date or date_from/date_to, not both");
                return DateSpan.single(date);
            }
            Guard.isTrue(dateFrom != null && dateTo != null,
                    "each value needs date or both date_from and date_to");
            return new DateSpan(dateFrom, dateTo);
        }
    }

    public List<AvailabilityValueCommand> toCommands() {
        return values.stream().map(Value::toCommand).toList();
    }
}
