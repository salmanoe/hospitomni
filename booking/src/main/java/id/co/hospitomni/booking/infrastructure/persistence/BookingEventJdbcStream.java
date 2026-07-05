/*
 * BookingEventStream over JdbcClient — the append-only, cursor-read path is
 * set-shaped and high-volume, so it stays SQL (persistence split by
 * workload). Dedupe rides the table's unique constraint: ON CONFLICT DO
 * NOTHING RETURNING seq yields no row for a redelivered revision.
 *
 * The payload column snapshots the event exactly as delivered; replays
 * re-serve these bytes rather than re-deriving from current state.
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
import id.co.hospitomni.booking.domain.port.out.BookingEventStream;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.BookingId;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.RatePlanId;
import id.co.hospitomni.shared.RoomTypeId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;

@Repository
public class BookingEventJdbcStream implements BookingEventStream {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public BookingEventJdbcStream(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public OptionalLong append(Booking booking) {
        Long seq = jdbc.sql("""
                        INSERT INTO booking_revision
                            (account_id, booking_id, ota_name, ota_reservation_code,
                             revision_seq, status, payload)
                        VALUES (:accountId, :bookingId, :otaName, :otaReservationCode,
                                :revisionSeq, :status, CAST(:payload AS jsonb))
                        ON CONFLICT DO NOTHING
                        RETURNING seq""")
                .param("accountId", booking.accountId().value())
                .param("bookingId", booking.id().value())
                .param("otaName", booking.otaName())
                .param("otaReservationCode", booking.otaReservationCode())
                .param("revisionSeq", booking.latestRevisionSeq())
                .param("status", booking.status().wire())
                .param("payload", objectMapper.writeValueAsString(EventPayload.of(booking)))
                .query(Long.class)
                .optional()
                .orElse(null);
        return seq == null ? OptionalLong.empty() : OptionalLong.of(seq);
    }

    @Override
    public List<StoredBookingEvent> readAfter(AccountId accountId, long after, int limit) {
        return jdbc.sql("""
                        SELECT seq, payload::text AS payload
                        FROM booking_revision
                        WHERE account_id = :accountId AND seq > :after
                        ORDER BY seq
                        LIMIT :limit""")
                .param("accountId", accountId.value())
                .param("after", after)
                .param("limit", limit)
                .query((rs, rowNum) -> new StoredBookingEvent(rs.getLong("seq"), rs.getString("payload")))
                .list();
    }

    // ── payload snapshot (field names serialize snake_case globally) ────

    private record EventPayload(
            BookingId bookingId,
            BookingStatus status,
            PropertyId propertyId,
            String otaName,
            String otaReservationCode,
            int revisionSeq,
            Customer customer,
            List<RoomPayload> rooms) {

        static EventPayload of(Booking booking) {
            return new EventPayload(
                    booking.id(), booking.status(), booking.propertyId(),
                    booking.otaName(), booking.otaReservationCode(), booking.latestRevisionSeq(),
                    booking.customer(),
                    booking.rooms().stream().map(RoomPayload::of).toList());
        }
    }

    private record RoomPayload(
            RoomTypeId roomTypeId,
            RatePlanId ratePlanId,
            LocalDate checkinDate,
            LocalDate checkoutDate,
            Occupancy occupancy) {

        static RoomPayload of(RoomStay stay) {
            return new RoomPayload(
                    stay.roomTypeId(), stay.ratePlanId(), stay.checkinDate(), stay.checkoutDate(),
                    new Occupancy(stay.occAdults(), stay.occChildren()));
        }
    }

    private record Occupancy(int adults, int children) {
    }
}
