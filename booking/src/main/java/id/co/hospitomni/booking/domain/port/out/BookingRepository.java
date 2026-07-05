/*
 * Persistence port for the booking aggregate (JPA-backed — bookings are
 * aggregate-shaped and low-volume; the revision stream has its own port).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.domain.port.out;

import id.co.hospitomni.booking.domain.model.Booking;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.BookingId;

import java.util.Optional;

public interface BookingRepository {

    Optional<Booking> findById(BookingId id, AccountId accountId);

    /** Lookup by the OTA's own identity — how redeliveries and revisions find their aggregate. */
    Optional<Booking> findByOtaReservation(AccountId accountId, String otaName, String otaReservationCode);

    void save(Booking booking);
}
