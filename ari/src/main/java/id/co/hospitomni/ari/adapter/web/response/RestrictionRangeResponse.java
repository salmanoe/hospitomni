/*
 * One compressed restriction range on the wire; unset/unselected fields
 * are omitted from the JSON.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.adapter.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import id.co.hospitomni.ari.application.AriReadService.RestrictionRange;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RestrictionRangeResponse(
        PropertyId propertyId,
        RatePlanId ratePlanId,
        LocalDate dateFrom,
        LocalDate dateTo,
        @Nullable Long rate,
        @Nullable Integer minStay,
        @Nullable Integer maxStay,
        @Nullable Boolean closedToArrival,
        @Nullable Boolean closedToDeparture,
        @Nullable Boolean stopSell) {

    public static RestrictionRangeResponse from(RestrictionRange range) {
        return new RestrictionRangeResponse(
                range.propertyId(), range.ratePlanId(), range.dateFrom(), range.dateTo(),
                range.fields().rate(), range.fields().minStay(), range.fields().maxStay(),
                range.fields().closedToArrival(), range.fields().closedToDeparture(),
                range.fields().stopSell());
    }
}
