/*
 * One room line of a booking: which unit, which plan, which nights.
 * Nights are the half-open span [checkin, checkout) in hotel-local dates —
 * checkout day itself holds no inventory.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.domain.model;

import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;

import java.time.LocalDate;

public record RoomStay(
        RoomTypeId roomTypeId,
        RatePlanId ratePlanId,
        LocalDate checkinDate,
        LocalDate checkoutDate,
        int occAdults,
        int occChildren) {

    public RoomStay {
        Guard.notNull(roomTypeId, "roomTypeId");
        Guard.notNull(ratePlanId, "ratePlanId");
        Guard.notNull(checkinDate, "checkinDate");
        Guard.notNull(checkoutDate, "checkoutDate");
        Guard.isTrue(checkoutDate.isAfter(checkinDate), "checkoutDate must be after checkinDate");
        Guard.positive(occAdults, "occAdults");
        Guard.nonNegative(occChildren, "occChildren");
    }

    /** Last night that consumes inventory — the day before checkout. */
    public LocalDate lastNight() {
        return checkoutDate.minusDays(1);
    }
}
