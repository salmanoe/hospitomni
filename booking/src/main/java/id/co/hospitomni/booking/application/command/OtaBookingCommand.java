/*
 * One inbound OTA booking notification, normalized: full booking state plus
 * the OTA's revision sequence. Every status (including cancellation) carries
 * the complete room set — cancellations need it to release inventory even
 * when the booking was never seen before.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.application.command;

import id.co.hospitomni.booking.domain.model.BookingStatus;
import id.co.hospitomni.booking.domain.model.Customer;
import id.co.hospitomni.booking.domain.model.RoomStay;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;

import java.util.List;

public record OtaBookingCommand(
        PropertyId propertyId,
        String otaName,
        String otaReservationCode,
        int revisionSeq,
        BookingStatus status,
        Customer customer,
        List<RoomStay> rooms) {

    public OtaBookingCommand {
        Guard.notNull(propertyId, "propertyId");
        Guard.notBlank(otaName, "otaName");
        Guard.notBlank(otaReservationCode, "otaReservationCode");
        Guard.positive(revisionSeq, "revisionSeq");
        Guard.notNull(status, "status");
        Guard.notNull(customer, "customer");
        Guard.notNull(rooms, "rooms");
        Guard.isTrue(!rooms.isEmpty(), "rooms must not be empty");
        rooms = List.copyOf(rooms);
    }
}
