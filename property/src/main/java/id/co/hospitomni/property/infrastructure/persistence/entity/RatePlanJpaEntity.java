/*
 * Maps the `rate_plan` table.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "rate_plan")
public class RatePlanJpaEntity {

    @Id
    private @Nullable UUID id;

    @Column(name = "property_id", nullable = false)
    private @Nullable UUID propertyId;

    @Column(name = "room_type_id", nullable = false)
    private @Nullable UUID roomTypeId;

    @Column(nullable = false, length = 200)
    private @Nullable String title;

    @Column(nullable = false, length = 3)
    private @Nullable String currency;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private @Nullable Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private @Nullable Instant updatedAt;

    protected RatePlanJpaEntity() {} // JPA only

    public RatePlanJpaEntity(UUID id, UUID propertyId, UUID roomTypeId, String title, String currency) {
        this.id = id;
        this.propertyId = propertyId;
        this.roomTypeId = roomTypeId;
        this.title = title;
        this.currency = currency;
    }

    /** Content update; identity (id, propertyId) is immutable, room type may repoint. */
    public void updateContent(UUID roomTypeId, String title, String currency) {
        this.roomTypeId = roomTypeId;
        this.title = title;
        this.currency = currency;
        this.updatedAt = Instant.now();
    }

    public UUID id() {
        return Objects.requireNonNull(id);
    }

    public UUID propertyId() {
        return Objects.requireNonNull(propertyId);
    }

    public UUID roomTypeId() {
        return Objects.requireNonNull(roomTypeId);
    }

    public String title() {
        return Objects.requireNonNull(title);
    }

    public String currency() {
        return Objects.requireNonNull(currency);
    }
}
