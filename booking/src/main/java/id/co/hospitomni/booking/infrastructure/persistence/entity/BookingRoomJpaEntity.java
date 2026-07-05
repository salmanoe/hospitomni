/*
 * Maps the `booking_room` table — one room line of a booking snapshot.
 * Rows are immutable; a newer revision replaces the whole set.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "booking_room")
public class BookingRoomJpaEntity {

    @Id
    private @Nullable UUID id;

    @Column(name = "room_type_id", nullable = false, updatable = false)
    private @Nullable UUID roomTypeId;

    @Column(name = "rate_plan_id", nullable = false, updatable = false)
    private @Nullable UUID ratePlanId;

    @Column(name = "checkin_date", nullable = false, updatable = false)
    private @Nullable LocalDate checkinDate;

    @Column(name = "checkout_date", nullable = false, updatable = false)
    private @Nullable LocalDate checkoutDate;

    @Column(name = "occ_adults", nullable = false, updatable = false)
    private int occAdults;

    @Column(name = "occ_children", nullable = false, updatable = false)
    private int occChildren;

    protected BookingRoomJpaEntity() {} // JPA only

    public BookingRoomJpaEntity(
            UUID roomTypeId, UUID ratePlanId,
            LocalDate checkinDate, LocalDate checkoutDate, int occAdults, int occChildren) {
        this.id = UUID.randomUUID();
        this.roomTypeId = roomTypeId;
        this.ratePlanId = ratePlanId;
        this.checkinDate = checkinDate;
        this.checkoutDate = checkoutDate;
        this.occAdults = occAdults;
        this.occChildren = occChildren;
    }

    public UUID roomTypeId() {
        return Objects.requireNonNull(roomTypeId);
    }

    public UUID ratePlanId() {
        return Objects.requireNonNull(ratePlanId);
    }

    public LocalDate checkinDate() {
        return Objects.requireNonNull(checkinDate);
    }

    public LocalDate checkoutDate() {
        return Objects.requireNonNull(checkoutDate);
    }

    public int occAdults() {
        return occAdults;
    }

    public int occChildren() {
        return occChildren;
    }
}
