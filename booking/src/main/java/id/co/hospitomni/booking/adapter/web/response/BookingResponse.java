/*
 * Current-state view of one booking — same field shape as the event payload
 * (customer block, rooms with nested occupancy), so PMS-side mapping code
 * is written once.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.adapter.web.response;

import id.co.hospitomni.booking.domain.model.Booking;
import id.co.hospitomni.booking.domain.model.BookingStatus;
import id.co.hospitomni.booking.domain.model.Customer;
import id.co.hospitomni.booking.domain.model.RoomStay;
import id.co.hospitomni.shared.BookingId;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;

import java.time.LocalDate;
import java.util.List;

public record BookingResponse(
        BookingId id,
        PropertyId propertyId,
        String otaName,
        String otaReservationCode,
        BookingStatus status,
        int revisionSeq,
        Customer customer,
        List<Room> rooms) {

    public record Room(
            RoomTypeId roomTypeId,
            RatePlanId ratePlanId,
            LocalDate checkinDate,
            LocalDate checkoutDate,
            Occupancy occupancy) {

        static Room from(RoomStay stay) {
            return new Room(stay.roomTypeId(), stay.ratePlanId(),
                    stay.checkinDate(), stay.checkoutDate(),
                    new Occupancy(stay.occAdults(), stay.occChildren()));
        }
    }

    public record Occupancy(int adults, int children) {
    }

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.id(), booking.propertyId(), booking.otaName(), booking.otaReservationCode(),
                booking.status(), booking.latestRevisionSeq(), booking.customer(),
                booking.rooms().stream().map(Room::from).toList());
    }
}
