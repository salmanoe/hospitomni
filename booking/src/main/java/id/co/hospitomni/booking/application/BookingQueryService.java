/*
 * Read side of the inbound loop: single-booking lookup and the cursor-based
 * event feed the PMS polls. The client owns its cursor — pass the last seq
 * it saw as `after`; nothing expires and nothing needs acking.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.application;

import id.co.hospitomni.booking.domain.model.Booking;
import id.co.hospitomni.booking.domain.port.out.BookingEventStream;
import id.co.hospitomni.booking.domain.port.out.BookingEventStream.StoredBookingEvent;
import id.co.hospitomni.booking.domain.port.out.BookingRepository;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.BookingId;
import id.co.hospitomni.shared.Guard;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookingQueryService {

    private final BookingRepository bookingRepository;
    private final BookingEventStream eventStream;

    public BookingQueryService(BookingRepository bookingRepository, BookingEventStream eventStream) {
        this.bookingRepository = bookingRepository;
        this.eventStream = eventStream;
    }

    public Booking get(BookingId id) {
        return bookingRepository.findById(id, AccountContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id.value()));
    }

    public List<StoredBookingEvent> eventsAfter(long after, int limit) {
        Guard.isTrue(after >= 0, "after must be >= 0");
        Guard.positive(limit, "limit");
        return eventStream.readAfter(AccountContext.current(), after, limit);
    }
}
