/*
 * One compressed availability range on the wire.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.ari.adapter.web.response;

import id.co.hospitomni.ari.application.AriReadService.AvailabilityRange;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RoomTypeId;

import java.time.LocalDate;

public record AvailabilityRangeResponse(
        PropertyId propertyId,
        RoomTypeId roomTypeId,
        LocalDate dateFrom,
        LocalDate dateTo,
        int availability) {

    public static AvailabilityRangeResponse from(AvailabilityRange range) {
        return new AvailabilityRangeResponse(
                range.propertyId(), range.roomTypeId(),
                range.dateFrom(), range.dateTo(), range.availability());
    }
}
