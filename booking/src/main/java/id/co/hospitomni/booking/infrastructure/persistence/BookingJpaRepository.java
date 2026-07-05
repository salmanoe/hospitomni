/*
 * Spring Data repository for the booking aggregate.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.infrastructure.persistence;

import id.co.hospitomni.booking.infrastructure.persistence.entity.BookingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookingJpaRepository extends JpaRepository<BookingJpaEntity, UUID> {

    Optional<BookingJpaEntity> findByIdAndAccountId(UUID id, UUID accountId);

    Optional<BookingJpaEntity> findByAccountIdAndOtaNameAndOtaReservationCode(
            UUID accountId, String otaName, String otaReservationCode);
}
