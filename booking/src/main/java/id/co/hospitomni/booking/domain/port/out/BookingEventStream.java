/*
 * The durable, append-only booking-events stream. seq is the cursor:
 * oldest-first, replayable from any position, nothing expires, no ack.
 * Appends are deduped on (account, ota_name, ota_reservation_code,
 * revision_seq) — a redelivered notification produces no second event.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.domain.port.out;

import id.co.hospitomni.booking.domain.model.Booking;
import id.co.hospitomni.shared.AccountId;

import java.util.List;
import java.util.OptionalLong;

public interface BookingEventStream {

    /**
     * Appends the full snapshot of {@code booking} as one event.
     *
     * @return the assigned cursor seq, or empty when the revision was
     *         already present (schema-level dedupe)
     */
    OptionalLong append(Booking booking);

    /** Events with {@code seq > after}, oldest-first, at most {@code limit}. */
    List<StoredBookingEvent> readAfter(AccountId accountId, long after, int limit);

    /** One stored event: the cursor position and the payload as appended. */
    record StoredBookingEvent(long seq, String payloadJson) {
    }
}
