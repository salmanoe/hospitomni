/*
 * BookingRepository port implementation over Spring Data JPA.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.infrastructure.persistence;

import id.co.hospitomni.booking.domain.model.Booking;
import id.co.hospitomni.booking.domain.model.BookingStatus;
import id.co.hospitomni.booking.domain.model.Customer;
import id.co.hospitomni.booking.domain.model.RoomStay;
import id.co.hospitomni.booking.domain.port.out.BookingRepository;
import id.co.hospitomni.booking.infrastructure.persistence.entity.BookingJpaEntity;
import id.co.hospitomni.booking.infrastructure.persistence.entity.BookingRoomJpaEntity;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.BookingId;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BookingRepositoryAdapter implements BookingRepository {

    private final BookingJpaRepository jpa;

    public BookingRepositoryAdapter(BookingJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Booking> findById(BookingId id, AccountId accountId) {
        return jpa.findByIdAndAccountId(id.value(), accountId.value())
                .map(BookingRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Booking> findByOtaReservation(
            AccountId accountId, String otaName, String otaReservationCode) {
        return jpa.findByAccountIdAndOtaNameAndOtaReservationCode(
                        accountId.value(), otaName, otaReservationCode)
                .map(BookingRepositoryAdapter::toDomain);
    }

    @Override
    public void save(Booking booking) {
        Customer customer = booking.customer();
        List<BookingRoomJpaEntity> rooms = booking.rooms().stream()
                .map(stay -> new BookingRoomJpaEntity(
                        stay.roomTypeId().value(), stay.ratePlanId().value(),
                        stay.checkinDate(), stay.checkoutDate(),
                        stay.occAdults(), stay.occChildren()))
                .toList();
        BookingJpaEntity entity = jpa.findById(booking.id().value())
                .map(existing -> {
                    existing.applyRevision(booking.status().wire(), booking.latestRevisionSeq(),
                            customer.name(), customer.surname(), customer.mail(),
                            customer.phone(), customer.country(), rooms);
                    return existing;
                })
                .orElseGet(() -> new BookingJpaEntity(
                        booking.id().value(), booking.accountId().value(), booking.propertyId().value(),
                        booking.otaName(), booking.otaReservationCode(),
                        booking.status().wire(), booking.latestRevisionSeq(),
                        customer.name(), customer.surname(), customer.mail(),
                        customer.phone(), customer.country(), rooms));
        // Flush now: the revision append that follows in the same transaction
        // references this row via plain SQL, bypassing the persistence context.
        jpa.saveAndFlush(entity);
    }

    private static Booking toDomain(BookingJpaEntity entity) {
        return new Booking(
                BookingId.of(entity.id()),
                AccountId.of(entity.accountId()),
                PropertyId.of(entity.propertyId()),
                entity.otaName(),
                entity.otaReservationCode(),
                BookingStatus.fromWire(entity.status()),
                entity.latestRevisionSeq(),
                new Customer(entity.customerName(), entity.customerSurname(), entity.customerMail(),
                        entity.customerPhone(), entity.customerCountry()),
                entity.rooms().stream()
                        .map(room -> new RoomStay(
                                RoomTypeId.of(room.roomTypeId()), RatePlanId.of(room.ratePlanId()),
                                room.checkinDate(), room.checkoutDate(),
                                room.occAdults(), room.occChildren()))
                        .toList());
    }
}
