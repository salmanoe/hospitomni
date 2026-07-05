/*
 * Maps the `booking` table with its `booking_room` children. Rooms are a
 * fully-owned collection: a newer revision replaces the whole set
 * (orphanRemoval), matching the snapshot semantics of OTA notifications.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-05
 */
package id.co.hospitomni.booking.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "booking")
public class BookingJpaEntity {

    @Id
    private @Nullable UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private @Nullable UUID accountId;

    @Column(name = "property_id", nullable = false, updatable = false)
    private @Nullable UUID propertyId;

    @Column(name = "ota_name", nullable = false, updatable = false, length = 64)
    private @Nullable String otaName;

    @Column(name = "ota_reservation_code", nullable = false, updatable = false, length = 128)
    private @Nullable String otaReservationCode;

    @Column(nullable = false, length = 16)
    private @Nullable String status;

    @Column(name = "latest_revision_seq", nullable = false)
    private int latestRevisionSeq;

    @Column(name = "customer_name", nullable = false, length = 200)
    private @Nullable String customerName;

    @Column(name = "customer_surname", length = 200)
    private @Nullable String customerSurname;

    @Column(name = "customer_mail", length = 320)
    private @Nullable String customerMail;

    @Column(name = "customer_phone", length = 32)
    private @Nullable String customerPhone;

    @Column(name = "customer_country", length = 2)
    private @Nullable String customerCountry;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private List<BookingRoomJpaEntity> rooms = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private @Nullable Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private @Nullable Instant updatedAt;

    protected BookingJpaEntity() {} // JPA only

    public BookingJpaEntity(
            UUID id, UUID accountId, UUID propertyId, String otaName, String otaReservationCode,
            String status, int latestRevisionSeq,
            String customerName, @Nullable String customerSurname, @Nullable String customerMail,
            @Nullable String customerPhone, @Nullable String customerCountry,
            List<BookingRoomJpaEntity> rooms) {
        this.id = id;
        this.accountId = accountId;
        this.propertyId = propertyId;
        this.otaName = otaName;
        this.otaReservationCode = otaReservationCode;
        this.status = status;
        this.latestRevisionSeq = latestRevisionSeq;
        this.customerName = customerName;
        this.customerSurname = customerSurname;
        this.customerMail = customerMail;
        this.customerPhone = customerPhone;
        this.customerCountry = customerCountry;
        this.rooms = new ArrayList<>(rooms);
    }

    /** Applies a newer revision; identity (id, account, property, OTA key) is immutable. */
    public void applyRevision(
            String status, int latestRevisionSeq,
            String customerName, @Nullable String customerSurname, @Nullable String customerMail,
            @Nullable String customerPhone, @Nullable String customerCountry,
            List<BookingRoomJpaEntity> rooms) {
        this.status = status;
        this.latestRevisionSeq = latestRevisionSeq;
        this.customerName = customerName;
        this.customerSurname = customerSurname;
        this.customerMail = customerMail;
        this.customerPhone = customerPhone;
        this.customerCountry = customerCountry;
        this.rooms.clear();
        this.rooms.addAll(rooms);
        this.updatedAt = Instant.now();
    }

    public UUID id() {
        return Objects.requireNonNull(id);
    }

    public UUID accountId() {
        return Objects.requireNonNull(accountId);
    }

    public UUID propertyId() {
        return Objects.requireNonNull(propertyId);
    }

    public String otaName() {
        return Objects.requireNonNull(otaName);
    }

    public String otaReservationCode() {
        return Objects.requireNonNull(otaReservationCode);
    }

    public String status() {
        return Objects.requireNonNull(status);
    }

    public int latestRevisionSeq() {
        return latestRevisionSeq;
    }

    public String customerName() {
        return Objects.requireNonNull(customerName);
    }

    public @Nullable String customerSurname() {
        return customerSurname;
    }

    public @Nullable String customerMail() {
        return customerMail;
    }

    public @Nullable String customerPhone() {
        return customerPhone;
    }

    public @Nullable String customerCountry() {
        return customerCountry;
    }

    public List<BookingRoomJpaEntity> rooms() {
        return rooms;
    }
}
