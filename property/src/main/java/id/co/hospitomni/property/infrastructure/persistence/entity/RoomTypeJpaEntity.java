/*
 * Maps the `room_type` table.
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
@Table(name = "room_type")
public class RoomTypeJpaEntity {

    @Id
    private @Nullable UUID id;

    @Column(name = "property_id", nullable = false)
    private @Nullable UUID propertyId;

    @Column(nullable = false, length = 200)
    private @Nullable String title;

    @Column(name = "count_of_rooms", nullable = false)
    private int countOfRooms;

    @Column(name = "occ_adults", nullable = false)
    private int occAdults;

    @Column(name = "occ_children", nullable = false)
    private int occChildren;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private @Nullable Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private @Nullable Instant updatedAt;

    protected RoomTypeJpaEntity() {} // JPA only

    public RoomTypeJpaEntity(UUID id, UUID propertyId, String title,
                             int countOfRooms, int occAdults, int occChildren) {
        this.id = id;
        this.propertyId = propertyId;
        this.title = title;
        this.countOfRooms = countOfRooms;
        this.occAdults = occAdults;
        this.occChildren = occChildren;
    }

    /** Content update; identity (id, propertyId) is immutable. */
    public void updateContent(String title, int countOfRooms, int occAdults, int occChildren) {
        this.title = title;
        this.countOfRooms = countOfRooms;
        this.occAdults = occAdults;
        this.occChildren = occChildren;
        this.updatedAt = Instant.now();
    }

    public UUID id() {
        return Objects.requireNonNull(id);
    }

    public UUID propertyId() {
        return Objects.requireNonNull(propertyId);
    }

    public String title() {
        return Objects.requireNonNull(title);
    }

    public int countOfRooms() {
        return countOfRooms;
    }

    public int occAdults() {
        return occAdults;
    }

    public int occChildren() {
        return occChildren;
    }
}
