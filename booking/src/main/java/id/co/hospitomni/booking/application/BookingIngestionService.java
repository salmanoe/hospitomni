/*
 * Turns OTA booking notifications into booking revisions — the write side of
 * the inbound loop. One transaction covers the event append, the aggregate
 * upsert, and the optimistic availability adjustment, so the stream never
 * shows a revision whose side effects didn't commit.
 *
 * Ordering/dedupe: revisions apply only when their revision_seq is newer
 * than the aggregate's; the stream's unique constraint is the backstop for
 * concurrent redeliveries. Ignored revisions leave no trace anywhere.
 *
 * Inventory: a landed booking immediately decrements availability and the
 * resulting dirty cells fan out to every mapped channel — the overbooking
 * window closes in seconds, not PMS polling latency. The PMS's next
 * authoritative ARI push reconciles; the PMS value wins.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.application;

import id.co.hospitomni.booking.application.command.OtaBookingCommand;
import id.co.hospitomni.booking.domain.model.Booking;
import id.co.hospitomni.booking.domain.model.RoomStay;
import id.co.hospitomni.booking.domain.port.out.AvailabilityAdjuster;
import id.co.hospitomni.booking.domain.port.out.BookingCatalog;
import id.co.hospitomni.booking.domain.port.out.BookingEventStream;
import id.co.hospitomni.booking.domain.port.out.BookingRepository;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.BookingId;
import id.co.hospitomni.shared.event.AriChangedEvent;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.OptionalLong;

@Service
public class BookingIngestionService {

    /** Outcome of one notification; {@code seq} is null when it was a duplicate/stale no-op. */
    public record IngestResult(BookingId bookingId, @Nullable Long seq) {
        public boolean duplicate() {
            return seq == null;
        }
    }

    private final BookingRepository bookingRepository;
    private final BookingEventStream eventStream;
    private final BookingCatalog catalog;
    private final AvailabilityAdjuster availabilityAdjuster;
    private final ApplicationEventPublisher eventPublisher;

    public BookingIngestionService(
            BookingRepository bookingRepository,
            BookingEventStream eventStream,
            BookingCatalog catalog,
            AvailabilityAdjuster availabilityAdjuster,
            ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.eventStream = eventStream;
        this.catalog = catalog;
        this.availabilityAdjuster = availabilityAdjuster;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public IngestResult ingest(OtaBookingCommand command) {
        AccountId account = AccountContext.current();
        validateOwnership(command, account);

        Optional<Booking> existing = bookingRepository.findByOtaReservation(
                account, command.otaName(), command.otaReservationCode());
        if (existing.isPresent() && !existing.get().acceptsRevision(command.revisionSeq())) {
            // Stale or redelivered revision — already applied, nothing changes.
            return new IngestResult(existing.get().id(), null);
        }

        Booking booking = existing
                .map(current -> current.apply(
                        command.status(), command.revisionSeq(), command.customer(), command.rooms()))
                .orElseGet(() -> new Booking(
                        BookingId.generate(), account, command.propertyId(),
                        command.otaName(), command.otaReservationCode(),
                        command.status(), command.revisionSeq(),
                        command.customer(), command.rooms()));

        // The aggregate must be flushed before the revision row (FK). A
        // concurrent identical revision loses the stream's unique-constraint
        // race below and aborts the whole transaction — nothing half-applies.
        bookingRepository.save(booking);
        OptionalLong seq = eventStream.append(booking);
        if (seq.isEmpty()) {
            // Booking reference only — guest data never reaches error paths.
            throw new DataIntegrityViolationException(
                    "Concurrent delivery of %s reservation %s revision %d"
                            .formatted(booking.otaName(), booking.otaReservationCode(),
                                    command.revisionSeq()));
        }

        // Release what the previous revision held, hold what this one needs.
        // Unchanged rooms get a transient +1/-1 inside the same transaction —
        // the committed value and the relayed push both carry the net result.
        existing.ifPresent(previous -> {
            if (previous.holdsInventory()) {
                previous.rooms().forEach(stay -> adjust(previous, stay, +1));
            }
        });
        if (booking.holdsInventory()) {
            booking.rooms().forEach(stay -> adjust(booking, stay, -1));
        }

        return new IngestResult(booking.id(), seq.getAsLong());
    }

    private void adjust(Booking booking, RoomStay stay, int delta) {
        availabilityAdjuster.adjust(
                booking.propertyId(), stay.roomTypeId(), stay.checkinDate(), stay.lastNight(), delta);
        // Same-transaction event: the channel module marks dirty outbox cells
        // atomically, and the relay pushes the adjusted availability to every
        // mapped channel (the source OTA just receives a harmless echo).
        eventPublisher.publishEvent(new AriChangedEvent(
                booking.propertyId(), AriChangedEvent.Unit.AVAILABILITY,
                stay.roomTypeId().value(), stay.checkinDate(), stay.lastNight()));
    }

    private void validateOwnership(OtaBookingCommand command, AccountId account) {
        if (!catalog.propertyOwnedBy(command.propertyId(), account)) {
            throw new ResourceNotFoundException("Property", command.propertyId().value());
        }
        for (RoomStay stay : command.rooms()) {
            if (!catalog.roomTypeInProperty(stay.roomTypeId(), command.propertyId())) {
                throw new ResourceNotFoundException("RoomType", stay.roomTypeId().value());
            }
            if (!catalog.ratePlanInProperty(stay.ratePlanId(), command.propertyId())) {
                throw new ResourceNotFoundException("RatePlan", stay.ratePlanId().value());
            }
        }
    }
}
