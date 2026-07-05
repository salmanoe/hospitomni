/*
 * Current state of one inbound OTA booking. Identity within an account is
 * the OTA's own (ota_name, ota_reservation_code) pair; revisions of the same
 * pair mutate this aggregate. latestRevisionSeq fences out stale or
 * redelivered revisions — ordering is by revision sequence, never timestamps.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.domain.model;

import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.BookingId;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.PropertyId;

import java.util.List;

public record Booking(
        BookingId id,
        AccountId accountId,
        PropertyId propertyId,
        String otaName,
        String otaReservationCode,
        BookingStatus status,
        int latestRevisionSeq,
        Customer customer,
        List<RoomStay> rooms) {

    public Booking {
        Guard.notNull(id, "id");
        Guard.notNull(accountId, "accountId");
        Guard.notNull(propertyId, "propertyId");
        Guard.notBlank(otaName, "otaName");
        Guard.notBlank(otaReservationCode, "otaReservationCode");
        Guard.notNull(status, "status");
        Guard.positive(latestRevisionSeq, "latestRevisionSeq");
        Guard.notNull(customer, "customer");
        Guard.notNull(rooms, "rooms");
        Guard.isTrue(!rooms.isEmpty(), "rooms must not be empty");
        rooms = List.copyOf(rooms);
    }

    /** True if {@code revisionSeq} is newer than everything applied so far. */
    public boolean acceptsRevision(int revisionSeq) {
        return revisionSeq > latestRevisionSeq;
    }

    /** True while the booking's rooms hold inventory (i.e. not cancelled). */
    public boolean holdsInventory() {
        return status != BookingStatus.CANCELLED;
    }

    /** The aggregate after applying a newer revision. */
    public Booking apply(BookingStatus newStatus, int revisionSeq, Customer newCustomer, List<RoomStay> newRooms) {
        Guard.isTrue(acceptsRevision(revisionSeq), "revisionSeq must be newer than latestRevisionSeq");
        return new Booking(id, accountId, propertyId, otaName, otaReservationCode,
                newStatus, revisionSeq, newCustomer, newRooms);
    }
}
