/*
 * POST /restrictions payload (Channex-shaped): partial-update semantics —
 * only the fields present in a value change the stored night.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.adapter.web.request;

import id.co.hospitomni.ari.application.command.RestrictionValueCommand;
import id.co.hospitomni.ari.domain.model.DateSpan;
import id.co.hospitomni.ari.domain.model.RestrictionFields;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RestrictionWriteRequest(@NotEmpty List<@Valid Value> values) {

    public record Value(
            @NotNull UUID propertyId,
            @NotNull UUID ratePlanId,
            @Nullable LocalDate date,
            @Nullable LocalDate dateFrom,
            @Nullable LocalDate dateTo,
            @Nullable Long rate,
            @Nullable Integer minStay,
            @Nullable Integer maxStay,
            @Nullable Boolean closedToArrival,
            @Nullable Boolean closedToDeparture,
            @Nullable Boolean stopSell) {

        public RestrictionValueCommand toCommand() {
            return new RestrictionValueCommand(
                    PropertyId.of(propertyId), RatePlanId.of(ratePlanId), toSpan(),
                    new RestrictionFields(rate, minStay, maxStay,
                            closedToArrival, closedToDeparture, stopSell));
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

    public List<RestrictionValueCommand> toCommands() {
        return values.stream().map(Value::toCommand).toList();
    }
}
